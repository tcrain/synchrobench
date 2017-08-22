package skiplists.lockfree.tower;

public class MarkerUnsafe extends TowerUnsafe {
	final TowerUnsafe next;

	public MarkerUnsafe(int val, int level, TowerUnsafe next) {
		super(val, 0);
		this.next = next;
	}

	@Override
	public TowerUnsafe getNext(int level) {
		return next;
	}
}