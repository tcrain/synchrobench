package skiplists.versioned.tower;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import skiplists.TowerBase;

public class Tower2 extends TowerBase {

	// private final Tower2[] array;

	// public final int height;

	/* 0 = being inserted, 1 = valid, 2 = being deleted */
	public volatile int status = 0;

	protected volatile int lock;

	protected final static AtomicIntegerFieldUpdater<Tower2> atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater
			.newUpdater(Tower2.class, "lock");

	private static final int VERSION_BIT_MASK = -2;

	public Tower2(int val, int maxHeight, int height) {
		super(val, height);
		atomicIntegerFieldUpdater.set(this, 0);
		// this.height = height;
	}

	public int getVersion() {
		return (lock & VERSION_BIT_MASK);
	}

	public boolean tryLockAtVersion(int version) {
		return atomicIntegerFieldUpdater.compareAndSet(this, version, version + 1);
	}

	public boolean spinlock() {
		int version = getVersion();
		while (!atomicIntegerFieldUpdater.compareAndSet(this, version, version + 1)) {
			version = getVersion();
		}
		return true;
	}

	public void unlockAndIncrementVersion() {
		atomicIntegerFieldUpdater.set(this, lock + 1);
	}

	public void unlock() {
		atomicIntegerFieldUpdater.set(this, lock - 1);
	}

	public void resetLocks() {
		lock = 0;
	}
}
