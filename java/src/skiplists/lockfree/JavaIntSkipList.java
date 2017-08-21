package skiplists.lockfree;

import java.util.Collection;

import contention.abstractions.CompositionalIntSet;

//public class JavaIntSkipList extends JavaSkiplist implements CompositionalIntSet {
public class JavaIntSkipList extends java.util.concurrent.ConcurrentSkipListMap implements CompositionalIntSet {

	
	@Override
	public void fill(int range, long size) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addInt(int x) {
		if(super.put(x, x) == null) {
			return true;
		}
		return false;
	}

	@Override
	public boolean removeInt(int x) {
		if(super.remove(x) == null)
			return false;
		return true;
	}

	@Override
	public boolean containsInt(int x) {
		return super.containsKey(x);
	}

	@Override
	public Object getInt(int x) {
		return super.get(x);
	}

	@Override
	public boolean addAll(Collection<Integer> c) {
		for(Integer i : c) {
			super.put(i, i);
		}
		return true;
	}

	@Override
	public boolean removeAll(Collection<Integer> c) {
		for(Integer i : c) {
			this.remove(i);
		}
		return true;
	}

	@Override
	public Object putIfAbsent(int x, int y) {
		return super.putIfAbsent(x, y);
	}
	
}
