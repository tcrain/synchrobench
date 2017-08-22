package skiplists.versioned.tower;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class Tower2Old {
    public final int val;

    public final Tower2Old[] nexts;

    public volatile int height = 0;

    /* 0 = being inserted, 1 = valid, 2 = being deleted */
    public volatile int status = 0;

    private volatile int lock;

    private static final int VERSION_BIT_MASK = -2;

    public Tower2Old(int val, int maxHeight, int height) {
        this.val = val;
        this.nexts = new Tower2Old[height];
        this.lock = 0;
        this.height = height;
    }
    
    // UNSAFE mechanics

    private static final sun.misc.Unsafe UNSAFE;
    private static final long lockOffset;

    static {
        try {
            UNSAFE = getUnsafe();
            Class<?> k = Tower2Old.class;
            lockOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("lock"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }
    
    private static Unsafe getUnsafe() {
        try {

        	Field f = Unsafe.class.getDeclaredField("theUnsafe");
        	f.setAccessible(true);
        	Unsafe unsafe = (Unsafe) f.get(null);
        	return unsafe;

        } catch (Exception e) {
        	System.err.println("error getting unsafe");
        	return null;
        }
    }
    

    public int getVersion() {
        return (lock & VERSION_BIT_MASK);
    }

    public boolean tryLockAtVersion(int version) {
    	return UNSAFE.compareAndSwapInt(this, lockOffset, version, version +1);
    }

    public void spinlock() {
        int version = getVersion();
        while (!UNSAFE.compareAndSwapInt(this, lockOffset, version, version + 1)) {
            version = getVersion();
        }
    }

    public void unlockAndIncrementVersion() {
        lock = lock + 1;
    }

    public void unlock() {
        lock = lock - 1;
    }

    public void resetLocks() {
        lock = 0;
    }
}
