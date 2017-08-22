package skiplists.versioned.tower;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class Tower2Old2 {
    public final int val;

    public final Tower2Old2[] nexts;

    public volatile int height = 0;

    /* 0 = being inserted, 1 = valid, 2 = being deleted */
    public volatile int status = 0;

    private volatile int lock;
    
    private final static AtomicIntegerFieldUpdater<Tower2Old2>
    	atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater.newUpdater(
            Tower2Old2.class, "lock" );

    private static final int VERSION_BIT_MASK = -2;

    public Tower2Old2(int val, int maxHeight, int height) {
        this.val = val;
        this.nexts = new Tower2Old2[height];
        atomicIntegerFieldUpdater.set(this, 0);
        this.height = height;
    }
    

    public int getVersion() {
        return (lock & VERSION_BIT_MASK);
    }

    public boolean tryLockAtVersion(int version) {
    	return atomicIntegerFieldUpdater.compareAndSet(this, version, version +1);
    }

    public void spinlock() {
        int version = getVersion();
        while (!atomicIntegerFieldUpdater.compareAndSet(this, version, version + 1)) {
            version = getVersion();
        }
    }

    public void unlockAndIncrementVersion() {
    	atomicIntegerFieldUpdater.set(this, atomicIntegerFieldUpdater.get(this) + 1);
    }

    public void unlock() {
    	atomicIntegerFieldUpdater.set(this, atomicIntegerFieldUpdater.get(this) - 1);
    }

    public void resetLocks() {
    	atomicIntegerFieldUpdater.set(this, 0);
    }
}
