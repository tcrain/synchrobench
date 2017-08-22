package skiplists.lockfree.tower;

public class MarkerStatus extends TowerStatus {
	final TowerStatus next;

	public MarkerStatus(int val, int level, TowerStatus next) {
		super(val, 0);
		this.next = next;
	}

	@Override
	public TowerStatus getNext(int level) {
		return next;
	}
}
