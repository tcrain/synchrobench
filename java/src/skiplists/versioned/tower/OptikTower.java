package skiplists.versioned.tower;

public class OptikTower extends LazyTower {

	private static final int VERSION_BIT_MASK = -4;
	private static final int MARK_MOD = 3;
	private static final int MARK_CHECK = 2;
	private static final int LOCK_MOD = 1;
	private static final int LOCK_CHECK = 1;

	public OptikTower(int val, int maxHeight, int height) {
		super(val, maxHeight, height);
		atomicIntegerFieldUpdater.set(this, 0);
		// this.height = height;
	}

	@Override
	public int getVersion() {
		return lock;
	}

	public static final boolean isMarked(final int version) {
		if ((version & MARK_MOD) == MARK_CHECK) {
			//assert false;
			return true;
		}
		return false;
	}

	public boolean isMarked() {
		return isMarked(lock);
	}

	public static final boolean isLocked(final int version) {
		if ((version & LOCK_MOD) == LOCK_CHECK) {
			return true;
		}
		return false;
	}

	public void mark() {
		// assert (!Node.isMarked(lock.get()));
		// assert (Node.isLocked(lock.get()));
		//assert false;
		atomicIntegerFieldUpdater.addAndGet(this, 1);
		// lock.addAndGet(1);
	}

	@Override
	public boolean tryLockAtVersion(int version) {
		version &= VERSION_BIT_MASK;
		return atomicIntegerFieldUpdater.compareAndSet(this, version, version + 1);
		// return lock.compareAndSet(version, version + 1);
	}

	@Override
	public boolean spinlock() {
		int version;
		do {
			version = lock;
			if (isMarked(version)) {
				return false;
			}
			version &= VERSION_BIT_MASK;
		} while (!atomicIntegerFieldUpdater.compareAndSet(this, version, version + 1));
		// version = getVersion();
		// if (Node.isMarked(version)) {
		// return false;
		// }
		// }
		return true;
	}

	@Override
	public void unlockAndIncrementVersion() {
		// assert (Node.isLocked(lock.get()));
		assert isLocked(lock);
		assert !isMarked(lock);
		int val = atomicIntegerFieldUpdater.addAndGet(this, 3);
		assert !isMarked(val);
	}

}
