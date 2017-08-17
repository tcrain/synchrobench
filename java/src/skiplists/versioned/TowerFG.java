package skiplists.versioned;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class TowerFG {
	public final int val;

	public final AtomicReferenceArray<TowerFG> nexts;

	public volatile int height = 0;

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	final AtomicInteger[] nextLocks;
	private static final int VERSION_BIT_MASK = -2;

	public TowerFG(int val, int maxHeight, int height) {
		this.val = val;
		this.nexts = new AtomicReferenceArray<TowerFG>(maxHeight);
		this.height = height;
		this.nextLocks = new AtomicInteger[height];
		for (int i = 0; i < height; i++) {
			this.nextLocks[i] = new AtomicInteger();
		}
	}

	public int getVersion(int level) {
		return (nextLocks[level].get() & VERSION_BIT_MASK);
	}

	public boolean tryLockAtVersion(int level, int version) {
		return nextLocks[level].compareAndSet(version, version + 1);
	}

	public void spinlock(int level) {
		int version = getVersion(level);
		while (!nextLocks[level].compareAndSet(version, version + 1)) {
			version = getVersion(level);
		}
	}

	public void unlockAndIncrementVersion(int level) {
		nextLocks[level].incrementAndGet();
	}

	public void unlock(int level) {
		nextLocks[level].decrementAndGet();
	}

	public void resetLocks() {
		for (int i = 0; i < height; i++) {
			nextLocks[i].set(0);
		}
	}

}
