package skiplists;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicReferenceArray;

import sun.misc.Unsafe;

public abstract class TowerBase {

	public static Unsafe getUnsafe() {
		try {
			//Field f = Unsafe.class.getDeclaredField("theUnsafe");
			//f.setAccessible(true);
			//return (Unsafe) f.get(null);
			Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
			unsafeConstructor.setAccessible(true);
			Unsafe unsafe = unsafeConstructor.newInstance();
			return unsafe;
		} catch (Exception e) {
			assert false;
		}
		return null;
	}

	protected static final Unsafe unsafe;
	private static final int base;
	private static final int shift;
	private static final long arrayFieldOffset;
	protected final Object[] array; // must have exact type Object[]

	static {
		int scale;
		try {
			unsafe = getUnsafe();
			arrayFieldOffset = unsafe.objectFieldOffset(AtomicReferenceArray.class.getDeclaredField("array"));
			base = unsafe.arrayBaseOffset(Object[].class);
			scale = unsafe.arrayIndexScale(Object[].class);
		} catch (Exception e) {
			throw new Error(e);
		}
		if ((scale & (scale - 1)) != 0)
			throw new Error("data type scale not a power of two");
		shift = 31 - Integer.numberOfLeadingZeros(scale);
	}

	protected long checkedByteOffset(int i) {
		if (i < 0 || i >= array.length)
			throw new IndexOutOfBoundsException("index " + i);

		return ((long) i << shift) + base;
	}

	private static long byteOffset(int i) {
		return ((long) i << shift) + base;
	}

	public final int val;

	public TowerBase(int val, int height) {
		this.val = val;
		if (height == 0) {
			this.array = null;
		} else {
			this.array = new Object[height];
		}
	}

	public int getHeight() {
		return array.length;
	}

	public final boolean compareAndSet(int i, Object expect, Object update) {
		return unsafe.compareAndSwapObject(array, checkedByteOffset(i), expect, update);
	}

	public Object getNext(int level) {
		return unsafe.getObjectVolatile(array, checkedByteOffset(level));
	}
	
	public Object getNextNormal(int level) {
		return unsafe.getObject(array, checkedByteOffset(level));
	}

	public void set(int i, Object newValue) {
		//unsafe.putObjectVolatile(array, byteOffset(i), newValue);
		unsafe.putObjectVolatile(array, checkedByteOffset(i), newValue);
	}
}
