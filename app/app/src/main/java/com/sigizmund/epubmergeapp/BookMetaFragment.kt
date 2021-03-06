package com.sigizmund.epubmergeapp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import java.util.*


private const val ARG_TITLE = "book_meta_title"
private const val ARG_AUTHOR = "book_meta_author"

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [BookMetaFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [BookMetaFragment.newInstance] factory method to
 * create an instance of this fragment.
 *
 */
class BookMetaFragment : Fragment() {
  private val TAG = "BookMetaFragment"
  private var listener: OnFragmentInteractionListener? = null
  private lateinit var bookAuthor: EditText
  private lateinit var bookTitle: EditText

  private lateinit var model: BooksViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    arguments?.let {
      val entries = it.getParcelableArrayList<Uri>(SELECTED_FILES)
      model = ViewModelProviders.of(
        requireActivity(),
        BooksViewModel.BooksViewModelFactory(entries, activity?.application!!)
      )[BooksViewModel::class.java]
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_book_meta, container, false)

    bookAuthor = view.findViewById(R.id.bookAuthor)
    bookTitle = view.findViewById(R.id.bookTitle)

    bookAuthor.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        if (bookAuthor.tag == null) {
          Log.d(TAG, "Book author has changed to ${s.toString()}, updating")

          // This has side effect: now title is considered to be "set", so it won't
          // be automatically updated when books are re-ordered
          model.bookAuthor = s.toString()
        }
      }
    })

    bookTitle.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
      override fun afterTextChanged(s: Editable?) {
        if (bookTitle.tag == null) {
          Log.d(TAG, "Book title was changed to ${bookTitle.text}, updating")

          model.bookTitle = s.toString()
        }
      }
    })

    model.bookEntries?.observe(this, Observer {
      if (!model.bookEntries?.value?.isNullOrEmpty()!!) {
        bookAuthor.tag = TAG
        bookTitle.tag = TAG

        bookAuthor.setText(model.bookAuthor)
        bookTitle.setText(model.bookTitle)

        bookAuthor.tag = null
        bookTitle.tag = null
      }
    })

    bookTitle.tag


    return view
  }


  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (context is OnFragmentInteractionListener) {
      listener = context
    } else {
      throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
    }
  }

  override fun onDetach() {
    super.onDetach()

    listener = null
  }

  /**
   * This interface must be implemented by activities that contain this
   * fragment to allow an interaction in this fragment to be communicated
   * to the activity and potentially other fragments contained in that
   * activity.
   *
   *
   * See the Android Training lesson [Communicating with Other Fragments]
   * (http://developer.android.com/training/basics/fragments/communicating.html)
   * for more information.
   */
  interface OnFragmentInteractionListener {
    fun onMetadataUpdated(title: String, author: String)
  }

  companion object {
    @JvmStatic
    fun newInstance(model: ArrayList<Uri>) =
      BookMetaFragment().apply {
        arguments = Bundle().apply {
          putParcelableArrayList(SELECTED_FILES, model)
        }
      }
  }
}
