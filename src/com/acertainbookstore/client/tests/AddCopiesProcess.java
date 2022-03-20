package com.acertainbookstore.client.tests;

import java.util.Set;

import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

public class AddCopiesProcess implements Runnable {

  private final StockManager stockManager;
  private final Set<BookCopy> bookCopies;
  private final int repetitions;

  public AddCopiesProcess(StockManager stockManager, Set<BookCopy> bookCopies,
                          int repetitions) throws BookStoreException {
    this.stockManager = stockManager;
    this.bookCopies = bookCopies;
    if (repetitions < 0) {
      throw new BookStoreException("Invalid number of repetitions");
    }
    this.repetitions = repetitions;
  }

  public AddCopiesProcess(StockManager stockManager, Set<BookCopy> bookCopies) {
    this.stockManager = stockManager;
    this.bookCopies = bookCopies;
    this.repetitions = -1;
  }

  public void run() {
    for (int i = 0; i != repetitions; ++i) {
      try {
        stockManager.addCopies(bookCopies);
      } catch (BookStoreException err) {
        ;
      }
      if (Thread.interrupted()) return;
    }
  }

}
