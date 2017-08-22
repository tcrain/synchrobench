package skiplists.versioned;

import skiplists.versioned.tower.TowerFG;
import contention.abstractions.AbstractCompositionalIntSet;

public class PughVersioned extends AbstractCompositionalIntSet {

	private final TowerFG head;
	private final TowerFG tail;

	private static final int MAX_HEIGHT = 22; // TODO: use variable max height
												// selection
	private static final int TOP = MAX_HEIGHT - 1;

	private static final int OK = 0;
	private static final int ABORT = 1;

	public PughVersioned() {

		tail = new TowerFG(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT);
		head = new TowerFG(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT);

		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.set(i, tail);
		}

		//head.height = MAX_HEIGHT;
		//tail.height = MAX_HEIGHT;
		head.status = 1;
		tail.status = 1;
	}

	/*
	 * TRAVERSE
	 * *****************************************************************
	 * *********************
	 */

	private TowerFG traverse(int val, TowerFG[] prevs, int[] foundarr) {
		TowerFG prev = head;
		TowerFG curr = null;
		TowerFG found = null;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			curr = (TowerFG) prev.getNext(level);

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = (TowerFG) curr.getNext(level);
			}

			/* record prevs at each level */
			prevs[level] = prev;

			/* record if val is ever found */
			if (curr.val == val) {
				found = curr;
				if (foundarr[0] == -1) {
					foundarr[0] = level + 1;
				}
			}
		}

		/* val not found at any level */
		return found;
	}

	private TowerFG traverse(int val, TowerFG[] prevs) {
		TowerFG prev = head;
		TowerFG curr = null;
		TowerFG found = null;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			curr = (TowerFG) prev.getNext(level);

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = (TowerFG) curr.getNext(level);
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

	private int validateInsert(TowerFG prev, TowerFG towerToInsert, int level,
			int[] state) {
		int prevVer = prev.getVersion(level);

		/* if prev is being inserted or being deleted */
		if (prev.status != 1) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is already pointing to towerToInsert at the current level */
		if (((TowerFG)prev.getNext(level)).val == towerToInsert.val) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is no longer the appropriate prev to utilize */
		if (((TowerFG)prev.getNext(level)).val < towerToInsert.val) {
			state[0] = ABORT;
			return 0;
		}

		/* success, so return the version at this level */
		state[0] = OK;
		return prevVer;
	}

	private boolean tryInsertAtLevel(TowerFG prev, TowerFG towerToInsert,
			int level) {

		int validVer;
		int[] state = { OK };
		do {
			/* pre-locking validation */
			validVer = validateInsert(prev, towerToInsert, level, state);
			if (state[0] == ABORT) {
				return false;
			}

			/* try-lock prev */
		} while (!prev.tryLockAtVersion(level, validVer));

		/* set towerToInsert's next */
		towerToInsert.set(level, prev.getNext(level));

		/* update prev's next */
		prev.set(level, towerToInsert); /*
											 * linearization point when level =
											 * 0
											 */
		prev.unlockAndIncrementVersion(level);
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
				if (foundTower.status == 2) {
					/* can not return false as that equals successful contains */
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
				towerToInsert = new TowerFG(val, MAX_HEIGHT, height);

				/* start tower as locked */
				towerToInsert.spinlock(0); /*
											 * guaranteed to work on new node
											 */
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
					}
					/* re-traverse to update list of prevs */
					traverse(val, prevs);
				}
			}

			break retryFromTraverse;
		}

		/* mark as valid */
		towerToInsert.status = 1;
		int arr[] = new int[1];
		arr[0] = -1;
		foundTower = traverse(val, prevs, arr);
		assert (foundTower == towerToInsert);
		assert (arr[0] == foundTower.getHeight());

		/* unlock tower */
		towerToInsert.unlock(0);

		return true;
	}

	/*
	 * REMOVE
	 * *******************************************************************
	 * *********************
	 */

	private int validateRemove(TowerFG prev, TowerFG towerToRemove, int level,
			int[] state) {
		int prevVer = prev.getVersion(level);

		/* if prev is being inserted or being deleted */
		if (prev.status != 1) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is not pointing to the tower at the current level */
		if (prev.getNext(level) != towerToRemove) {
			state[0] = ABORT;
			return 0;
		}

		/* success, so return the version at this level */
		state[0] = OK;
		return prevVer;

	}

	private boolean tryRemoveAtLevel(TowerFG prev, TowerFG towerToRemove,
			int level) {

		int validVer;
		int[] state = { OK };
		do {
			/* pre-locking validation */
			validVer = validateRemove(prev, towerToRemove, level, state);
			if (state[0] == ABORT) {
				return false;
			}

			/* try-lock prev */
		} while (!prev.tryLockAtVersion(level, validVer));
		if (level != 0) {
			towerToRemove.spinlock(level);
		}
		/* update prev to skip over towerToRemove */
		prev.set(level, towerToRemove.getNext(level)); /*
																 * linearization
																 * point when
																 * level = 0
																 */

		prev.unlockAndIncrementVersion(level);
		towerToRemove.unlockAndIncrementVersion(level);
		return true;
	}

	@Override
	public boolean removeInt(int val) {
		TowerFG[] prevs = new TowerFG[MAX_HEIGHT];
		TowerFG foundTower = null;

		while (true) {
			/* traverse the skiplist */
			foundTower = traverse(val, prevs);

			/* val is not found, so remove fails */
			if (foundTower == null) {
				return false;
			}
			// TowerFG prev = prevs[0];

			/* pre-lock validation */
			int validVer = foundTower.getVersion(0);
			// int preValidVer = prev.getVersion(0);
			/*
			 * found tower is already being removed, or is not fully inserted
			 */
			int status;
			status = foundTower.status;
			if (status == 2) {
				/* insert linearizes at the start thus blocks concurrent removes */
				/* remove linearizes at the end thus blocks concurrent removes */
				return false;
			} else if (status == 0) {
				continue;
			}
			// if (prev.nexts.get(0) != foundTower || prev.status != 1) {
			// continue;
			// }
			// prev.tryLockAtVersion(0, preValidVer);
			if (!foundTower.tryLockAtVersion(0, validVer)) {
				continue;
			}

			// int arr[] = new int[1];
			// arr[0] = -1;
			// assert (foundTower == traverse(val, prevs, arr));
			// assert (arr[0] == foundTower.height);
			/* mark tower as being deleted */
			foundTower.status = 2;

			/* remove at each level */
			int level = (foundTower.getHeight() - 1);
			while (level >= 0) {
				if (tryRemoveAtLevel(prevs[level], foundTower, level)) {
					/* remove has succeeded at this level */
					level--;

					/* re-traverse and try again with updated prevs */
				} else {
					/* re-traverse to update list of prevs */
					traverse(val, prevs);
				}
			}

			/* no need to unlock tower as it is fully unlinked */
			// foundTower.unlockAndIncrementVersion();
			foundTower.unlockAndIncrementVersion(0);
			return true;
		}
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
			curr = (TowerFG) prev.getNext(level);

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = (TowerFG) curr.getNext(level);
			}

			/* if val is found at the current level */
			if (curr.val == val) {
				/* success depends on if the TowerFG is not deleted */
				return (curr.status != 2);
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
		TowerFG curr = (TowerFG) head.getNext(0);
		while (curr != tail) {
			if (curr.getHeight() != 0) {
				size++;
			}
			curr = (TowerFG) curr.getNext(0);
		}
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
			TowerFG next = head;
			while (next != tail) {
				System.out.print(next.val + "-- ");
				next = (TowerFG) next.getNext(i);
			}
			System.out.println();
		}
		System.out.println();
	}

}
