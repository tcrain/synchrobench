package skiplists.lockfree;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import contention.abstractions.AbstractCompositionalIntSet;

public class Pugh extends AbstractCompositionalIntSet {

	private final TowerFG head;
	private final TowerFG tail;

	private static final int MAX_HEIGHT = 22; // TODO: use variable max height
												// selection
	private static final int TOP = MAX_HEIGHT - 1;

	private final boolean HELP;

	public Pugh() {
		this(true);
	}

	public Pugh(boolean help) {

		HELP = help;

		tail = new TowerFG(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT);
		head = new TowerFG(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT);

		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.nexts.set(i, tail);
		}

		// head.height = MAX_HEIGHT;
		// tail.height = MAX_HEIGHT;
		head.status.set(1);
		tail.status.set(1);
	}

	/*
	 * TRAVERSE
	 * *****************************************************************
	 * *********************
	 */

	/*
	 * private TowerFG traverse(int val, TowerFG[] prevs, int[] foundarr) {
	 * TowerFG prev = head; TowerFG curr = null; TowerFG found = null;
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

	private boolean helpRemoval(int level, TowerFG prevPrev, TowerFG prev,
			TowerFG curr) {
		assert (prev.nexts.get(level).marker);
		return prevPrev.nexts.compareAndSet(level, prev, curr);
	}

	private boolean helpMarker(int level, TowerFG prev, TowerFG curr) {
		assert prev.status.get() == 2;
		TowerFG marker = new TowerFG(curr.val, MAX_HEIGHT, curr.height, true);
		marker.nexts.set(level, curr);
		return prev.nexts.compareAndSet(level, curr, marker);
	}

	private TowerFG traverse(int val, TowerFG[] prevs) {
		TowerFG prev = head, prevPrev;
		TowerFG curr = null;
		TowerFG found = null;
		boolean marker = false;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			marker = false;
			prevPrev = null;
			curr = prev.nexts.get(level);
			if (curr.marker) {
				curr = curr.nexts.get(level);
				marker = true;
			}

			/* traverse at the current level */
			while (curr.val < val) {
				marker = false;
				prevPrev = prev;
				prev = curr;
				curr = curr.nexts.get(level);
				if (curr.marker) {
					curr = curr.nexts.get(level);
					marker = true;
				}
			}
			if (HELP) {
				if (marker && prevPrev != null) {
					helpRemoval(level, prevPrev, prev, curr);
				} else if (prev.status.get() == 2) {
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
	 * private boolean validateInsert(TowerFG prev, TowerFG towerToInsert, int
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

	private boolean tryInsertAtLevel(TowerFG prev, TowerFG towerToInsert,
			int level) {
		// prev.nextLocks[level].lock();
		// try {
		// if (!validateInsert(prev, towerToInsert, level)) {
		// return false;
		// }

		/* if prev is being inserted or being deleted */
		if (towerToInsert.status.get() > 1) {
			assert (HELP);
			return false;
		}
		assert (!prev.marker);
		TowerFG expect = prev.nexts.get(level);
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
		towerToInsert.nexts.set(level, expect);
		assert (expect != null);

		/* update prev's next */
		if (prev.nexts.compareAndSet(level, expect, towerToInsert)) {
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

	private boolean helpRemovalMarker(TowerFG[] prevs, TowerFG curr) {
		assert (curr.status.get() == 2);
		int retries = 0;
		TowerFG marker = new TowerFG(curr.val, MAX_HEIGHT, curr.height, true);
		// Try to remove one level at a time, if a level fails, just exit since
		// we are not the main one
		for (int level = curr.height - 1; level >= 0; level--) {
			if (!tryRemoveAtLevel(prevs[level], curr, marker, level, retries)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addInt(int val) {
		TowerFG[] prevs = new TowerFG[MAX_HEIGHT];
		TowerFG towerToInsert = null;
		TowerFG foundTower = null;

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
				if (foundTower.status.get() == 2) {
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
				towerToInsert = new TowerFG(val, MAX_HEIGHT, height);

				/* start tower as locked */
				// towerToInsert.nextLocks[0].lock(); /*
				// * guaranteed to work on new
				// * node
				// */
			}

			/* insert at each level */
			int level = 0;
			while (level < towerToInsert.height) {
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
						if (towerToInsert.status.get() != 1) {
							assert (towerToInsert.status.get() == 2 && level > 0);
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
			towerToInsert.status.set(1);
		}
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
	 * private boolean validateRemove(TowerFG prev, TowerFG towerToRemove, int
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

	private boolean tryRemoveAtLevel(TowerFG prev, TowerFG towerToRemove,
			TowerFG marker, int level, int retries) {
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
		TowerFG next = prev.nexts.get(level);
		if (prev.nexts.get(level) != towerToRemove) {
			if (HELP) {
				if (next.marker) {
					next = next.nexts.get(level);
				}
				while (next.val < towerToRemove.val) {
					prev = next;
					next = prev.nexts.get(level);
					if (next.marker) {
						next = next.nexts.get(level);
					}
				}
				if (next != towerToRemove) {
					/* Someone did the removal for us */
					return true;
				}
			}
			return false;
		}

		assert (!prev.marker);

		// TowerFG next;
		while (true) {
			next = towerToRemove.nexts.get(level);
			if (next.marker) {
				if (!HELP) {
					assert (next == marker);
				}
				next = next.nexts.get(level);
				break;
			}
			assert (next != null && !next.marker);
			marker.nexts.set(level, next);
			if (towerToRemove.nexts.compareAndSet(level, next, marker)) {
				break;
			}
		}

		assert (next != null && !next.marker);
		/* update prev to skip over towerToRemove */
		if (!prev.nexts.compareAndSet(level, towerToRemove, next)) {
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

	private boolean helpInsert(TowerFG[] prevs, TowerFG towerToInsert) {
		int level = 1;
		while (level < towerToInsert.height) {
			if (tryInsertAtLevel(prevs[level], towerToInsert, level)) {
				/* insert has succeeded at this level */
				level++;
			} else {
				if (towerToInsert.status.get() != 0) {
					// Someone has finished for us, or started deleting this
					// tower
					return true;
				}
				return false;
			}
		}
		if (towerToInsert.status.compareAndSet(0, 1)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean removeInt(int val) {
		TowerFG[] prevs = new TowerFG[MAX_HEIGHT];
		TowerFG foundTower = null;
		int status;

		while (true) {
			/* traverse the skiplist */
			foundTower = traverse(val, prevs);

			/* val is not found, so remove fails */
			if (foundTower == null) {
				return false;
			}
			assert (!foundTower.marker);

			// TowerFG prev = prevs[0];
			// prev.nextLocks[0].lock();
			// foundTower.nextLocks[0].lock();
			// try {

			/* pre-lock validation */

			/*
			 * found tower is already being removed, or is not fully inserted
			 */
			status = foundTower.status.get();
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
		// assert (arr[0] == foundTower.height);
		/* mark tower as being deleted */
		if (!foundTower.status.compareAndSet(status, 2)) {
			return false;
		}
		TowerFG marker = new TowerFG(val, MAX_HEIGHT, foundTower.height, true);

		/* remove at each level */
		int level = (foundTower.height - 1);
		int retries = 0;
		while (level >= 0) {
			if (tryRemoveAtLevel(prevs[level], foundTower, marker, level,
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
		TowerFG prev = head;
		TowerFG curr = null;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			curr = prev.nexts.get(level);
			if (curr.marker) {
				curr = curr.nexts.get(level);
			}

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = curr.nexts.get(level);
				if (curr.marker) {
					curr = curr.nexts.get(level);
				}
			}

			/* if val is found at the current level */
			if (curr.val == val) {
				/* success depends on if the TowerFG is not deleted */
				return (curr.status.get() != 2);
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
		TowerFG curr = head.nexts.get(0);
		while (curr != tail) {
			if (curr.height != 0 && curr.status.get() == 1) {
				size++;
			}
			if (curr.status.get() == 2) {
				assert curr.nexts.get(0).marker;
				curr = curr.nexts.get(0);
			} else
				assert curr.status.get() == 1;
			curr = curr.nexts.get(0);
		}
		// this.print();
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.nexts.set(i, tail);
		}
		// head.resetLocks();
		// tail.resetLocks();
		assert head.height == MAX_HEIGHT;
		assert tail.height == MAX_HEIGHT;
	}

	private int getRandomHeight() {
		/* height from 1 to TOP */
		return Math
				.min(TOP, (skiplists.RandomLevelGenerator.randomLevel() + 1));
	}

	void print() {
		System.out.println("skiplist:");
		for (int i = MAX_HEIGHT - 1; i >= 0; i--) {
			TowerFG next = head;
			while (next != tail) {
				System.out.print(next.val + "-- ");
				next = next.nexts.get(i);
			}
			System.out.println();
		}
		System.out.println();
	}

	public class TowerFG {
		public final int val;

		public final AtomicReferenceArray<TowerFG> nexts;

		public final int height;
		public final boolean marker;

		/* 0 = being inserted, 1 = valid, 2 = being deleted, 3 = marker */
		public final AtomicInteger status;

		// final ReentrantLock[] nextLocks;

		public TowerFG(int val, int maxHeight, int height) {
			this(val, maxHeight, height, false);
		}

		public TowerFG(int val, int maxHeight, int height, boolean marker) {
			if (HELP) {
				status = new AtomicInteger(1);
			} else {
				status = new AtomicInteger(0);
			}
			this.marker = marker;
			if (marker) {
				status.set(3);
			}
			this.val = val;
			this.nexts = new AtomicReferenceArray<TowerFG>(height);
			this.height = height;
		}
	}

}
