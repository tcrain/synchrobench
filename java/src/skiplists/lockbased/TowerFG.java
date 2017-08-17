
package skiplists.lockbased;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;

public class TowerFG {
	public final int val;

	public final AtomicReferenceArray<TowerFG> nexts;

	public volatile int height = 0;

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	final ReentrantLock[] nextLocks;

	public TowerFG(int val, int maxHeight, int height) {
		this.val = val;
		this.nexts = new AtomicReferenceArray<TowerFG>(maxHeight);
		this.height = height;
		this.nextLocks = new ReentrantLock[height];
		for (int i = 0; i < height; i++) {
			this.nextLocks[i] = new ReentrantLock();
		}
	}
}

