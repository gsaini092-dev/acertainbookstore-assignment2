package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/**
 * {@link TwoLevelLockingConcurrentCertainBookStore} implements the
 * {@link BookStore} and {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, BookStoreBook> bookMap = null;

	private ReadWriteLock globalLock = null;
	private ConcurrentHashMap<Integer, ReadWriteLock> lockMap = null;

	/**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
		globalLock = new ReentrantReadWriteLock();
		lockMap = new ConcurrentHashMap<Integer, ReadWriteLock>();
	}

	private void takeLocalReadLock(int isbn) throws BookStoreException {
		if (!lockMap.containsKey(isbn)) {
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.NOT_AVAILABLE);
		}

		ReadWriteLock localLock = lockMap.get(isbn);

		if (localLock == null) {
			throw new BookStoreException("Lock " + isbn + " does not exist");
		}

		globalLock.readLock().lock();
		localLock.readLock().lock();
	}

	private void releaseLocalReadLock(int isbn) {
		if (!bookMap.containsKey(isbn)) {
			/*
			 * In case of locking errors we should always try to release any potential lock.
			 * So, if we try to release a nonexistent lock, we just return instead of
			 * throwing an error
			 */
			return;
		}

		ReadWriteLock localLock = lockMap.get(isbn);
		if (localLock == null) {
			return;
		}
		try {
			localLock.readLock().unlock();
			globalLock.readLock().unlock();
		} catch (IllegalMonitorStateException ex) {
			;
		}
	}

	private void takeLocalWriteLock(int isbn) throws BookStoreException {
		if (!lockMap.containsKey(isbn)) {
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.NOT_AVAILABLE);
		}

		ReadWriteLock localLock = lockMap.get(isbn);
		if (localLock == null) {
			throw new BookStoreException("Lock " + isbn + " does not exist");
		}

		globalLock.readLock().lock();
		localLock.writeLock().lock();
	}

	private void releaseLocalWriteLock(int isbn) {
		if (BookStoreUtility.isInvalidISBN(isbn)) {
			return;
		}

		ReadWriteLock localLock = lockMap.get(isbn);
		if (localLock == null) {
			return;
		}

		try {
			localLock.writeLock().unlock();
			globalLock.readLock().unlock();
		} catch (IllegalMonitorStateException ex) {
			;
		}
	}

	private void takeGlobalLock() {
		globalLock.writeLock().lock();
	}

	private void releaseGlobalLock() {
		globalLock.writeLock().unlock();
	}

	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}

	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}

	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}

	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		takeGlobalLock();

		try {
			if (bookSet == null) {
				throw new BookStoreException(BookStoreConstants.NULL_INPUT);
			}

			// Check if all are there
			for (StockBook book : bookSet) {
				validate(book);
			}

			for (StockBook book : bookSet) {
				int isbn = book.getISBN();
				bookMap.put(isbn, new BookStoreBook(book));
			}
		} finally {
			releaseGlobalLock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		int numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		try {
			for (BookCopy bookCopy : bookCopiesSet) {
				/* take locks */
				takeLocalWriteLock(bookCopy.getISBN());
				validate(bookCopy);
			}

			BookStoreBook book;

			// Update the number of copies
			for (BookCopy bookCopy : bookCopiesSet) {
				isbn = bookCopy.getISBN();
				numCopies = bookCopy.getNumCopies();
				book = bookMap.get(isbn);
				book.addCopies(numCopies);
			}
		} finally {
			for (BookCopy bc : bookCopiesSet) {
				releaseLocalWriteLock(bc.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		Collection<BookStoreBook> bookMapValues = bookMap.values();

		try {
			return bookMapValues.stream().map(book -> book.immutableStockBook()).collect(Collectors.toList());
		} finally {
			for (BookStoreBook book : bookMapValues) {
				releaseLocalReadLock(book.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		int isbnValue;

		try {
			for (BookEditorPick editorPickArg : editorPicks) {
				/* take locks */
				takeLocalWriteLock(editorPickArg.getISBN());
				validate(editorPickArg);
			}

			for (BookEditorPick editorPickArg : editorPicks) {
				bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
			}
		} finally {
			for (BookEditorPick bp : editorPicks) {
				releaseLocalWriteLock(bp.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we buy are there first.
		int isbn;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();

		try {
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				isbn = bookCopyToBuy.getISBN();

				/* take locks */
				takeLocalWriteLock(isbn);
				validate(bookCopyToBuy);

				book = bookMap.get(isbn);

				if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
					// If we cannot sell the copies of the book, it is a miss.
					salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
					saleMiss = true;
				}
			}

			// We throw exception now since we want to see how many books in the
			// order incurred misses which is used by books in demand
			if (saleMiss) {
				for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
					book = bookMap.get(saleMissEntry.getKey());
					book.addSaleMiss(saleMissEntry.getValue());
				}
				throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
			}

			// Then make the purchase.
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				book = bookMap.get(bookCopyToBuy.getISBN());
				book.buyCopies(bookCopyToBuy.getNumCopies());
			}
		} finally {
			for (BookCopy bc : bookCopiesToBuy) {
				releaseLocalWriteLock(bc.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		try {
			for (Integer ISBN : isbnSet) {
				/* take locks */
				takeLocalReadLock(ISBN);
				validateISBNInStock(ISBN);
			}

			return isbnSet.stream().map(isbn -> bookMap.get(isbn).immutableStockBook()).collect(Collectors.toList());
		} finally {
			for (Integer isbn : isbnSet) {
				releaseLocalReadLock(isbn);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		try {
			// Check that all ISBNs that we rate are there to start with.
			for (Integer ISBN : isbnSet) {
				/* take locks */
				takeLocalReadLock(ISBN);
				validateISBNInStock(ISBN);
			}

			return isbnSet.stream().map(isbn -> bookMap.get(isbn).immutableBook()).collect(Collectors.toList());
		} finally {
			for (Integer isbn : isbnSet) {
				releaseLocalReadLock(isbn);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		try {
			List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream().map(pair -> pair.getValue())
					.filter(book -> book.isEditorPick()).collect(Collectors.toList());

			// Find numBooks random indices of books that will be picked.
			Random rand = new Random();
			Set<Integer> tobePicked = new HashSet<>();
			int rangePicks = listAllEditorPicks.size();

			if (rangePicks <= numBooks) {

				// We need to add all books.
				for (int i = 0; i < listAllEditorPicks.size(); i++) {
					tobePicked.add(i);
				}
			} else {

				// We need to pick randomly the books that need to be returned.
				int randNum;

				while (tobePicked.size() < numBooks) {
					randNum = rand.nextInt(rangePicks);
					tobePicked.add(randNum);
				}
			}

			// Return all the books by the randomly chosen indices.
			return tobePicked.stream().map(index -> listAllEditorPicks.get(index).immutableBook())
					.collect(Collectors.toList());

		} finally {
			for (BookStoreBook bs : bookMapValues) {
				releaseLocalReadLock(bs.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}

		List<BookStoreBook> listSortedRatedBooks = new ArrayList<BookStoreBook>();
		List<Book> listTopRatedBooks = new ArrayList<>();
		Iterator<Entry<Integer, BookStoreBook>> it = bookMap.entrySet().iterator();
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		BookStoreBook book;

		try {

			// Get all books
			while (it.hasNext()) {
				Entry<Integer, BookStoreBook> pair = it.next();
				book = pair.getValue();

				/* take locks */
				takeLocalReadLock(pair.getKey());
				listSortedRatedBooks.add(book);
			}

			// Sort all books that are rated

			Collections.sort(listSortedRatedBooks, new Comparator<BookStoreBook>() {

				public int compare(BookStoreBook b1, BookStoreBook b2) {

					float averageRate1 = b1.getAverageRating();
					float averageRate2 = b2.getAverageRating();
					if (averageRate1 < averageRate2)
						return 1;
					else {
						if (averageRate1 == averageRate2)
							return 0;
						else
							return -1;
					}
				}

			});

			// Find numBooks descending indices of books that will be picked.

			Set<Integer> tobePicked = new HashSet<>();
			int rangePicks = listSortedRatedBooks.size();
			if (rangePicks <= numBooks) {

				// We need to add all books.

				for (int i = 0; i < rangePicks; i++) {
					tobePicked.add(i);
				}

			} else {

				// We need to pick top k rated books that need to be returned.

				int indexNum = 0;
				while (tobePicked.size() < numBooks) {
					tobePicked.add(indexNum);
					indexNum++;
				}
			}

			// Get the numBooks books.

			for (Integer index : tobePicked) {
				book = listSortedRatedBooks.get(index);
				listTopRatedBooks.add(book.immutableBook());
			}
			return listTopRatedBooks;
		} finally {
			for (BookStoreBook bs : bookMapValues) {
				releaseLocalReadLock(bs.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		if (bookRating == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		// Check that all ISBNs that we rate are there first.

		int isbn;
		int ratings;
		BookStoreBook book;

		/* take locks */
		for (BookRating br : bookRating) {
			takeLocalWriteLock(br.getISBN());
		}

		try {
			for (BookRating bookToRate : bookRating) {

				isbn = bookToRate.getISBN();

				ratings = bookToRate.getRating();

				if (BookStoreUtility.isInvalidISBN(isbn)) {
					throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
				}

				if (!bookMap.containsKey(isbn)) {
					throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.NOT_AVAILABLE);
				}

				if (BookStoreUtility.isInvalidRating(ratings)) {
					throw new BookStoreException(BookStoreConstants.RATING + ratings + BookStoreConstants.INVALID);
				}

				book = bookMap.get(isbn);

				if (book.hadSaleMiss() == true) {
					// If the book is not in the collection it will throw a exception.
					throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
				}
			}
			// update the sum of ratings and the number of ratings

			for (BookRating bookToRate : bookRating) {

				book = bookMap.get(bookToRate.getISBN());

				book.addRating(bookToRate.getRating());
			}

		} finally {
			for (BookRating br : bookRating) {
				releaseLocalWriteLock(br.getISBN());
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		bookMap.clear();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		takeGlobalLock();

		try {
			for (Integer ISBN : isbnSet) {
				if (BookStoreUtility.isInvalidISBN(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}

				if (!bookMap.containsKey(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
				}
			}

			for (int isbn : isbnSet) {
				bookMap.remove(isbn);
			}
		} finally {
			releaseGlobalLock();
		}
	}
}
