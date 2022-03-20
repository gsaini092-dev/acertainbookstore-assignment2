package com.acertainbookstore.client.tests;

import java.util.Set;

import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.utils.BookStoreException;

public class BuyBooksProcess implements Runnable {

  private final BookStore bookStore;
  private final Set<BookCopy> bookCopies;
  private final int repetitions;

  public BuyBooksProcess(BookStore bookStore, Set<BookCopy> bookCopies,
                         int repetitions) throws BookStoreException {
    this.bookStore = bookStore;
    this.bookCopies = bookCopies;
    if (repetitions < 0) {
      throw new BookStoreException("Invalid number of repetitions");
    }
    this.repetitions = repetitions;
  }

  public BuyBooksProcess(BookStore bookStore, Set<BookCopy> bookCopies) {
    this.bookStore = bookStore;
    this.bookCopies = bookCopies;
    this.repetitions = -1;
  }

  public void run() {
    for (int i = 0; i != repetitions; ++i) {
      try {
        bookStore.buyBooks(bookCopies);
      } catch (BookStoreException err) {
        ;
      }
      if (Thread.interrupted()) return;
    }
  }

}
