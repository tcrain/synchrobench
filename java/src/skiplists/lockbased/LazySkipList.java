/*
 * Algorithm:
 *   Fine-grained locking skip list.
 *   "A Simple Optimistic Skiplist Algorithm" 
 *   M. Herlihy, Y. Lev, V. Luchangco, N. Shavit 
 *   p.124-138, SIROCCO 2007
 *
 * Code:
 *  Based on example code from:
 *  "The Art of Multiprocessor Programming"
 *  M. Herlihy, N. SHavit
 *  chapter 14.3, 2008
 *
 */

package skiplists.lockbased;

import java.util.Collection;
import java.util.Random;

import contention.abstractions.CompositionalIntSet;
import skiplists.lockbased.tower.LazyTower;

public final class LazySkipList implements CompositionalIntSet {

	/** The maximum number of levels */
	final private int maxLevel;
	/** The first element of the list */
	final private LazyTower head;
	/** The last element of the list */
	final private LazyTower tail;

	/**
	 * The thread-private PRNG, used for fil(), not for height/level determination.
	 */
	final private static ThreadLocal<Random> s_random = new ThreadLocal<Random>() {
		@Override
		protected synchronized Random initialValue() {
			return new Random();
		}
	};

	private int randomLevel() {
		return Math.min((maxLevel), (skiplists.RandomLevelGenerator.randomLevel() + 1));
	}

	public LazySkipList() {
		this(22);
	}

	public LazySkipList(final int maxLevel) {
		this.head = new LazyTower(Integer.MIN_VALUE, maxLevel, maxLevel);
		this.tail = new LazyTower(Integer.MAX_VALUE, maxLevel, maxLevel);
		this.maxLevel = maxLevel;
		for (int i = 0; i < maxLevel; i++) {
			head.set(i, tail);
		}
	}

	@Override
	public boolean containsInt(final int value) {
		int key = value;
		int levelFound = -1;
		LazyTower pred = head;
		LazyTower curr = pred;

		for (int level = maxLevel - 1; level >= 0; level--) {
			curr = (LazyTower) pred.getNext(level);

			while (key > curr.val) {
				pred = curr;
				curr = (LazyTower) pred.getNext(level);
			}

			if (levelFound == -1 && key == curr.val) {
				levelFound = level;
			}
		}
		return (levelFound != -1 && curr.fullyLinked && !curr.marked);
	}

	/*
	 * The preds[] and succs[] arrays are filled from the maximum level to 0 with
	 * the predecessor and successor references for the given key.
	 */
	private int find(final int value, LazyTower[] preds, LazyTower[] succs) {
		int key = value;
		int levelFound = -1;
		LazyTower pred = head;

		for (int level = maxLevel - 1; level >= 0; level--) {
			LazyTower curr = (LazyTower) pred.getNext(level);

			while (key > curr.val) {
				pred = curr;
				curr = (LazyTower) pred.getNext(level);
			}

			if (levelFound == -1 && key == curr.val) {
				levelFound = level;
			}
			preds[level] = pred;
			succs[level] = curr;
		}
		return levelFound;
	}

	@Override
	public boolean addInt(final int value) {
		int topLevel = randomLevel();
		LazyTower[] preds = new LazyTower[maxLevel];
		LazyTower[] succs = new LazyTower[maxLevel];

		while (true) {
			/* Call find() to initialize preds and succs. */
			int levelFound = find(value, preds, succs);

			/* If an node is found that is unmarked then return false. */
			if (levelFound != -1) {
				LazyTower nodeFound = succs[levelFound];
				if (!nodeFound.marked) {
					/* Needs to wait for nodes to become fully linked. */
					while (!nodeFound.fullyLinked) {
					}
					return false;
				}
				/* If marked another thread is deleting it, so we retry. */
				continue;
			}

			int highestLocked = -1;

			try {
				LazyTower pred, succ;
				boolean valid = true;

				/* Acquire locks. */
				for (int level = 0; valid && (level < topLevel); level++) {
					pred = preds[level];
					succ = succs[level];
					pred.lock();
					highestLocked = level;
					valid = !pred.marked && !succ.marked && pred.getNext(level) == succ;
				}

				/*
				 * Must have encountered effects of a conflicting method, so it releases (in the
				 * finally block) the locks it acquired and retries
				 */
				if (!valid) {
					continue;
				}

				LazyTower newNode = new LazyTower(value, maxLevel, topLevel);
				for (int level = 0; level < topLevel; level++) {
					newNode.set(level, succs[level]);
				}
				for (int level = 0; level < topLevel; level++) {
					preds[level].set(level, newNode);
				}
				newNode.fullyLinked = true; // successful and linearization point
				return true;

			} finally {
				for (int level = 0; level <= highestLocked; level++) {
					preds[level].unlock();
				}
			}

		}

	}

	@Override
	public boolean removeInt(final int value) {
		LazyTower victim = null;
		boolean isMarked = false;
		int topLevel = -1;
		LazyTower[] preds = new LazyTower[maxLevel];
		LazyTower[] succs = new LazyTower[maxLevel];

		while (true) {
			/* Call find() to initialize preds and succs. */
			int levelFound = find(value, preds, succs);
			if (levelFound != -1) {
				victim = succs[levelFound];
			}

			/* Ready to delete if unmarked, fully linked, and at its top level. */
			if (isMarked | (levelFound != -1
					&& (victim.fullyLinked && victim.topLevel - 1 == levelFound && !victim.marked))) {

				/* Acquire locks in order to logically delete. */
				if (!isMarked) {
					topLevel = victim.topLevel;
					victim.lock();
					if (victim.marked) {
						victim.unlock();
						return false;
					}
					victim.marked = true; // logical deletion
					isMarked = true;
				}

				int highestLocked = -1;

				try {
					LazyTower pred;
					boolean valid = true;

					/* Acquire locks. */
					for (int level = 0; valid && (level < topLevel); level++) {
						pred = preds[level];
						pred.lock();
						highestLocked = level;
						valid = !pred.marked && pred.getNext(level) == victim;
					}

					/* Pred has changed and is no longer suitable, thus unlock and retries. */
					if (!valid) {
						continue;
					}

					/* Unlink. */
					for (int level = topLevel - 1; level >= 0; level--) {
						preds[level].set(level, victim.getNext(level));
					}
					victim.unlock();
					return true;

				} finally {
					for (int i = 0; i <= highestLocked; i++) {
						preds[i].unlock();
					}
				}
			} else {
				return false;
			}
		}
	}

	@Override
	public void fill(final int range, final long size) {
		while (this.size() < size) {
			this.addInt(s_random.get().nextInt(range));
		}
	}

	@Override
	public Object getInt(int value) {
		// TODO
		return null;
	}

	@Override
	public boolean addAll(Collection<Integer> c) {
		// TODO
		return false;
	}

	@Override
	public boolean removeAll(Collection<Integer> c) {
		// TODO
		return false;
	}

	@Override
	public int size() {
		int size = 0;
		LazyTower node = (LazyTower) ((LazyTower) head.getNext(0)).getNext(0);

		while (node != null) {
			node = (LazyTower) node.getNext(0);
			size++;
		}
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < this.maxLevel; i++) {
			this.head.set(i, this.tail);
		}
		return;
	}

	@Override
	public String toString() {
		// TODO
		return null;

	}

	@Override
	public Object putIfAbsent(int x, int y) {
		// TODO
		return null;
	}

}
