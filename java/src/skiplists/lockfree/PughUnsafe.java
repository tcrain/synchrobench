package skiplists.lockfree;

import skiplists.lockfree.tower.MarkerUnsafe;
import skiplists.lockfree.tower.TowerUnsafe;
import contention.abstractions.AbstractCompositionalIntSet;

public class PughUnsafe extends AbstractCompositionalIntSet {

	private final TowerUnsafe head;
	private final TowerUnsafe tail;

	private static final int MAX_HEIGHT = 22; // TODO: use variable max height
												// selection
	private static final int TOP = MAX_HEIGHT - 1;

	private final boolean HELP;
	private final boolean HELP_TRAVERSAL;
	private final boolean HELP_CONTAINS_TRAVERSAL;
	private final boolean INSERT_REMOVE;

	final private ThreadLocal<TowerUnsafe[]> thdLocalPrevArray = new ThreadLocal<TowerUnsafe[]>() {
		@Override
		protected synchronized TowerUnsafe[] initialValue() {
			return (TowerUnsafe[]) new TowerUnsafe[MAX_HEIGHT];
		}
	};

	public PughUnsafe() {
		// First and 2nd must be true to ensure non-blocking
		this(true, true, false, false);
	}
	
	public PughUnsafe(boolean help, boolean helpTraversal, boolean helpContainsTraversal) {
		this(help, helpTraversal, helpContainsTraversal, false);
	}

	public PughUnsafe(boolean help, boolean helpTraversal,
			boolean helpContainsTraversal, boolean insertRemove) {

		INSERT_REMOVE = insertRemove;
		HELP = help;
		HELP_TRAVERSAL = helpTraversal;
		HELP_CONTAINS_TRAVERSAL = helpContainsTraversal;

		tail = new TowerUnsafe(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT);
		head = new TowerUnsafe(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT);

		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.set(i, tail);
		}

		// head.height = MAX_HEIGHT;
		// tail.height = MAX_HEIGHT;
		// head.status.set(1);
		// tail.status.set(1);
	}

	/*
	 * TRAVERSE
	 * *****************************************************************
	 * *********************
	 */

	/*
	 * private TowerUnsafe traverse(int val, TowerUnsafe[] prevs, int[]
	 * foundarr) { TowerUnsafe prev = head; TowerUnsafe curr = null; TowerUnsafe
	 * found = null;
	 * 
	 * traverse down the levels of the skiplist for (int level = TOP; level >=
	 * 0; level--) { curr = prev.nexts.get(level);
	 * 
	 * traverse at the current level while (curr.val < val) { prev = curr; curr
	 * = curr.nexts.get(level); }
	 * 
	 * record prevs at each level prevs[level] = prev;
	 * 
	 * record if val is ever found if (curr.val == val) { found = curr; if
	 * (foundarr[0] == -1) { foundarr[0] = level + 1; } } }
	 * 
	 * val not found at any level return found; }
	 */

	private boolean helpRemoval(int level, TowerUnsafe prevPrev,
			TowerUnsafe prev, TowerUnsafe curr) {
		assert ((TowerUnsafe) prev.getNext(level)).marker;
		return prevPrev.compareAndSet(level, prev, curr);
	}

	private boolean helpMarker(int level, TowerUnsafe prev, TowerUnsafe curr) {
		// assert prev.status.get() == 2;
		TowerUnsafe marker = new MarkerUnsafe(curr.val, level, curr);
		// marker.nexts.set(level, curr);
		return prev.compareAndSet(level, curr, marker);
	}

	private TowerUnsafe traverse(int val, TowerUnsafe[] prevs) {
		TowerUnsafe prev = head, prevPrev;
		TowerUnsafe curr = null;
		TowerUnsafe found = null;
		boolean marker = false;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			marker = false;
			prevPrev = null;
			curr = (TowerUnsafe) prev.getNext(level);
			if (curr.marker) {
				curr = (TowerUnsafe) curr.getNext(level);
				marker = true;
			}

			/* traverse at the current level */
			while (curr.val < val) {
				marker = false;
				prevPrev = prev;
				prev = curr;
				curr = (TowerUnsafe) curr.getNext(level);
				if (curr.marker) {
					curr = (TowerUnsafe) curr.getNext(level);
					marker = true;
				}
				if (HELP_TRAVERSAL) {
					if (marker) {
						helpRemoval(level, prevPrev, prev, curr);
					} else if (((TowerUnsafe) prev.getNext(0)).marker) {
						if (helpMarker(level, prev, curr)) {
							helpRemoval(level, prevPrev, prev, curr);
						}
					}
				}
			}
			if (HELP && !HELP_TRAVERSAL) {
				if (marker && prevPrev != null) {
					helpRemoval(level, prevPrev, prev, curr);
				} else if (((TowerUnsafe) prev.getNext(0)).marker) {
					helpMarker(level, prev, curr);
				}
			}

			/* record prevs at each level */
			prevs[level] = prev;

			/* record if val is ever found */
			if (curr.val == val) {
				found = curr;
			}
		}

		/* val not found at any level */
		return found;
	}

	/*
	 * INSERT
	 * *******************************************************************
	 * *********************
	 */

	/*
	 * private boolean validateInsert(TowerUnsafe prev, TowerUnsafe
	 * towerToInsert, int level) {
	 * 
	 * if prev is being inserted or being deleted if (prev.status.get() != 1) {
	 * return false; }
	 * 
	 * if prev is already pointing to towerToInsert at the current level if
	 * (prev.nexts.get(level).val == towerToInsert.val) { return false; }
	 * 
	 * if prev is no longer the appropriate prev to utilize if
	 * (prev.nexts.get(level).val < towerToInsert.val) { return false; }
	 * 
	 * success, so return the version at this level return true; }
	 */

	private boolean tryInsertAtLevel(TowerUnsafe prev,
			TowerUnsafe towerToInsert, int level) {
		// prev.nextLocks[level].lock();
		// try {
		// if (!validateInsert(prev, towerToInsert, level)) {
		// return false;
		// }

		/* if prev is being inserted or being deleted */
		TowerUnsafe next = (TowerUnsafe) prev.getNext(0);
		if (next.marker) {
			// assert (HELP);
			return false;
		}
		assert !(prev.marker);
		TowerUnsafe expect = (TowerUnsafe) prev.getNext(level);
		// if (prev.status.get() == 2 || expect.marker) {
		if (expect.marker) {
			// if (HELP) {
			// next
			// }
			// helping is done in traversal, so don't help here
			return false;
		}

		if (expect == towerToInsert) {
			assert (HELP && level > 0);
			// Someone helped insert this level
			return true;
		} else if (expect.val == towerToInsert.val) {
			return false;
		}

		/* if prev is no longer the appropriate prev to utilize */
		if (expect.val < towerToInsert.val) {
			return false;
		}

		/* set towerToInsert's next */
		towerToInsert.set(level, expect);
		assert (expect != null);

		/* update prev's next */
		if (prev.compareAndSet(level, expect, towerToInsert)) {
			return true;
		}
		/*
		 * linearization point when level = 0
		 */
		return false;
		// } finally {
		// prev.nextLocks[level].unlock();
		// }
	}

	private boolean helpRemovalMarker(TowerUnsafe[] prevs, TowerUnsafe curr) {
		// assert (curr.status.get() == 2);
		int retries = 0;
		// Try to remove one level at a time, if a level fails, just exit since
		// we are not the main one
		for (int level = curr.getHeight() - 1; level >= 0; level--) {
			if (!tryRemoveAtLevel(prevs[level], curr, level, retries)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addInt(int val) {
		TowerUnsafe[] prevs = new TowerUnsafe[MAX_HEIGHT];
		// TowerUnsafe[] prevs = thdLocalPrevArray.get();
		TowerUnsafe towerToInsert = null;
		TowerUnsafe foundTower = null, next;

		retryFromTraverse: while (true) {
			towerToInsert = null;
			foundTower = null;
			/* traverse the skiplist */
			foundTower = traverse(val, prevs);

			/* the value already exists in the skiplist */
			if (foundTower != null) {
				/*
				 * wait-free traversed onto node just before being fully
				 * unlinked / deleted
				 */
				next = (TowerUnsafe) foundTower.getNext(0);
				if (next.marker) {
					/* can not return false as that equals successful contains */
					if (HELP) {
						helpRemovalMarker(prevs, foundTower);
					}
					continue retryFromTraverse;
				}
				/*
				 * insert is linearized at the start thus allows concurrent
				 * inserts
				 */
				/*
				 * remove is linearized at the end thus allows concurrent
				 * inserted
				 */
				return false;
			}

			/* create tower to insert */
			if (towerToInsert == null) {
				int height = getRandomHeight();
				assert (height > 0);
				towerToInsert = new TowerUnsafe(val, MAX_HEIGHT, height);

				/* start tower as locked */
				// towerToInsert.nextLocks[0].lock(); /*
				// * guaranteed to work on new
				// * node
				// */
			}

			/* insert at each level */
			int level = 0;
			while (level < towerToInsert.getHeight()) {
				if (tryInsertAtLevel(prevs[level], towerToInsert, level)) {
					/* insert has succeeded at this level */
					level++;

					/* re-traverse and try again with updated prevs */
				} else {
					/*
					 * race: aborting at level zero might mean another thread
					 * inserted first
					 */
					if (level == 0) {
						continue retryFromTraverse;
					} else if (HELP) {
						next = (TowerUnsafe) towerToInsert.getNext(0);
						if (next.marker) {
							assert level > 0;
							/*
							 * Someone has finished for us, or started deleting
							 * this tower
							 */
							return true;
						}
					}
					/* re-traverse to update list of prevs */
					traverse(val, prevs);
				}
			}

			break retryFromTraverse;
		}

		/* mark as valid */
		// if (HELP) {
		// towerToInsert.status.compareAndSet(0, 1);
		// } else {
		// if (!HELP) {
		// towerToInsert.status.set(1);
		// }
		// }
		// int arr[] = new int[1];
		// arr[0] = -1;
		// foundTower = traverse(val, prevs, arr);
		// assert (foundTower == towerToInsert);
		// assert (arr[0] == foundTower.height);

		/* unlock tower */
		// towerToInsert.nextLocks[0].unlock();

		return true;
	}

	/*
	 * REMOVE
	 * *******************************************************************
	 * *********************
	 */
	/*
	 * private boolean validateRemove(TowerUnsafe prev, TowerUnsafe
	 * towerToRemove, int level) {
	 * 
	 * if prev is being inserted or being deleted if (prev.status.get() != 1) {
	 * return false; }
	 * 
	 * if prev is not pointing to the tower at the current level if
	 * (prev.nexts.get(level) != towerToRemove) { return false; }
	 * 
	 * success, so return the version at this level return true;
	 * 
	 * }
	 */

	private boolean tryRemoveAtLevel(TowerUnsafe prev,
			TowerUnsafe towerToRemove, int level, int retries) {
		// prev.nextLocks[level].lock();
		// towerToRemove.nextLocks[level].lock();
		// try {
		// if (!validateRemove(prev, towerToRemove, level)) {
		// return false;
		// }

		/* if prev is being inserted or being deleted */
		// int status = prev.status.get();
		// assert (status != 3);
		// if (status == 2) {
		// Helping is done in traversal
		// return false;
		// }
		// if (prev.status.get() != 1) {
		// return false;
		// }

		/* if prev is not pointing to the tower at the current level */
		TowerUnsafe next = (TowerUnsafe) prev.getNext(level);
		if (prev.getNext(level) != towerToRemove) {
			if (HELP) {
				if (next.marker) {
					next = (TowerUnsafe) next.getNext(level);
				}
				while (next.val < towerToRemove.val) {
					prev = next;
					next = (TowerUnsafe) prev.getNext(level);
					if (next.marker) {
						next = (TowerUnsafe) next.getNext(level);
					}
				}
				if (next != towerToRemove) {
					/* Someone did the removal for us */
					return true;
				}
			}
			return false;
		}

		assert !(prev.marker);

		// TowerUnsafe next;
		while (true) {
			next = (TowerUnsafe) towerToRemove.getNext(level);
			if (next.marker) {
				// if (!HELP) {
				// assert (next == marker);
				// }
				next = (TowerUnsafe) next.getNext(level);
				break;
			}
			assert (next != null && !(next.marker));
			MarkerUnsafe marker = new MarkerUnsafe(towerToRemove.val, level,
					next);
			// marker.nexts.set(level, next);
			if (towerToRemove.compareAndSet(level, next, marker)) {
				break;
			}
		}

		assert (next != null && !(next.marker));
		/* update prev to skip over towerToRemove */
		if (!prev.compareAndSet(level, towerToRemove, next)) {
			return false;
		}
		/*
		 * linearization point when level = 0
		 */

		return true;
		// } finally {
		// prev.nextLocks[level].unlock();
		// towerToRemove.nextLocks[level].unlock();
		// }

	}

	/*
	 * private boolean helpInsert(TowerUnsafe[] prevs, TowerUnsafe
	 * towerToInsert) { int level = 1; while (level < towerToInsert.height) { if
	 * (tryInsertAtLevel(prevs[level], towerToInsert, level)) { insert has
	 * succeeded at this level level++; } else { if (towerToInsert.status.get()
	 * != 0) { // Someone has finished for us, or started deleting this // tower
	 * return true; } return false; } } if
	 * (towerToInsert.status.compareAndSet(0, 1)) { return true; } return false;
	 * }
	 */

	@Override
	public boolean removeInt(int val) {
		// TowerUnsafe[] prevs = thdLocalPrevArray.get();
		TowerUnsafe[] prevs = new TowerUnsafe[MAX_HEIGHT];
		TowerUnsafe foundTower = null, next;
		// int status;

		while (true) {
			/* traverse the skiplist */
			foundTower = traverse(val, prevs);

			/* val is not found, so remove fails */
			if (foundTower == null) {
				return false;
			}
			assert !(foundTower.marker);

			// TowerUnsafe prev = prevs[0];
			// prev.nextLocks[0].lock();
			// foundTower.nextLocks[0].lock();
			// try {

			/* pre-lock validation */

			/*
			 * found tower is already being removed, or is not fully inserted
			 */

			// status = foundTower.status.get();
			next = (TowerUnsafe) foundTower.getNext(0);
			if (next.marker) {
				/* insert linearizes at the start thus blocks concurrent removes */
				/* remove linearizes at the end thus blocks concurrent removes */
				return false;
			} else {// if (status == 1) {
				// if (HELP) {
				// Should help insert
				// if (!helpInsert(prevs, foundTower)) {
				// continue;
				// }
				// } else {
				break;
				// }
			}
			// assert (status == 0);
		}
		// if (prev.nexts.get(0) != foundTower || prev.status != 1) {
		// continue;
		// }

		// int arr[] = new int[1];
		// arr[0] = -1;
		// assert (foundTower == traverse(val, prevs, arr));
		// assert (arr[0] == foundTower.height);
		/* mark tower as being deleted */
		// if (!foundTower.status.compareAndSet(status, 2)) {
		if (!helpMarker(0, foundTower, next)) {
			return false;
		}
		// TowerUnsafe marker = new Marker(val, foundTower.height);

		/* remove at each level */
		int level = (foundTower.getHeight() - 1);
		int retries = 0;
		while (level >= 0) {
			if (tryRemoveAtLevel(prevs[level], foundTower, level, retries)) {
				/* remove has succeeded at this level */
				level--;
				retries = 0;
				// return true;
				/* re-traverse and try again with updated prevs */
			} else {
				/* re-traverse to update list of prevs */
				traverse(val, prevs);
				retries++;
			}
		}

		/* no need to unlock tower as it is fully unlinked */
		// foundTower.unlockAndIncrementVersion();

		return true;
		// } finally {
		// prev.nextLocks[0].unlock();
		// foundTower.nextLocks[0].unlock();
		// }

	}

	/*
	 * CONTAINS
	 * *****************************************************************
	 * *********************
	 */

	@Override
	public boolean containsInt(int val) {
		TowerUnsafe prev = head, prevPrev;
		TowerUnsafe curr = null;
		boolean marker;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			marker = false;
			prevPrev = null;
			curr = (TowerUnsafe) prev.getNext(level);
			if (curr.marker) {
				curr = (TowerUnsafe) curr.getNext(level);
			}

			/* traverse at the current level */
			while (curr.val < val) {
				marker = false;
				prevPrev = prev;
				prev = curr;
				curr = (TowerUnsafe) curr.getNext(level);
				if (curr.marker) {
					curr = (TowerUnsafe) curr.getNext(level);
				}
				if (HELP_CONTAINS_TRAVERSAL) {
					if (marker) {
						helpRemoval(level, prevPrev, prev, curr);
					} else if (((TowerUnsafe) prev.getNext(0)).marker) {
						if (helpMarker(level, prev, curr)) {
							helpRemoval(level, prevPrev, prev, curr);
						}
					}
				}
			}

			/* if val is found at the current level */
			if (curr.val == val) {
				/* success depends on if the TowerUnsafe is not deleted */
				// return (curr.status.get() != 2);
				return !(((TowerUnsafe) curr.getNext(0)).marker);
			}
		}

		/* val not found at any level */
		return false;
	}

	/*
	 * UTILITY
	 * ******************************************************************
	 * *********************
	 */

	@Override
	public int size() {
		int size = 0;
		TowerUnsafe curr = (TowerUnsafe) head.getNext(0);
		while (curr != tail) {
			if (curr.getHeight() != 0 && !((TowerUnsafe) curr.getNext(0)).marker) {
				size++;
			}
			if (((TowerUnsafe) curr.getNext(0)).marker) {
				curr = (TowerUnsafe) curr.getNext(0);
			}
			curr = (TowerUnsafe) curr.getNext(0);
		}
		// this.print();
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.set(i, tail);
		}
		// head.resetLocks();
		// tail.resetLocks();
		assert head.getHeight() == MAX_HEIGHT;
		assert tail.getHeight() == MAX_HEIGHT;
	}

	private int getRandomHeight() {
		/* height from 1 to TOP */
		return Math
				.min(TOP, (skiplists.RandomLevelGenerator.randomLevel() + 1));
	}

	void print() {
		System.out.println("skiplist:");
		for (int i = MAX_HEIGHT - 1; i >= 0; i--) {
			TowerUnsafe next = head;
			while (next != tail) {
				System.out.print(next.val + "-- ");
				next = (TowerUnsafe) next.getNext(i);
			}
			System.out.println();
		}
		System.out.println();
	}

}
