package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.BookEditorPick;
import com.acertainbookstore.business.BookRating;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = false;

	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that concurrently adding copies and buying books maintain consistent state
	 *
	 * @throws BookStoreException
	 */
	@Test
	public void testBuyAndAddConcurrent() throws BookStoreException {
	 	int ITERATIONS = 100000;

		Set<BookCopy> copiesToAdd = new HashSet<>();

		BookCopy book = new BookCopy(TEST_ISBN, ITERATIONS);
		copiesToAdd.add(book);
		storeManager.addCopies(copiesToAdd);
		HashSet<BookCopy> oneBook = new HashSet<>(Arrays.asList(new BookCopy(TEST_ISBN, 1)));
		
        Thread C1 = new Thread(() -> {
            try {
                for(int i = 0; i < ITERATIONS; i++) {
                    client.buyBooks(oneBook);
                }
            } catch (BookStoreException ex) {
            	;
			}
        });
      

        Thread C2 = new Thread(() -> {
			try {
				for(int i = 0; i < ITERATIONS; i++) {
					storeManager.addCopies(oneBook);
				}
			} catch (BookStoreException ex) {
				;
			}
        });
        
        C1.start();
        C2.start();

        try {
			C1.join();
			C2.join();
		} catch (InterruptedException ex) {
        	fail();
		}

		List<StockBook> booksInStore = storeManager.getBooks();
        StockBook defaultBook = booksInStore.get(0);
		assertTrue(defaultBook.getNumCopies() == ITERATIONS + NUM_COPIES);
	}
	
	/**
	 * Spawns two threads that buy books and remove copies in an indeterministic
	 * fashion, verifying that the final amount of books correspondings to the sum
	 * of operations performed by the two threads.
	 */
	@Test
	public void testConcurrency1() throws BookStoreException {

		// Parameters to concurrency test
		int totalBooks = 1000;
		int iterations = 50;
		int booksPerIteration = totalBooks / iterations;

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
		               "The Art of Computer Programming", "Donald Knuth",
									 (float) 300, totalBooks, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
		               "The C Programming Language",
									 "Dennis Ritchie and Brian Kerninghan", (float) 50,
									 totalBooks, 0, 0, 0, false));
		storeManager.removeAllBooks();
		storeManager.addBooks(booksToAdd);

		// Add and buy same amount of books in batches determined by the number of
		// operations
		HashSet<BookCopy> copies = new HashSet<BookCopy>(2);
		copies.add(new BookCopy(TEST_ISBN+1, booksPerIteration));
		copies.add(new BookCopy(TEST_ISBN+2, booksPerIteration));
		Thread buyBooksThread =
		    new Thread(new BuyBooksProcess(client, copies, iterations));
		Thread addCopiesThread =
		    new Thread(new AddCopiesProcess(storeManager, copies, iterations));
		buyBooksThread.start();
		//SleepThread();
		addCopiesThread.start();
		
		try {
			
			buyBooksThread.join();
			addCopiesThread.join();
		} catch (InterruptedException err) {
			fail();
		}

		List<StockBook> bookList = storeManager.getBooks();
		assertEquals(totalBooks, bookList.get(0).getNumCopies());
		assertEquals(totalBooks, bookList.get(1).getNumCopies());

	}
	
	/**
	 * Continously buys/adds copies of books to the store in one thread, while
	 * another thread probes the stock a number of times to verify that the result
	 * is always in a valid state.
	 */
	@Test
	public void testConcurrency2() throws BookStoreException {

		// Number of iterations before accepting
		int repetitions = 1000;

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1,
									 "The Art of Computer Programming", "Donald Knuth",
									 (float) 300, 100, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2,
									 "The C Programming Language",
									 "Dennis Ritchie and Brian Kerninghan", (float) 50,
		               100, 0, 0, 0, false));
		storeManager.removeAllBooks();
		storeManager.addBooks(booksToAdd);

		HashSet<BookCopy> copies = new HashSet<BookCopy>(2);
		copies.add(new BookCopy(TEST_ISBN+1, 50));
		copies.add(new BookCopy(TEST_ISBN+2, 50));

		// The modifying thread continously buys/adds books until stopped
		Thread modifyThread =
		    new Thread(new ModifyProcess(client, storeManager, copies));
		modifyThread.start();

		// This thread acts as the one verifying results, no reason to spawn a new
		// one
		for (int i = 0; i < repetitions; ++i) {
			List<StockBook> bookList = storeManager.getBooks();
			int numberOfCopies = bookList.get(0).getNumCopies();
			assertTrue(numberOfCopies == 50 || numberOfCopies == 100);
		}

		modifyThread.interrupt();

	}

	/**
	 * Adds and removes the same set of books in several threads, ensuring that
	 * all operations successfully release their locks, even when throwing
	 * exceptions. The main thread continously checks that the books are in a
	 * valid state.
	 */
	//@Test
	public void testConcurrency3() throws BookStoreException {

		// Times to check valid state of database
		int repetitions = 1000;

		HashSet<StockBook> stockBooks = new HashSet<StockBook>(2);
		HashSet<Integer> isbns = new HashSet<Integer>(2);
		stockBooks.add(new ImmutableStockBook(TEST_ISBN + 1,
							  	 "The Art of Computer Programming", "Donald Knuth",
							 		 (float) 300, 100, 0, 0, 0, false));
		stockBooks.add(new ImmutableStockBook(TEST_ISBN + 2,
					  			 "The C Programming Language",
										"Dennis Ritchie and Brian Kerninghan", (float) 50,
										100, 0, 0, 0, false));
    isbns.add(TEST_ISBN+1);
		isbns.add(TEST_ISBN+2);

		storeManager.removeAllBooks();

		Thread addThread0 =
		    new Thread(new AddBooksProcess(storeManager, stockBooks));
		Thread addThread1 =
		    new Thread(new AddBooksProcess(storeManager, stockBooks));
		Thread removeThread0 =
		    new Thread(new RemoveBooksProcess(storeManager, isbns));
		Thread removeThread1 =
		    new Thread(new RemoveBooksProcess(storeManager, isbns));
		addThread0.start();
		//SleepThread();
		addThread1.start();
		//SleepThread();
		removeThread0.start();
		//SleepThread();
		removeThread1.start();

		for (int i = 0; i < repetitions; ++i) {
			int size = storeManager.getBooks().size();
			assertTrue(size == 0 || size == 1);
			try {
				size = storeManager.getBooksByISBN(isbns).size();
				assertEquals(size, 2);
			} catch (BookStoreException err) {
				;
			}
		}

		addThread0.interrupt();
		addThread1.interrupt();
		removeThread0.interrupt();
		removeThread1.interrupt();

	}

	/**
	 * Stress test to perform all operations manipulating both stock books and
	 * book copies concurrently to make sure no deadlocks or unexpected exceptions
	 * occur.
	 */
	//@Test
	public void testConcurrency4() throws BookStoreException {

		int repetitions = 10000;
		int threadsPerMethod = 5;

		HashSet<StockBook> stockBooks = new HashSet<StockBook>(2);
		HashSet<Integer> isbns = new HashSet<Integer>(2);
		stockBooks.add(new ImmutableStockBook(TEST_ISBN + 1,
							  	 "The Art of Computer Programming", "Donald Knuth",
							 		 (float) 300, 100, 0, 0, 0, false));
		stockBooks.add(new ImmutableStockBook(TEST_ISBN + 2,
					  			 "The C Programming Language",
										"Dennis Ritchie and Brian Kerninghan", (float) 50,
										100, 0, 0, 0, false));
    isbns.add(TEST_ISBN+1);
		isbns.add(TEST_ISBN+2);

		HashSet<BookCopy> copies = new HashSet<BookCopy>(2);
		copies.add(new BookCopy(TEST_ISBN+1, 50));
		copies.add(new BookCopy(TEST_ISBN+2, 50));

		storeManager.removeAllBooks();

		ArrayList<Thread> endlessThreads =
		    new ArrayList<Thread>(2*threadsPerMethod);
		ArrayList<Thread> limitedThreads =
		    new ArrayList<Thread>(2*threadsPerMethod);
    for (int i = 0; i < threadsPerMethod; ++i) {
			endlessThreads.add(
				new Thread(new AddBooksProcess(storeManager, stockBooks))
			);
			endlessThreads.add(
			  new Thread(new RemoveBooksProcess(storeManager, isbns))
			);
			limitedThreads.add(
				new Thread(new BuyBooksProcess(client, copies, repetitions))
			);
			limitedThreads.add(
				new Thread(new AddCopiesProcess(storeManager, copies, repetitions))
			);
		}

		for (Thread thread : endlessThreads) thread.start();
		for (Thread thread : limitedThreads) thread.start();
		for (Thread thread : limitedThreads) {
			try {
				thread.join();
			} catch (InterruptedException err) {
				fail();
			}
		}
		for (Thread thread : endlessThreads) thread.interrupt();

	}

	/**
	 * Tests that addCopies and buyBooks respect before-and-after semantics.
	 * That is, it is not possible to see a state where a call to addCopies or buyBooks is half-finished.
	 *
	 * @throws BookStoreException
	 */
	@Test
	public void testBuyAndAddConsistency() throws BookStoreException {
		int ITERATIONS = 100000;
		int INITIAL_COPIES = 10;

		// Adding some books to the store
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book1 = new ImmutableStockBook(1, "Book1", "George RR Testin'", (float) 10, INITIAL_COPIES, 0, 0,
				0, false);
		StockBook book2 = new ImmutableStockBook(2, "Book2", "Ernest Testingway", (float) 10, INITIAL_COPIES, 0, 0,
				0, false);
		StockBook book3 = new ImmutableStockBook(3, "Book3", "Ursula K. Le Test", (float) 10, INITIAL_COPIES, 0, 0,
				0, false);
		booksToAdd.add(book1); booksToAdd.add(book2); booksToAdd.add(book3);
		storeManager.addBooks(booksToAdd);

		// The set of books to buy and add
		Set<BookCopy> bookSet = new HashSet<>();
		BookCopy copy1 = new BookCopy(1,1);
		BookCopy copy2 = new BookCopy(2,2);
		BookCopy copy3 = new BookCopy(3,3);
		bookSet.add(copy1); bookSet.add(copy2); bookSet.add(copy3);

		Thread C1 = new Thread(() -> {
			try {
				for(int i = 0; i < ITERATIONS; i++) {
					client.buyBooks(bookSet);

					storeManager.addCopies(bookSet);
				}
			} catch (BookStoreException ex) {}
		});
		C1.start();

		HashSet<Integer> isbnSet = new HashSet<>();
		isbnSet.add(1); isbnSet.add(2); isbnSet.add(3);

		Thread C2 = new Thread(() -> {
			try {
				for(int i = 0; i < ITERATIONS; i++) {
					List<StockBook> booksInStock = storeManager.getBooksByISBN(isbnSet);
					for (StockBook book : booksInStock) {
						for (BookCopy buyCopy : bookSet) {
							if (book.getISBN() == buyCopy.getISBN()) {
								int copies = book.getNumCopies();
								assertTrue(copies == INITIAL_COPIES
										|| copies == INITIAL_COPIES - buyCopy.getNumCopies());
								break;
							}
						}
					}
				}
			} catch (BookStoreException ex) {}
		});
		C2.start();

		try {
			C1.join();
			C2.join();
		} catch (InterruptedException ex) {
			fail();
		}

	}
	
	/**
	 * Tests that addBooks and removeBooks respect before-and-after semantics.
	 * That is, it is not possible to see a state where a call to addBooks or removeBooks is half-finished.
	 *
	 * @throws BookStoreException
	 */
	//@Test
	public void testAddAndRemoveConcurrent() {
		int ITERATIONS = 100000;
		int INITIAL_COPIES = 10;
		Set<StockBook> booktoAdd = new HashSet<StockBook>();
		HashSet<Integer> isbnSet = new HashSet<Integer>();
		StockBook book1 = new ImmutableStockBook(1, "NewBook", "George RR Testin'", (float) 10, INITIAL_COPIES, 0, 0,
				0, false); 
		StockBook book2 = new ImmutableStockBook(2, "Book2", "George  Orwell'", (float) 10, INITIAL_COPIES, 0, 0,
				0, false); 
		StockBook book3 = new ImmutableStockBook(3, "Book3", "Ursula K. Le Test", (float) 10, INITIAL_COPIES, 0, 0,
				0, false);
		booktoAdd.add(book1);booktoAdd.add(book2);booktoAdd.add(book3);
		isbnSet.add(book1.getISBN());isbnSet.add(book2.getISBN());isbnSet.add(book3.getISBN());
		Thread C1 = new Thread(()->{
			for (int i = 0; i < ITERATIONS; i++ ){
				try {
					storeManager.addBooks(booktoAdd);
					storeManager.removeBooks(isbnSet);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		C1.start();
		
        for(int i = 0; i < ITERATIONS; i++){
            try {
                List<StockBook> bookInStock = storeManager.getBooks();
                assertTrue(bookInStock.size()==1
                        ||bookInStock.size() == 1 + booktoAdd.size() );
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

		try{
			C1.join();
		}catch (InterruptedException ex) {
			fail();
		}
	}
    
	/**
	 * Tests that updateEditorPicks 
	 *
	 * @throws BookStoreException
	 */
	
	//@Test
	public void testUpdateEditorPicks() throws BookStoreException, InterruptedException {
		int ITERATIONS = 100000;
		int NUMBER_OF_BOOKS = 1000;

		//Add books to the store
		Set<StockBook> bookToStock = new HashSet<StockBook>();
		for (int i = 1; i <= NUMBER_OF_BOOKS; i++) {
			StockBook book = new ImmutableStockBook(i, "F", "A", (float) 10, 10, 0, 0,
					0, false);
			bookToStock.add(book);
		}
		storeManager.addBooks(bookToStock);

		Set<BookEditorPick> bookToTrue = new HashSet<BookEditorPick>();
		Set<BookEditorPick> bookToFalse = new HashSet<BookEditorPick>();

		for (int i = 1; i <= NUMBER_OF_BOOKS; i++) {
			BookEditorPick pickT = new BookEditorPick(i,true);
			bookToTrue.add(pickT);

			BookEditorPick pickF = new BookEditorPick(i,false);
			bookToFalse.add(pickF);
		}

		List<StockBook> allBooks = storeManager.getBooks();
		assertTrue(allBooks.size() == NUMBER_OF_BOOKS + 1); //Remember the default book
		assertTrue(client.getEditorPicks(1).size() == 0);

		Thread C1 = new Thread(()->{
			try {
				for (int i = 0; i < ITERATIONS; i++){
					storeManager.updateEditorPicks(bookToTrue);
				}
			} catch (BookStoreException ex) {
				ex.printStackTrace();
			}
		});

		Thread C2 = new Thread(()-> {
			for (int i = 0; i < ITERATIONS; i++) {
				try {
					storeManager.updateEditorPicks(bookToFalse);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		C1.start();
		C2.start();

		for (int i = 0; i < ITERATIONS; i++) {
			int numberOfPicks = client.getEditorPicks(NUMBER_OF_BOOKS).size();
			assertTrue(numberOfPicks == NUMBER_OF_BOOKS
					|| numberOfPicks == 0);
		}

        C1.join();
        C2.join();
	}
	
	/**
	 * Test that rate books respect before-and-after semantics.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */

    @Test

    public void testRatedBooks() throws BookStoreException  {
        
    	int ITERATIONS = 100000;
		Set<StockBook> bookToStock = new HashSet<StockBook>();

		HashSet<BookRating> isbnList1 = new HashSet<>();

		StockBook book1 = new ImmutableStockBook(1, "F", "A", (float) 10, 10, 0, 0,
				0, false);
		StockBook book2 = new ImmutableStockBook(2, "F", "A", (float) 10, 10, 0, 0,
				0, false);
		StockBook book3 = new ImmutableStockBook(3, "F", "A", (float) 10, 10, 0, 0,
				0, false);
		bookToStock.add(book1);bookToStock.add(book2);bookToStock.add(book3);
		
		storeManager.addBooks(bookToStock);

		isbnList1.add(new BookRating(1,1));
		isbnList1.add(new BookRating(2,1));
		isbnList1.add(new BookRating(3,1));

		Thread C1 = new Thread(()->{
			try {
				for (int i = 0; i < ITERATIONS; i++){
					client.rateBooks(isbnList1);
				}
			} catch (BookStoreException ex) {
				ex.printStackTrace();
			}
		});

		C1.start();

		try {
			for (int i = 0; i < ITERATIONS; i++){
			    Set<Integer> isbns = new HashSet<>(Arrays.asList(1,2,3));
				List<StockBook> sbooks = storeManager.getBooksByISBN(isbns);
				assertTrue(sbooks.get(0).getTotalRating() == sbooks.get(1).getTotalRating()
							&& sbooks.get(0).getTotalRating() == sbooks.get(2).getTotalRating());
			}
		} catch (BookStoreException ex) {
			ex.printStackTrace();
		}
		
		try {
			C1.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }
	
	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}
