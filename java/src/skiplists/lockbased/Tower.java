package skiplists.lockbased;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Tower {
	public final int val;

	public final AtomicReferenceArray<Tower> nexts;

	public volatile int height = 0;

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	final Lock lock = new ReentrantLock();

	public Tower(int val, int maxHeight, int height) {
		this.val = val;
		this.nexts = new AtomicReferenceArray<Tower>(maxHeight);
		this.height = height;
	}

	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}
}
