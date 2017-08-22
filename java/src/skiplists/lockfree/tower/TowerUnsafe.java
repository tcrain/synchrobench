package skiplists.lockfree.tower;

import skiplists.TowerBase;

public class TowerUnsafe extends TowerBase {


	public final boolean marker;

	/* 0 = being inserted, 1 = valid, 2 = being deleted, 3 = marker */
	// public final AtomicInteger status;

	// final ReentrantLock[] nextLocks;

	public TowerUnsafe(int val, int height) {
		super(val, 0);
		// status = null;
		this.marker = true;
	}

	public TowerUnsafe(int val, int maxHeight, int height) {
		super(val, height);
		
		// if (HELP) {
		// status = new AtomicInteger(1);
		// } else {
		// status = new AtomicInteger(0);
		// }
		this.marker = false;
	}


}
