package com.acertainbookstore.client.tests;

import java.util.Set;

import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreException;

public class RemoveBooksProcess implements Runnable {

  private final StockManager stockManager;
  private final Set<Integer> stockBooks;

  public RemoveBooksProcess(StockManager stockManager,
                            Set<Integer> stockBooks) {
    this.stockManager = stockManager;
    this.stockBooks = stockBooks;
  }

  public void run() {
    while (!Thread.interrupted()) {
      try {
        stockManager.removeBooks(stockBooks);
      } catch (BookStoreException err) {
        ;
      }
    }
  }

}
