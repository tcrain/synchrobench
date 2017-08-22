package skiplists.lockfree;

import skiplists.lockfree.tower.MarkerStatus;
import skiplists.lockfree.tower.TowerStatus;
import contention.abstractions.AbstractCompositionalIntSet;

public class PughStatus extends AbstractCompositionalIntSet {

	private final TowerStatus head;
	private final TowerStatus tail;

	private static final int MAX_HEIGHT = 22; // TODO: use variable max height
												// selection
	private static final int TOP = MAX_HEIGHT - 1;

	private final boolean HELP;
	private final boolean HELP_TRAVERSAL;
	private final boolean HELP_CONTAINS_TRAVERSAL;

	public PughStatus() {
		this(true, true, false);
	}

	public PughStatus(boolean help, boolean helpTraversal, boolean helpContainsTraversal) {

		HELP = help;
		HELP_TRAVERSAL = helpTraversal;
		HELP_CONTAINS_TRAVERSAL = helpContainsTraversal;

		tail = new TowerStatus(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT, HELP);
		head = new TowerStatus(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT, HELP);

		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.set(i, tail);
		}

		// head.height = MAX_HEIGHT;
		// tail.height = MAX_HEIGHT;
		head.setStatus(1);
		tail.setStatus(1);
	}

	/*
	 * TRAVERSE
	 * *****************************************************************
	 * *********************
	 */

	/*
	 * private TowerStatus traverse(int val, TowerStatus[] prevs, int[] foundarr) {
	 * TowerStatus prev = head; TowerStatus curr = null; TowerStatus found = null;
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

	private boolean helpRemoval(int level, TowerStatus prevPrev, TowerStatus prev,
			TowerStatus curr) {
		assert (((TowerStatus)prev.getNext(level)).marker);
		return prevPrev.compareAndSet(level, prev, curr);
	}

	private boolean helpMarker(int level, TowerStatus prev, TowerStatus curr) {
		assert prev.getStatus() == 2;
		TowerStatus marker = new MarkerStatus(curr.val, level, curr);
		//marker.nexts.set(level, curr);
		return prev.compareAndSet(level, curr, marker);
	}

	private TowerStatus traverse(int val, TowerStatus[] prevs) {
		TowerStatus prev = head, prevPrev;
		TowerStatus curr = null;
		TowerStatus found = null;
		boolean marker = false;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			marker = false;
			prevPrev = null;
			curr = (TowerStatus) prev.getNext(level);
			if (curr.marker) {
				curr = (TowerStatus) curr.getNext(level);
				marker = true;
			}

			/* traverse at the current level */
			while (curr.val < val) {
				marker = false;
				prevPrev = prev;
				prev = curr;
				curr = (TowerStatus) curr.getNext(level);
				if (curr.marker) {
					curr = (TowerStatus) curr.getNext(level);
					marker = true;
				}
				if(HELP_TRAVERSAL) {
					if(marker) {
						helpRemoval(level, prevPrev, prev, curr);
					} else if(prev.getStatus() == 2) {
						if(helpMarker(level, prev, curr)) {
							helpRemoval(level, prevPrev, prev, curr);
						}
					}
				}
			}
			if (HELP && !HELP_TRAVERSAL) {
				if (marker && prevPrev != null) {
					helpRemoval(level, prevPrev, prev, curr);
				} else if (prev.getStatus() == 2) {
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
	 * private boolean validateInsert(TowerStatus prev, TowerStatus towerToInsert, int
	 * level) {
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

	private boolean tryInsertAtLevel(TowerStatus prev, TowerStatus towerToInsert,
			int level) {
		// prev.nextLocks[level].lock();
		// try {
		// if (!validateInsert(prev, towerToInsert, level)) {
		// return false;
		// }

		/* if prev is being inserted or being deleted */
		if (towerToInsert.getStatus() > 1) {
			assert (HELP);
			return false;
		}
		assert !(prev.marker);
		TowerStatus expect = (TowerStatus) prev.getNext(level);
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

	private boolean helpRemovalMarker(TowerStatus[] prevs, TowerStatus curr) {
		assert (curr.getStatus() == 2);
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
		TowerStatus[] prevs = new TowerStatus[MAX_HEIGHT];
		TowerStatus towerToInsert = null;
		TowerStatus foundTower = null;

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
				if (foundTower.getStatus() == 2) {
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
				towerToInsert = new TowerStatus(val, MAX_HEIGHT, height, HELP);

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
						if (towerToInsert.getStatus() != 1) {
							assert (towerToInsert.getStatus() == 2 && level > 0);
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
		if (!HELP) {
			towerToInsert.setStatus(1);
		}
		// }
		// int arr[] = new int[1];
		// arr[0] = -1;
		// foundTower = traverse(val, prevs, arr);
		// assert (foundTower == towerToInsert);
		// assert (arr[0] == foundTower.getHeight());

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
	 * private boolean validateRemove(TowerStatus prev, TowerStatus towerToRemove, int
	 * level) {
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

	private boolean tryRemoveAtLevel(TowerStatus prev, TowerStatus towerToRemove,
			int level, int retries) {
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
		TowerStatus next = (TowerStatus) prev.getNext(level);
		if (prev.getNext(level) != towerToRemove) {
			if (HELP) {
				if (next.marker) {
					next = (TowerStatus) next.getNext(level);
				}
				while (next.val < towerToRemove.val) {
					prev = next;
					next = (TowerStatus) prev.getNext(level);
					if (next.marker) {
						next = (TowerStatus) next.getNext(level);
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

		// TowerStatus next;
		while (true) {
			next = (TowerStatus) towerToRemove.getNext(level);
			if (next.marker) {
				//if (!HELP) {
				//	assert (next == marker);
				//}
				next = (TowerStatus) next.getNext(level);
				break;
			}
			assert (next != null && !(next.marker));
			MarkerStatus marker = new MarkerStatus(towerToRemove.val, level, next);
			//marker.nexts.set(level, next);
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

	private boolean helpInsert(TowerStatus[] prevs, TowerStatus towerToInsert) {
		int level = 1;
		while (level < towerToInsert.getHeight()) {
			if (tryInsertAtLevel(prevs[level], towerToInsert, level)) {
				/* insert has succeeded at this level */
				level++;
			} else {
				if (towerToInsert.getStatus() != 0) {
					// Someone has finished for us, or started deleting this
					// tower
					return true;
				}
				return false;
			}
		}
		if (towerToInsert.compareAndSetStatus(0, 1)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean removeInt(int val) {
		TowerStatus[] prevs = new TowerStatus[MAX_HEIGHT];
		TowerStatus foundTower = null;
		int status;

		while (true) {
			/* traverse the skiplist */
			foundTower = traverse(val, prevs);

			/* val is not found, so remove fails */
			if (foundTower == null) {
				return false;
			}
			assert !(foundTower.marker);

			// TowerStatus prev = prevs[0];
			// prev.nextLocks[0].lock();
			// foundTower.nextLocks[0].lock();
			// try {

			/* pre-lock validation */

			/*
			 * found tower is already being removed, or is not fully inserted
			 */
			status = foundTower.getStatus();
			if (status == 2) {
				/* insert linearizes at the start thus blocks concurrent removes */
				/* remove linearizes at the end thus blocks concurrent removes */
				return false;
			} else if (status == 1) {
				// if (HELP) {
				// Should help insert
				// if (!helpInsert(prevs, foundTower)) {
				// continue;
				// }
				// } else {
				break;
				// }
			}
			assert (status == 0);
		}
		// if (prev.nexts.get(0) != foundTower || prev.status != 1) {
		// continue;
		// }

		// int arr[] = new int[1];
		// arr[0] = -1;
		// assert (foundTower == traverse(val, prevs, arr));
		// assert (arr[0] == foundTower.getHeight());
		/* mark tower as being deleted */
		if (!foundTower.compareAndSetStatus(status, 2)) {
			return false;
		}
		//TowerStatus marker = new Marker(val, foundTower.getHeight());

		/* remove at each level */
		int level = (foundTower.getHeight() - 1);
		int retries = 0;
		while (level >= 0) {
			if (tryRemoveAtLevel(prevs[level], foundTower, level,
					retries)) {
				/* remove has succeeded at this level */
				level--;
				retries = 0;

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
		TowerStatus prev = head, prevPrev;
		TowerStatus curr = null;
		boolean marker;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			marker = false;
			prevPrev = null;
			curr = (TowerStatus) prev.getNext(level);
			if (curr.marker) {
				curr = (TowerStatus) curr.getNext(level);
			}

			/* traverse at the current level */
			while (curr.val < val) {
				marker = false;
				prevPrev = prev;
				prev = curr;
				curr = (TowerStatus) curr.getNext(level);
				if (curr.marker) {
					curr = (TowerStatus) curr.getNext(level);
				}
				if(HELP_CONTAINS_TRAVERSAL) {
					if(marker) {
						helpRemoval(level, prevPrev, prev, curr);
					} else if(prev.getStatus() == 2) {
						if(helpMarker(level, prev, curr)) {
							helpRemoval(level, prevPrev, prev, curr);
						}
					}
				}
			}


			/* if val is found at the current level */
			if (curr.val == val) {
				/* success depends on if the TowerStatus is not deleted */
				return (curr.getStatus() != 2);
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
		TowerStatus curr = (TowerStatus) head.getNext(0);
		while (curr != tail) {
			if (curr.getHeight() != 0 && curr.getStatus() == 1) {
				size++;
			}
			if (curr.getStatus() == 2) {
				assert ((TowerStatus)curr.getNext(0)).marker;
				curr = (TowerStatus) curr.getNext(0);
			} else
				assert curr.getStatus() == 1;
			curr = (TowerStatus) curr.getNext(0);
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
			TowerStatus next = head;
			while (next != tail) {
				System.out.print(next.val + "-- ");
				next = (TowerStatus) next.getNext(i);
			}
			System.out.println();
		}
		System.out.println();
	}

	/*
	 * public class TowerStatus { public final int val;
	 * 
	 * public final AtomicReferenceArray<TowerStatus> nexts;
	 * 
	 * public final int height;
	 * 
	 * final boolean marker;
	 * 
	 * 0 = being inserted, 1 = valid, 2 = being deleted, 3 = marker public final
	 * AtomicInteger status;
	 * 
	 * // final ReentrantLock[] nextLocks;
	 * 
	 * public TowerStatus(int val, int height) { status = null; nexts = null;
	 * this.getHeight() = height; this.val = val; this.marker = true; }
	 * 
	 * public TowerStatus(int val, int maxHeight, int height) { if (HELP) {
	 * status = new AtomicInteger(1); } else { status = new AtomicInteger(0); }
	 * this.val = val; this.nexts = new
	 * AtomicReferenceArray<TowerStatus>(height); this.getHeight() = height;
	 * this.marker = false; }
	 * 
	 * public TowerStatus getNext(int level) { return nexts.get(level); } }
	 * public class Marker extends TowerStatus { final TowerStatus next;
	 * 
	 * public Marker(int val, int level, TowerStatus next) { super(val, level);
	 * this.next = next; }
	 * 
	 * @Override public TowerStatus getNext(int level) { assert level == height;
	 * return next; } }
	 */
}
