package skiplists.versioned.tower;

public class LazyTower extends Tower2 {

	public volatile boolean marked = false;
	public volatile boolean fullyLinked = false;
	public int topLevel;

	public LazyTower(int val, int maxHeight, int height) {
		super(val, maxHeight, height);
		topLevel = height;
	}

}
