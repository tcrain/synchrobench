package skiplists.versioned;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class Tower {
    public final int val;

    public final AtomicReferenceArray<Tower> nexts;

    public volatile int height = 0;

    /* 0 = being inserted, 1 = valid, 2 = being deleted */
    public volatile int status = 0;

    private final AtomicInteger lock;

    private static final int VERSION_BIT_MASK = -2;

    public Tower(int val, int maxHeight, int height) {
        this.val = val;
        this.nexts = new AtomicReferenceArray<Tower>(maxHeight);
        this.lock = new AtomicInteger(0);
        this.height = height;
    }

    public int getVersion() {
        return (lock.get() & VERSION_BIT_MASK);
    }

    public boolean tryLockAtVersion(int version) {
        return lock.compareAndSet(version, version + 1);
    }

    public void spinlock() {
        int version = getVersion();
        while (!lock.compareAndSet(version, version + 1)) {
            version = getVersion();
        }
    }

    public void unlockAndIncrementVersion() {
        lock.incrementAndGet();
    }

    public void unlock() {
        lock.decrementAndGet();
    }

    public void resetLocks() {
        lock.set(0);
    }
}
