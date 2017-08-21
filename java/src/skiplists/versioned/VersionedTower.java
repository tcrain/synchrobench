package skiplists.versioned;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import contention.abstractions.AbstractCompositionalIntSet;

// TODO: should inserts be linearized at start or finish? then either has to block concurrent inserts or deletes

public class VersionedTower extends AbstractCompositionalIntSet {

	private final Tower head;
	private final Tower tail;

	private static final int MAX_HEIGHT = 22; // TODO: use variable max height
												// selection
	private static final int TOP = MAX_HEIGHT - 1;

	private static final int OK = 0;
	private static final int ABORT = 1;
	
	//private volatile Integer currentHeight = 3;
	
	//static final AtomicReferenceFieldUpdater<VersionedTower, Integer> currentHeightUpdater = AtomicReferenceFieldUpdater
	//		.newUpdater(VersionedTower.class, Integer.class, "currentHeight");
	
	//private boolean updateHeight(int prevHeight, int newHeight) {
	//	return currentHeightUpdater.compareAndSet(this, prevHeight, newHeight);
	//}

	public VersionedTower() {

		tail = new Tower(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT);
		head = new Tower(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT);

		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.nexts.set(i, tail);
		}

		head.height = MAX_HEIGHT;
		tail.height = MAX_HEIGHT;
		head.status = 1;
		tail.status = 1;
	}

	/*
	 * TRAVERSE
	 * *****************************************************************
	 * *********************
	 */

	private Tower traverse(int from, int val, Tower[] prevs) {
		Tower prev = head;
		Tower curr = null;
		Tower found = null;

		/* traverse down the levels of the skiplist */
		for (int level = from; level >= 0; level--) {
			curr = prev.nexts.get(level);

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = curr.nexts.get(level);
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

	private int validateInsert(Tower prev, Tower towerToInsert, int level,
			int[] state) {
		int prevVer = prev.getVersion();

		/* if prev is being inserted or being deleted */
		if (prev.status != 1) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is already pointing to towerToInsert at the current level */
		if (prev.nexts.get(level).val == towerToInsert.val) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is no longer the appropriate prev to utilize */
		if (prev.nexts.get(level).val < towerToInsert.val) {
			state[0] = ABORT;
			return 0;
		}

		/* success, so return the version at this level */
		state[0] = OK;
		return prevVer;
	}

	private boolean tryInsertAtLevel(Tower prev, Tower towerToInsert, int level) {
		int validVer;
		int[] state = { OK };
		do {
			/* pre-locking validation */
			validVer = validateInsert(prev, towerToInsert, level, state);
			if (state[0] == ABORT) {
				return false;
			}

			/* try-lock prev */
		} while (!prev.tryLockAtVersion(validVer));

		/* set towerToInsert's next */
		towerToInsert.nexts.set(level, prev.nexts.get(level));

		/* update prev's next */
		prev.nexts.set(level, towerToInsert); /*
											 * linearization point when level =
											 * 0
											 */

		prev.unlockAndIncrementVersion();
		return true;
	}

	@Override
	public boolean addInt(int val) {
		Tower[] prevs = new Tower[MAX_HEIGHT];
		Tower towerToInsert = null;
		Tower foundTower = null;
		//int height = getRandomHeight();
		//int traverseHeight = Math.max(height, currentHeight);

		retryFromTraverse: while (true) {
			towerToInsert = null;
			foundTower = null;			
			/* traverse the skiplist */
			foundTower = traverse(TOP, val, prevs);

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
				towerToInsert = new Tower(val, MAX_HEIGHT, height);
				//while(height > currentHeight) {
				//	this.updateHeight(currentHeight, height);
				//}
				/* start tower as locked */
				towerToInsert.spinlock(); /* guaranteed to work on new node */
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
					}
					/* re-traverse to update list of prevs */
					traverse(TOP, val, prevs);
				}
			}

			break retryFromTraverse;
		}

		/* mark as valid */
		towerToInsert.status = 1;

		/* unlock tower */
		towerToInsert.unlockAndIncrementVersion();

		return true;
	}

	/*
	 * REMOVE
	 * *******************************************************************
	 * *********************
	 */

	private int validateRemove(Tower prev, Tower towerToRemove, int level,
			int[] state) {
		int prevVer = prev.getVersion();

		/* if prev is being inserted or being deleted */
		if (prev.status != 1) {
			state[0] = ABORT;
			return 0;
		}

		/* if prev is not pointing to the tower at the current level */
		if (prev.nexts.get(level).val != towerToRemove.val) {
			state[0] = ABORT;
			return 0;
		}

		/* success, so return the version at this level */
		state[0] = OK;
		return prevVer;

	}

	private boolean tryRemoveAtLevel(Tower prev, Tower towerToRemove, int level) {
		int validVer;
		int[] state = { OK };
		do {
			/* pre-locking validation */
			validVer = validateRemove(prev, towerToRemove, level, state);
			if (state[0] == ABORT) {
				return false;
			}

			/* try-lock prev */
		} while (!prev.tryLockAtVersion(validVer));

		/* update prev to skip over towerToRemove */
		prev.nexts.set(level, towerToRemove.nexts.get(level)); /*
																 * linearization
																 * point when
																 * level = 0
																 */

		prev.unlockAndIncrementVersion();
		return true;
	}

	@Override
	public boolean removeInt(int val) {
		Tower[] prevs = new Tower[MAX_HEIGHT];
		Tower foundTower = null;

		retryFromTraverse: while (true) {
			/* traverse the skiplist */
			foundTower = traverse(TOP, val, prevs);

			/* val is not found, so remove fails */
			if (foundTower == null) {
				return false;
			}

			/* pre-lock validation */
			int validVer = foundTower.getVersion();

			/* found tower is already being removed, or is not fully inserted */
			int status;
			status = foundTower.status;
			if (status == 2) {
				/* insert linearizes at the start thus blocks concurrent removes */
				/* remove linearizes at the end thus blocks concurrent removes */
				return false;
			} else if (status == 0) {
				continue retryFromTraverse;
			}

			/* try-lock tower */
			if (!foundTower.tryLockAtVersion(validVer)) {
				continue retryFromTraverse;
			}

			break retryFromTraverse;
		}

		/* remove at each level */
		int level = (foundTower.height - 1);
		while (level >= 0) {
			if (tryRemoveAtLevel(prevs[level], foundTower, level)) {
				/* remove has succeeded at this level */
				level--;

				/* re-traverse and try again with updated prevs */
			} else {
				/* re-traverse to update list of prevs */
				traverse(TOP, val, prevs);
			}
		}

		/* mark tower as being deleted */
		foundTower.status = 2;

		/* no need to unlock tower as it is fully unlinked */
		// foundTower.unlockAndIncrementVersion();

		return true;

	}

	/*
	 * CONTAINS
	 * *****************************************************************
	 * *********************
	 */

	@Override
	public boolean containsInt(int val) {
		Tower prev = head;
		Tower curr = null;

		/* traverse down the levels of the skiplist */
		for (int level = TOP; level >= 0; level--) {
			curr = prev.nexts.get(level);

			/* traverse at the current level */
			while (curr.val < val) {
				prev = curr;
				curr = curr.nexts.get(level);
			}

			/* if val is found at the current level */
			if (curr.val == val) {
				/* success depends on if the tower is not deleted */
				/* TODO this is wrong, if it is 0 should also return false */
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
		Tower curr = head.nexts.get(0);
		while (curr != tail) {
			if (curr.height != 0) {
				size++;
			}
			curr = curr.nexts.get(0);
		}
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < MAX_HEIGHT; i++) {
			head.nexts.set(i, tail);
		}
		head.resetLocks();
		tail.resetLocks();
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
		for (int i = TOP - 1; i >= 0; i--) {
			Tower next = head;
			while (next != tail) {
				System.out.print(next.val + "-- ");
				next = next.nexts.get(i);
			}
			System.out.println();
		}
		System.out.println();
	}

}
