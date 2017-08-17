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

package skiplists.versioned;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import contention.abstractions.CompositionalIntSet;

public final class OptikLazyVersionedSkipList implements CompositionalIntSet {

	/** The maximum number of levels */
	final private int maxLevel;
	/** The first element of the list */
	final private Node head;
	/** The last element of the list */
	final private Node tail;

	/**
	 * The thread-private PRNG, used for fil(), not for height/level
	 * determination.
	 */
	final private static ThreadLocal<Random> s_random = new ThreadLocal<Random>() {
		@Override
		protected synchronized Random initialValue() {
			return new Random();
		}
	};

	private int randomLevel() {
		return Math.min((maxLevel - 1),
				(skiplists.RandomLevelGenerator.randomLevel()));
	}

	public OptikLazyVersionedSkipList() {
		this(22);
	}

	public OptikLazyVersionedSkipList(final int maxLevel) {
		this.head = new Node(Integer.MIN_VALUE, maxLevel);
		this.tail = new Node(Integer.MAX_VALUE, maxLevel);
		this.maxLevel = maxLevel;
		for (int i = 0; i <= maxLevel; i++) {
			head.next[i] = tail;
		}
	}

	@Override
	public boolean containsInt(final int value) {
		int key = value;
		int levelFound = -1;
		Node pred = head;
		Node curr = pred;

		for (int level = maxLevel; level >= 0; level--) {
			curr = pred.next[level];

			while (key > curr.key) {
				pred = curr;
				curr = pred.next[level];
			}

			if (levelFound == -1 && key == curr.key) {
				levelFound = level;
			}
		}
		return (levelFound != -1 && curr.fullyLinked && !curr.isMarked());
	}

	/*
	 * The preds[] and succs[] arrays are filled from the maximum level to 0
	 * with the predecessor and successor references for the given key.
	 */
	private int find(final int value, Node[] preds, Node[] succs, int[] versions) {
		int key = value;
		int levelFound = -1;
		Node pred = head;

		for (int level = maxLevel; level >= 0; level--) {
			int version = pred.getVersion();
			Node curr = pred.next[level];

			while (key > curr.key) {
				pred = curr;
				version = pred.getVersion();
				curr = pred.next[level];
			}
			
			if (levelFound == -1 && key == curr.key) {
				levelFound = level;
			}
			preds[level] = pred;
			succs[level] = curr;
			versions[level] = version;
		}
		return levelFound;
	}

	@Override
	public boolean addInt(final int value) {
		int topLevel = randomLevel();
		Node[] preds = (Node[]) new Node[maxLevel + 1];
		Node[] succs = (Node[]) new Node[maxLevel + 1];
		int[] versions = new int[maxLevel + 1];

		while (true) {
			/* Call find() to initialize preds and succs. */
			int levelFound = find(value, preds, succs, versions);

			/* If an node is found that is unmarked then return false. */
			if (levelFound != -1) {
				Node nodeFound = succs[levelFound];
				if (!nodeFound.isMarked()) {
					/* Needs to wait for nodes to become fully linked. */
					while (!nodeFound.fullyLinked) {
					}
					return false;
				}
				/* If marked another thread is deleting it, so we retry. */
				continue;
			}

			int highestLocked = -1;

			boolean valid = true;
			try {

				/* Acquire locks. */
				Node prev = null;
				for (int level = 0; valid && (level <= topLevel); level++) {
					// succ = succs[level];
					// pred.lock.lock();
					// valid = !pred.marked && !succ.marked &&
					// pred.next[level]==succ;
					Node pred = preds[level];
					if (!Node.isMarked(versions[level])
							&& !succs[level].isMarked()) {
						if (pred != prev) {
							prev = pred;
							valid = pred.tryLockAtVersion(versions[level]);
						}
						if (valid) {
							highestLocked = level;
						}
					} else {
						valid = false;
					}

				}

				/*
				 * Must have encountered effects of a conflicting method, so it
				 * releases (in the finally block) the locks it acquired and
				 * retries
				 */
				if (!valid) {
					continue;
				}

				Node newNode = new Node(value, topLevel);
				for (int level = 0; level <= topLevel; level++) {
					newNode.next[level] = succs[level];
				}
				for (int level = 0; level <= topLevel; level++) {
					preds[level].next[level] = newNode;
				}
				newNode.fullyLinked = true; // successful and linearization
											// point
				return true;

			} finally {
				Node prev = null;
				for (int level = 0; level <= highestLocked; level++) {
					Node pred = preds[level];
					if (pred != prev) {
						prev = pred;
						if (!valid)
							pred.unlock();
						else
							pred.unlockAndIncrementVersion();
					}
				}
			}

		}

	}

	@Override
	public boolean removeInt(final int value) {
		Node victim = null;
		boolean isMarked = false;
		int topLevel = -1;
		Node[] preds = (Node[]) new Node[maxLevel + 1];
		Node[] succs = (Node[]) new Node[maxLevel + 1];
		int[] versions = new int[maxLevel + 1];

		while (true) {
			/* Call find() to initialize preds and succs. */
			int levelFound = find(value, preds, succs, versions);
			if (levelFound != -1) {
				victim = succs[levelFound];
			}

			/* Ready to delete if unmarked, fully linked, and at its top level. */
			if (isMarked
					| (levelFound != -1 && (victim.fullyLinked
							&& victim.topLevel == levelFound && !victim
								.isMarked()))) {

				/* Acquire locks in order to logically delete. */
				if (!isMarked) {
					topLevel = victim.topLevel;
					if (!victim.spinlock()) {
						return false;
					}
					// if (victim.isMarked()) {
					// victim.unlock();
					// return false;
					// }
					// victim.marked = true; // logical deletion
					victim.mark();
					//assert (victim.isMarked());
					isMarked = true;
				}

				int highestLocked = -1;
				boolean valid = true;

				try {
					/* Acquire locks. */
					Node prev = null;
					for (int level = 0; valid && (level <= topLevel); level++) {
						Node pred = preds[level];
						// valid = !pred.marked && pred.next[level] == victim;
						if (!Node.isMarked(versions[level])) {
							if (pred != prev) {
								prev = pred;
								valid = pred.tryLockAtVersion(versions[level]);
								if (valid) {
									highestLocked = level;
								}
							}
						} else {
							valid = false;
						}
					}

					/*
					 * Pred has changed and is no longer suitable, thus unlock
					 * and retries.
					 */
					if (!valid) {
						continue;
					}

					/* Unlink. */
					for (int level = topLevel; level >= 0; level--) {
						preds[level].next[level] = victim.next[level];
					}
					// victim.unlockAndIncrementVersion();
					return true;

				} finally {
					Node prev = null;
					for (int i = 0; i <= highestLocked; i++) {
						Node pred = preds[i];
						if (pred != prev) {
							prev = pred;
							if (!valid)
								pred.unlock();
							else
								pred.unlockAndIncrementVersion();
						}
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
		Node node = head.next[0].next[0];

		while (node != null) {
			node = node.next[0];
			size++;
		}
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i <= this.maxLevel; i++) {
			this.head.next[i] = this.tail;
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

	private static final class Node {
		private static final int VERSION_BIT_MASK = -4;
		private static final int MARK_MOD = 3;
		private static final int MARK_CHECK = 2;
		private static final int LOCK_MOD = 1;
		private static final int LOCK_CHECK = 1;
		// Lock is mod 4
		// 0 == unlocked, 1 = locked, 2 = marked, (3 is unused)

		private final AtomicInteger lock = new AtomicInteger(0);
		final int key;
		final Node[] next;
		volatile boolean fullyLinked = false;
		private int topLevel;

		public Node(final int value, int height) {
			key = value;
			next = new Node[height + 1];
			topLevel = height;
		}

		public static final boolean isMarked(final int version) {
			if ((version & MARK_MOD) == MARK_CHECK) {
				return true;
			}
			return false;
		}

		public boolean isMarked() {
			return Node.isMarked(lock.get());
		}
		
		public static final boolean isLocked(final int version) {
			if ((version & LOCK_MOD) == LOCK_CHECK) {
				return true;
			}
			return false;
		}

		public int getVersion() {
			// return (lock.get() & VERSION_BIT_MASK);
			return lock.get();
		}

		public void mark() {
			//assert (!Node.isMarked(lock.get()));
			//assert (Node.isLocked(lock.get()));
			lock.addAndGet(1);
		}

		public boolean tryLockAtVersion(int version) {
			version &= VERSION_BIT_MASK;
			return lock.compareAndSet(version, version + 1);
		}

		public boolean spinlock() {
			int version;
			do {
				version = lock.get();
				if (Node.isMarked(version)) {
					return false;
				}
				version &= VERSION_BIT_MASK;
			} while (!lock.compareAndSet(version, version + 1));
			// version = getVersion();
			// if (Node.isMarked(version)) {
			// return false;
			// }
			// }
			return true;
		}

		public void unlockAndIncrementVersion() {
			//assert (Node.isLocked(lock.get()));
			lock.addAndGet(3);
		}

		public void unlock() {
			//assert (Node.isLocked(lock.get()));
			lock.decrementAndGet();
		}

		public void resetLocks() {
			lock.set(0);
		}
	}

}
