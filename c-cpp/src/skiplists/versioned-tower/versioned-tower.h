#ifndef _VERSIONED_TOWERS_SKIPLIST_H
#define _VERSIONED_TOWERS_SKIPLIST_H

#include <stdbool.h>
#include "skiplist-verlock.h"

bool validate_insert(sl_node_t *prev, sl_node_t *tower_to_insert, int level, verlock_t* valid_ver);
bool validate_remove(sl_node_t *prev, sl_node_t *tower_to_remove, int level, verlock_t* valid_ver);
sl_node_t* traverse(val_t val, sl_node_t** prevs, sl_node_t* head);
int sl_contains(sl_intset_t *set, val_t val);

bool try_insert_at_level(sl_node_t* prev, sl_node_t* tower_to_insert, int level);
int sl_insert(sl_intset_t *set, val_t val);

bool try_remove_at_level(sl_node_t* prev, sl_node_t* tower_to_remove, int level);
int sl_remove(sl_intset_t *set, val_t val);

#endif
