package skiplists.lockfree.tower;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import skiplists.TowerBase;

public class TowerStatus extends TowerBase {

	public final boolean marker;

	volatile int status = 0;

	private final static AtomicIntegerFieldUpdater<TowerStatus> atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater
			.newUpdater(TowerStatus.class, "status");

	/* 0 = being inserted, 1 = valid, 2 = being deleted, 3 = marker */
	// public final AtomicInteger status;

	// final ReentrantLock[] nextLocks;

	public TowerStatus(int val, int height) {
		super(val, 0);
		// status = null;
		this.marker = true;
	}

	public TowerStatus(int val, int maxHeight, int height, boolean help) {
		super(val, height);
		if (help) {
			status = 1;
		} else {
			status = 0;
		}
		this.marker = false;
	}

	public boolean compareAndSetStatus(int expect, int update) {
		return atomicIntegerFieldUpdater.compareAndSet(this, expect, update);
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getStatus() {
		return status;
	}

}
