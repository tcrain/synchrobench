package skiplists.versioned;

import contention.abstractions.AbstractCompositionalIntSet;

public class VersionedTower2 extends AbstractCompositionalIntSet {

    private final Tower2 head;
    private final Tower2 tail;

    private static final int MAX_HEIGHT = 22; // TODO: use variable max height selection
    private static final int TOP = MAX_HEIGHT - 1;

    private static final int OK = 0;
    private static final int ABORT = 1;
    
	final private ThreadLocal<Tower2[]> thdLocalPrevArray = new ThreadLocal<Tower2[]>() {
		@Override
		protected synchronized Tower2[] initialValue() {
			return (Tower2[]) new Tower2[MAX_HEIGHT];
		}
	};

    public VersionedTower2() {

        tail = new Tower2(Integer.MAX_VALUE, MAX_HEIGHT, MAX_HEIGHT);
        head = new Tower2(Integer.MIN_VALUE, MAX_HEIGHT, MAX_HEIGHT);

        for (int i = 0; i < MAX_HEIGHT; i++) {
            head.nexts[i] = tail;
        }

        //head.height = MAX_HEIGHT;
        //tail.height = MAX_HEIGHT;
        head.status = 1;
        tail.status = 1;
    }

/* TRAVERSE ***************************************************************************************/

    private Tower2 traverse(int val, Tower2[] prevs) {
        Tower2 prev = head;
        Tower2 curr = null;
        Tower2 found = null;

        /* traverse down the levels of the skiplist */
        for (int level = TOP; level >= 0; level--) {
        	prev.getVersion();
            curr = prev.nexts[level];

            /* traverse at the current level */
            while (curr.val < val) {
                prev = curr;
                curr = curr.nexts[level];
                curr.getVersion();
            }

            /* record prevs at each level */
            prevs[level] = prev;

            /* record if val is ever found */
            if (curr.val == val) {
                found = curr;
            }
        }

        /* val not found at any level */
        return found;
    }

/* INSERT *****************************************************************************************/

    private int validateInsert(Tower2 prev, Tower2 towerToInsert, int level, int[] state) {
        int prevVer = prev.getVersion();
        Tower2[] nexts = prev.nexts;
        
        /* if prev is being inserted or being deleted */
        if(prev.status != 1) {
            state[0] = ABORT;
            return 0;
        }

        /* if prev is already pointing to towerToInsert at the current level */
        if(nexts[level].val == towerToInsert.val) {
            state[0] = ABORT;
            return 0;
        }

        /* if prev is no longer the appropriate prev to utilize */
        if(nexts[level].val < towerToInsert.val) {
            state[0] = ABORT;
            return 0;
        }

        /* success, so return the version at this level */
        state[0] = OK;
        return prevVer;
    }

    private boolean tryInsertAtLevel(Tower2 prev, Tower2 towerToInsert, int level) {
        int validVer;
        int[] state = { OK };
        do {
            /* pre-locking validation */
            validVer = validateInsert(prev, towerToInsert, level, state);
            if (state[0] == ABORT) {
                return false;
            }

        /* try-lock prev */
        } while (!prev.tryLockAtVersion(validVer));

        Tower2[] prevNexts = prev.nexts;
        /* set towerToInsert's next */
        towerToInsert.nexts[level] = prevNexts[level];

        /* update prev's next */
        prevNexts[level] = towerToInsert; /* linearization point when level = 0 */

        prev.unlockAndIncrementVersion();
        return true;
    }

    @Override
    public boolean addInt(int val) {
        Tower2[] prevs = thdLocalPrevArray.get();
        Tower2 towerToInsert = null;
        Tower2 foundTower = null;

        retryFromTraverse: while(true) {
        	towerToInsert = null;
            foundTower = null;
            /* traverse the skiplist */
            foundTower = traverse(val, prevs);

            /* the value already exists in the skiplist */
            if (foundTower != null) {
                /* wait-free traversed onto node just before being fully unlinked / deleted */
                if (foundTower.status == 2) {
                    /* can not return false as that equals successful contains */
                    continue retryFromTraverse;
                }
                /* insert is linearized at the start thus allows concurrent inserts */
                /* remove is linearized at the end thus allows concurrent inserted */
                return false;
            }

            /* create tower to insert */
            if (towerToInsert == null) {
                int height = getRandomHeight();
                towerToInsert = new Tower2(val, MAX_HEIGHT, height);

                /* start tower as locked */
                towerToInsert.spinlock(); /* guaranteed to work on new node */
            }

            /* insert at each level */
            int level = 0;
            while (level < towerToInsert.nexts.length) {
                if (tryInsertAtLevel(prevs[level], towerToInsert, level)) {
                    /* insert has succeeded at this level */
                    level++;

                /* re-traverse and try again with updated prevs */
                } else {
                    /* race: aborting at level zero might mean another thread inserted first */
                    if (level == 0){
                        continue retryFromTraverse;
                    }
                    /* re-traverse to update list of prevs */
                    traverse(val, prevs);
                }
            }

            break retryFromTraverse;
        }

        /* mark as valid */
        towerToInsert.status = 1;

        /* unlock tower */
        towerToInsert.unlockAndIncrementVersion();

        return true;
    }

/* REMOVE *****************************************************************************************/

    private int validateRemove(Tower2 prev, Tower2 towerToRemove, int level, int[] state) {
        int prevVer = prev.getVersion();

        /* if prev is being inserted or being deleted */
        if(prev.status != 1) {
            state[0] = ABORT;
            return 0;
        }

        /* if prev is not pointing to the tower at the current level */
        if(prev.nexts[level].val != towerToRemove.val) {
            state[0] = ABORT;
            return 0;
        }

        /* success, so return the version at this level */
        state[0] = OK;
        return prevVer;

    }

    private boolean tryRemoveAtLevel(Tower2 prev, Tower2 towerToRemove, int level) {
        int validVer;
        int[] state = { OK };
        do {
            /* pre-locking validation */
            validVer = validateRemove(prev, towerToRemove, level, state);
            if (state[0] == ABORT) {
                return false;
            }

        /* try-lock prev */
        } while (!prev.tryLockAtVersion(validVer));

        /* update prev to skip over towerToRemove*/
        prev.nexts[level] = towerToRemove.nexts[level]; /* linearization point when level = 0 */

        prev.unlockAndIncrementVersion();
        return true;
    }

    @Override
    public boolean removeInt(int val) {
        Tower2[] prevs = thdLocalPrevArray.get();
        Tower2 foundTower = null;

        retryFromTraverse: while(true) {
            /* traverse the skiplist */
            foundTower = traverse(val, prevs);

            /* val is not found, so remove fails */
            if (foundTower == null) {
                return false;
            }

            /* pre-lock validation */
            int validVer = foundTower.getVersion();

            /* found tower is already being removed, or is not fully inserted */
            if (foundTower.status != 1) {
                /* insert linearizes at the start thus blocks concurrent removes */
                /* remove linearizes at the end thus blocks concurrent removes */
                return false;
            }

            /* try-lock tower */
            if (!foundTower.tryLockAtVersion(validVer)) {
                continue retryFromTraverse;
            }

            break retryFromTraverse;
        }

        /* remove at each level */
        int level = (foundTower.nexts.length-1);
        while (level >= 0) {
            if (tryRemoveAtLevel(prevs[level], foundTower, level)) {
                /* remove has succeeded at this level */
                level--;

            /* re-traverse and try again with updated prevs */
            } else {
                /* re-traverse to update list of prevs */
                traverse(val, prevs);
            }
        }

        /* mark tower as being deleted */
        foundTower.status = 2;

        /* no need to unlock tower as it is fully unlinked */
        //foundTower.unlockAndIncrementVersion();

        return true;

    }

/* CONTAINS ***************************************************************************************/

    @Override
    public boolean containsInt(int val) {
        Tower2 prev = head;
        Tower2 curr = null;

        /* traverse down the levels of the skiplist */
        for (int level = TOP; level >= 0; level--) {
        	prev.getVersion();
            curr = prev.nexts[level];

            /* traverse at the current level */
            while (curr.val < val) {
                prev = curr;
                curr = curr.nexts[level];
                curr.getVersion();
            }

            /* if val is found at the current level */
            if (curr.val == val) {
                /* success depends on if the tower is not deleted */
                return (curr.status != 2);
            }
        }

        /* val not found at any level */
        return false;
    }

/* UTILITY ****************************************************************************************/

    @Override
    public int size() {
        int size = 0;
        Tower2 curr = head.nexts[0];
        while (curr != tail) {
            if (curr.nexts.length != 0) {
                size++;
            }
            curr = curr.nexts[0];
        }
        return size;
    }

    @Override
    public void clear() {
        for (int i = 0; i < MAX_HEIGHT; i++) {
            head.nexts[i] = tail;
        }
        head.resetLocks();
        tail.resetLocks();
        //assert head.height == MAX_HEIGHT;
        //assert tail.height == MAX_HEIGHT;
    }

    private int getRandomHeight() {
        return Math.max(1,Math.min(TOP, RandomLevelGenerator.randomLevel()));
    }
    
    void print() {
    	System.out.println("skiplist:");
    	for(int i = MAX_HEIGHT -1; i >= 0; i--) {
    		Tower2 next = head;
    		while(next != tail) {
    			System.out.print(next.val + "-- ");
    			next = next.nexts[i];
    		}
    		System.out.println();
    	}
    	System.out.println();
    }
    
}