#include "versioned-tower.h"

/** TRAVERSE **************************************************************************************/

sl_node_t* traverse(val_t val, sl_node_t** prevs, sl_node_t* head) {
    sl_node_t* prev = head;
    sl_node_t* curr = NULL;
    sl_node_t* found = NULL;

    /* traverse down the levels of the skiplist */
    for (int level = (levelmax-1); level >= 0; level--) {
        curr = prev->nexts[level];

        /* traverse at the current level */
        while (curr->val < val) {
            prev = curr;
            curr = curr->nexts[level];
        }

        /* record prevs at each level */
        prevs[level] = prev;

        /* record if val is ever found */
        if (curr->val == val) {
            found = curr;
        }
    }

    /* val not found at any level */
    return found;
}

/** INSERT ****************************************************************************************/

bool validate_insert(sl_node_t *prev, sl_node_t *tower_to_insert, int level, verlock_t* valid_ver) {
    verlock_t prev_ver = get_version(&prev->lock);

    /* if prev is being inserted or being deleted */
    if(prev->status != 1) {
        return false;
    }

    /* if prev is already pointing to tower_to_insert at the current level */
    if(prev->nexts[level]->val == tower_to_insert->val) {
        return false;
    }

    /* if prev is no longer the appropriate prev to utilize */
    if(prev->nexts[level]->val < tower_to_insert->val) {
        return false;
    }

    /* success, so return the version at this level */
    *valid_ver = prev_ver;
    return true;
}


bool try_insert_at_level(sl_node_t* prev, sl_node_t* tower_to_insert, int level) {
    verlock_t valid_ver;
    do {
        /* pre-locking validation */
        if (!validate_insert(prev, tower_to_insert, level, &valid_ver)) {
            return false;
        }

    /* try-lock prev */
    } while (!try_lock_at_version(&prev->lock, valid_ver));

    /* set tower_to_insert's next */
    tower_to_insert->nexts[level] = prev->nexts[level];

    /* update prev's next */
    prev->nexts[level] = tower_to_insert; /* linearization point when level = 0 */

    unlock_and_increment_version(&prev->lock);
    return true;
}

int sl_insert(sl_intset_t *set, val_t val) {
    sl_node_t** prevs = pthread_getspecific(prevs_key);
    sl_node_t* tower_to_insert = NULL;
    sl_node_t* found_tower = NULL;

retry_from_traverse:
    /* traverse the skiplist */
    found_tower = traverse(val, prevs, set->head);

    /* the value already exists in the skiplist */
    if (found_tower != NULL) {
        /* wait-free traversed onto node just before being fully unlinked / deleted */
        if (found_tower->status == 2) {
            /* can not return false as that equals successful contains */
            goto retry_from_traverse;
        }
        /* insert linearizes at the start thus allows concurrent inserts */
        /* remove linearizes at the end thus allows concurrent inserts */
        return false;
    }

    /* create tower to insert */
    if (tower_to_insert == NULL) {
        int height = get_rand_level();
        ptst_t *ptst = ptst_critical_enter();
        tower_to_insert = sl_new_simple_node(val, height, ptst);
        ptst_critical_exit(ptst);

        /* start tower as locked */
        spinlock(&tower_to_insert->lock); /* guaranteed to work on new node */
    }

    /* insert at each level */
    int level = 0;
    while (level < tower_to_insert->height) {
        if (try_insert_at_level(prevs[level], tower_to_insert, level)) {
            /* insert has succeeded at this level */
            level++;

        /* re-traverse and try again with updated prevs */
        } else {
            /* race: aborting at level zero might mean another thread inserted first */
            if (level == 0){
                goto retry_from_traverse;
            }
            /* re-traverse to update list of prevs */
            traverse(val, prevs, set->head);
        }
    }

    /* mark as valid */
    tower_to_insert->status = 1;

    /* unlock tower */
    unlock_and_increment_version(&tower_to_insert->lock);

    return true;
}

/** REMOVE ****************************************************************************************/

bool validate_remove(sl_node_t *prev, sl_node_t *tower_to_remove, int level, verlock_t* valid_ver) {
    verlock_t prev_ver = get_version(&prev->lock);

    /* if prev is being inserted or being deleted */
    if(prev->status != 1) {
        return false;
    }

    /* if prev is not pointing to the tower at the current level */
    if(prev->nexts[level]->val != tower_to_remove->val) {
        return false;
    }

    /* success, so return the version at this level */
    *valid_ver = prev_ver;
    return true;
}


bool try_remove_at_level(sl_node_t* prev, sl_node_t* tower_to_remove, int level) {
    verlock_t valid_ver;
    do {
        /* pre-locking validation */
        if (!validate_remove(prev, tower_to_remove, level, &valid_ver)) {
            return false;
        }

    /* try-lock prev */
    } while (!try_lock_at_version(&prev->lock, valid_ver));

    /* update prev to skip over tower_to_remove*/
    prev->nexts[level] = tower_to_remove->nexts[level];  /* linearization point when level = 0 */

    unlock_and_increment_version(&prev->lock);
    return true;
}


int sl_remove(sl_intset_t *set, val_t val) {
    sl_node_t **prevs = pthread_getspecific(prevs_key);
    sl_node_t* found_tower = NULL;

retry_from_traverse:
    /* traverse the skiplist */
    found_tower = traverse(val, prevs, set->head);

    /* val is not found, so remove fails */
    if (found_tower == NULL) {
        return false;
    }

    /* pre-lock validation */
    verlock_t valid_ver = get_version(&found_tower->lock);

    /* found tower is already being removed, or is not fully inserted */
    if (found_tower->status != 1) {
        /* insert linearizes at the start thus blocks concurrent removes */
        /* remove linearizes at the end thus blocks concurrent removes */
        goto retry_from_traverse;
    }

    /* try-lock tower */
    if (!try_lock_at_version(&found_tower->lock, valid_ver)) {
        goto retry_from_traverse;
    }

    /* remove at each level */
    int level = (found_tower->height -1);
    while (level >= 0) {
        if (try_remove_at_level(prevs[level], found_tower, level)) {
            /* remove has succeeded at this level */
            level--;

        /* re-traverse and try again with updated prevs */
        } else {
            /* re-traverse to update list of prevs */
            traverse(val, prevs, set->head);
        }
    }

    /* mark tower as being deleted */
    found_tower->status = 2;

    /* no need to unlock tower as it is fully unlinked */
    //unlock_and_increment_version(&found_tower->lock);

    ptst_t *ptst = ptst_critical_enter();
    sl_delete_node(found_tower, ptst);
    ptst_critical_exit(ptst);

    return true;
}

/** CONTAINS **************************************************************************************/

int sl_contains(sl_intset_t *set, val_t val) {
    sl_node_t* prev = set->head;
    sl_node_t* curr;

    /* traverse down the levels of the skiplist */
    for (int level = (levelmax-1); level >= 0; level--) {
        curr = prev->nexts[level];

        /* traverse at the current level */
        while (curr->val < val) {
            prev = curr;
            curr = curr->nexts[level];
        }

        /* if val is found at the current level */
        if (curr->val == val) {
            /* success depends on if the tower is not deleted */
            return (curr->status != 2);
        }
    }

    /* val not found at any level */
    return false;
}

