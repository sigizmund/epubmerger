package epubmerger

import android.util.Log
import nl.siegmann.epublib.domain.*
import nl.siegmann.epublib.epub.EpubReader
import nl.siegmann.epublib.epub.EpubWriter
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.nio.file.Path
import java.util.*

class BookMerger(var epubs: List<Book>) {
  private val VERSION = "1.0"
  private val PUBLISHER = "EpubMerger $VERSION"

  val TOC_TYPE = setOf("application/x-dtbncx+xml")
  val LOG = LoggerFactory.getLogger(BookMerger::class.java)

  private val result = Book()
  private val resources = HashMap<Pair<Int, String>, EpubResource>()

  fun mergeBooks() {
    LOG.info("Starting to merge ${epubs.size} books")
    addAllResources()

    epubs.forEachIndexed { index, epub ->
      processTOC(epub, index)

      epub.spine.spineReferences.forEach {
        val key = index to it.resource.href
        if (!resources.containsKey(key)) {
          throw RuntimeException("Resource ${it.resource.href} is not processed")
        }

        result.spine.addSpineReference(SpineReference(result.resources.getByHref(resources[key]?.newHref)))
      }
    }


    // result.generateSpineFromTableOfContents()
    epubs.forEachIndexed { index, epub ->
      epub.spine.spineReferences.forEach { spr ->
        val key = index to spr.resource.href
        val newHref = resources.get(key)!!.newHref
        if (result.spine.getResourceIndex(newHref) == -1) {
          result.spine.addResource(this.result.resources.getByHref(newHref))
        }
      }
    }

    generateMetadata()
  }

  internal fun processTOC(book: Book, index: Int) {
    LOG.info("processTOC(${book.title})")
    val firstPageResource = if (book.coverPage != null) {
      LOG.info("Using cover page ${book.coverPage.href} for book ${book.title}")
      book.coverPage
    } else {
      LOG.info("Using first spine item ${book.spine.getResource(0)} for book ${book.title}\"")
      book.spine.getResource(0)
    }

    val firstPage: EpubResource? = resources.get(index to firstPageResource.href)
    LOG.info("Using firstPage=${firstPage}")
    val topLevelTocReference = result.addSection(book.title, result.resources.getByHref(firstPage!!.newHref))

    if (book.tableOfContents != null && book.tableOfContents.size() > 0) {
      book.tableOfContents.tocReferences.forEach { tocReference ->
        processTOCReference(index, tocReference, book, topLevelTocReference)
      }
    } else {
      // We should process spine, but for now just having a TOC entry for the cover page
      // or the first page of the book should be sufficient.
    }
  }

  private fun addAllResources() {
    epubs.forEachIndexed { index, epub ->
      epub.resources.all.forEach { res ->
        val key = index to res.href

        if (res.mediaType == null) {
          LOG.warn("res.mediaType=null! %s", res.href)
        }

        if (!TOC_TYPE.contains(res.mediaType.name)) {
          if (!resources.containsKey(key)) {
            val epubResource = ResourceProcessor.createEpubResource(res.href, res.id, index)
            resources.put(key, epubResource)

            val data = if (res.mediaType.isTextBasedFormat)
              ResourceProcessor.reprocessXhtmlFile(res.data, epub, index)
            else res.data

            val res = Resource(
                epubResource.newId,
                data,
                epubResource.newHref,
                res.mediaType)

            // We are not using resources.add because it screws resource id
            result.resources.resourceMap.put(res.href, res)
          }
        }
      }
    }
  }

  private fun processTOCReference(bookIndex: Int, sourceTocReference: TOCReference, book: Book, parent: TOCReference?) {
    // val href = "${bookIndex}_${sourceTocReference.resource.href}"
    val epubResource = ResourceProcessor.createEpubResource(
        sourceTocReference.resource.href,
        sourceTocReference.resourceId,
        bookIndex)

    // If below is untrue, then something disasterous happened and we cannot continue
    assert(result.resources.containsByHref(epubResource.newHref))

    val resource = result.resources.getByHref(epubResource.newHref)
    val tocReference = if (sourceTocReference.fragmentId == null)
      TOCReference(sourceTocReference.title, resource)
    else
      TOCReference(sourceTocReference.title, resource, sourceTocReference.fragmentId)

    var resultTOCReference = if (parent == null)
      result.tableOfContents.addTOCReference(tocReference)
    else parent.addChildSection(tocReference)

    sourceTocReference.children.forEach { childTocEntry ->
      processTOCReference(bookIndex, childTocEntry, book, resultTOCReference)
    }
  }

  private fun generateMetadata() {
    result.metadata.authors.clear()

    if (mergedBookAuthor.length > 1) {
      result.metadata.authors.add(Author(mergedBookAuthor))
    } else {
      result.metadata.authors.addAll(epubs.map { it.metadata.authors }.flatten<Author?>().distinct())
    }

    result.metadata.titles.clear()
    val allTitles = epubs.map { it.metadata.titles }.flatten<String?>()
    val series = allTitles.joinToString("; ")

    if (mergedBookTitle.length > 1) {
      result.metadata.titles.add(mergedBookTitle)
    } else {
      result.metadata.titles.addAll(allTitles)
      result.metadata.titles.add(series)
    }

    result.metadata.addIdentifier(Identifier("uuid", UUID.randomUUID().toString()))
    val publishers = epubs.map { it.metadata.publishers }.flatten().toSet()
    result.metadata.publishers.addAll(publishers)
    result.metadata.addPublisher(PUBLISHER)
  }

  fun writeBook(os: OutputStream) {
    EpubWriter().write(result, os)
    os.close()
  }

  fun writeBook(path: Path) {
    writeBook(path.toFile().outputStream())
  }

  private var _mergedBookTitle: String? = null
  var mergedBookTitle: String
    // TODO some lock needed
    get() {
      if (_mergedBookTitle == null) {
        _mergedBookTitle = ""
      }

      return _mergedBookTitle!!
    }
    set(value) {
      _mergedBookTitle = value
      result.metadata.titles.clear()
      val allTitles = epubs.map { it.metadata.titles }.flatten<String?>()

      result.metadata.titles.add(_mergedBookTitle)
      result.metadata.titles.addAll(allTitles)
    }

  private var _author: String? = null
  var mergedBookAuthor: String
    get() {
      return epubs.map { it.metadata.authors }.flatten<Author?>().distinct().joinToString()
    }
    set(value) {
      result.metadata.authors.clear()
      result.metadata.authors.add(Author(value))
    }

  var metadata: BookMetadata? = null
    set(value) {
      mergedBookAuthor = value?.author!!
      mergedBookTitle = value.title
    }

  companion object {
    fun createBook(bookPath: Path): Book? {
      return EpubReader().readEpub(bookPath.toFile().inputStream())
    }
  }
}

private val MediaType.isTextBasedFormat: Boolean
  get() {
    return this.name in setOf("text/html", "application/xhtml+xml", "text/plain", "text/xml", "application/xml")
  }
