package com.acertainbookstore.client.tests;

import java.util.Set;

import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

public class AddBooksProcess implements Runnable {

  private final StockManager stockManager;
  private final Set<StockBook> stockBooks;

  public AddBooksProcess(StockManager stockManager, Set<StockBook> stockBooks) {
    this.stockManager = stockManager;
    this.stockBooks = stockBooks;
  }

  public void run() {
    while (!Thread.interrupted()) {
      try {
        stockManager.addBooks(stockBooks);
      } catch (BookStoreException err) {
        ;
      }
    }
  }

}
