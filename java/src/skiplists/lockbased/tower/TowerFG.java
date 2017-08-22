package skiplists.lockbased.tower;

import java.util.concurrent.locks.ReentrantLock;

import skiplists.TowerBase;

public class TowerFG extends TowerBase {

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	private final ReentrantLock[] nextLocks;

	public TowerFG(int val, int maxHeight, int height) {
		super(val, height);
		this.nextLocks = new ReentrantLock[height];
		for (int i = 0; i < height; i++) {
			this.nextLocks[i] = new ReentrantLock();
		}
	}

	public void lock(int level) {
		nextLocks[level].lock();
	}

	public void unlock(int level) {
		nextLocks[level].unlock();
	}

}
