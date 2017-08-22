package skiplists.lockbased.tower;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import skiplists.TowerBase;

public class LazyTower extends TowerBase {

	public volatile boolean marked = false;
	public volatile boolean fullyLinked = false;
	public int topLevel;
	public 
	
	int val;
	int height;

	public LazyTower(int val, int maxHeight, int height) {
		super(val, height);
		topLevel = height;
		this.val = val;
		this.height = height;
	}
	
	//public int getHeight() {
	//	return height;
	//}
	
//	public void set(int level, Object next) {
	//	
	//}
	
	//public volatile int status = 0;

	final Lock lock = new ReentrantLock();


	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}
	
	//public LazyTower getNext(int next) {
	//	return null;
	//}
}

