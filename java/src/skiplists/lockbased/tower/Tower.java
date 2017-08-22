package skiplists.lockbased.tower;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import skiplists.TowerBase;

public class Tower extends TowerBase {

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	final Lock lock = new ReentrantLock();

	public Tower(int val, int maxHeight, int height) {
		super(val, height);
	}

	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}
}
