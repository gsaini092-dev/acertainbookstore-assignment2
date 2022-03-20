package com.acertainbookstore.client.tests;

import java.util.Set;

import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.utils.BookStoreException;

public class ModifyProcess implements Runnable {

  private final BookStore bookStore;
  private final StockManager stockManager;
  private final Set<BookCopy> bookCopies;

  public ModifyProcess(BookStore bookStore, StockManager stockManager,
                       Set<BookCopy> bookCopies) {
    this.bookStore = bookStore;
    this.stockManager = stockManager;
    this.bookCopies = bookCopies;
  }

  public void run() {
    while (!Thread.interrupted()) {
      try {
        bookStore.buyBooks(bookCopies);
        stockManager.addCopies(bookCopies);
      } catch (BookStoreException err) {
        return;
      }
    }
  }

}
