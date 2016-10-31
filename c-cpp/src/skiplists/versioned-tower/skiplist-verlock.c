#include "skiplist-verlock.h"

unsigned int levelmax;

static int gc_id[2];

void *xmalloc(size_t size)
{
    void *p = malloc(size);
    if (p == NULL) {
        perror("malloc");
        exit(1);
    }
    return p;
}

/*
 * Returns a random level for inserting a new node, results are hardwired to p=0.5, min=1, max=32.
 *
 * "Xorshift generators are extremely fast non-cryptographically-secure random number generators on
 * modern architectures."
 *
 * Marsaglia, George, (July 2003), "Xorshift RNGs", Journal of Statistical Software 8 (14)
 */
int get_rand_level() {
	static uint32_t y = 2463534242UL;
	y^=(y<<13);
	y^=(y>>17);
	y^=(y<<5);
	uint32_t temp = y;
	uint32_t level = 1;
	while (((temp >>= 1) & 1) != 0) {
		++level;
	}
	/* 1 <= level <= levelmax */
	if (level > levelmax) {
		return (int)levelmax;
	} else {
		return (int)level;
	}
}

int floor_log_2(unsigned int n) {
    int pos = 0;
    if (n >= 1<<16) { n >>= 16; pos += 16; }
    if (n >= 1<< 8) { n >>=  8; pos +=  8; }
    if (n >= 1<< 4) { n >>=  4; pos +=  4; }
    if (n >= 1<< 2) { n >>=  2; pos +=  2; }
    if (n >= 1<< 1) {           pos +=  1; }
    return ((n == 0) ? (-1) : pos);
}

/*
 * Create a new node without setting its next fields.
 */
sl_node_t *sl_new_simple_node(val_t val, int height, ptst_t *ptst)
{
    sl_node_t *node;

    node = gc_alloc(ptst, gc_id[0]);

    node->lock = 0; /* NOTE: ensure you avoid GCC bug 68622: initialization of atomic objects emits unnecessary fences */
    node->val = val;
    node->height = height;
    node->nexts = gc_alloc(ptst, gc_id[1]);

    return node;
}

/*
 * Create a new node with its next field.
 * If next=NULL, then this create a tail node.
 */
sl_node_t *sl_new_node(val_t val, sl_node_t *next, int height, ptst_t *ptst)
{
    sl_node_t *node;
    node = sl_new_simple_node(val, height, ptst);

    /* initialized the next array to levelmax */
    for (int i = 0; i < levelmax; i++) {
        node->nexts[i] = next;
    }

    return node;
}

void sl_delete_node(sl_node_t *n, ptst_t *ptst)
{
    gc_free(ptst, (void*)n->nexts, gc_id[1]);
    gc_free(ptst, (void*)n, gc_id[0]);
}

sl_intset_t *sl_set_new(ptst_t *ptst)
{
    sl_intset_t *set;
    sl_node_t *tail;
    sl_node_t *head;

    set = (sl_intset_t *)xmalloc(sizeof(sl_intset_t));

    tail = sl_new_node(VAL_MAX, NULL, levelmax, ptst);
    head = sl_new_node(VAL_MIN, tail, levelmax, ptst);

    set->head = head;

    tail->status = 1;
    head->status = 1;

    return set;
}

void sl_set_delete(sl_intset_t *set, ptst_t *ptst)
{
    sl_node_t *node, *next;

    node = set->head;
    while (node != NULL) {
        next = node->nexts[0];
        sl_delete_node(node, ptst);
        node = next;
    }
    free(set);
}

int sl_set_size(sl_intset_t *set)
{
    int size = -1;
    sl_node_t *node;

    /* We have at least 2 elements */
    node = set->head->nexts[0];
    while (node->nexts[0] != NULL) {
        if (node->height > 0) {
            size++;
        }
        node = node->nexts[0];
    }

    return size;
}

/**
 * set_subsystem_init - initialise the set subsystem
 */
void set_subsystem_init(void)
{
        gc_id[0]  = gc_add_allocator(sizeof(sl_node_t));
        gc_id[1]  = gc_add_allocator(levelmax * sizeof(sl_node_t *));
}
