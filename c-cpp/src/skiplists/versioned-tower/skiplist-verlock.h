#include <assert.h>
#include <getopt.h>
#include <limits.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <stdio.h>
#include <sys/time.h>
#include <time.h>
#include <stdint.h>

#include <stdatomic.h>
#include "../../utils/versioned-lock/versioned-lock.h"
#include "common.h"
#include "ptst.h"
#include "garbagecoll.h"

#define DEFAULT_DURATION                10000
#define DEFAULT_INITIAL                 256
#define DEFAULT_NB_THREADS              1
#define DEFAULT_RANGE                   0x7FFFFFFF
#define DEFAULT_SEED                    0
#define DEFAULT_UPDATE                  20
#define DEFAULT_ELASTICITY              4
#define DEFAULT_ALTERNATE               0
#define DEFAULT_EFFECTIVE               1

#define XSTR(s)                         STR(s)
#define STR(s)                          #s

extern pthread_key_t prevs_key;
extern unsigned int global_seed;
/* Skip list level */
#ifdef TLS
extern __thread unsigned int *rng_seed;
#else /* ! TLS */
extern pthread_key_t rng_seed_key;
#endif /* ! TLS */
extern unsigned int levelmax;

#define TRANSACTIONAL                   d->unit_tx

typedef intptr_t val_t;
#define VAL_MIN                         INT_MIN
#define VAL_MAX                         INT_MAX

typedef struct sl_node {
    val_t val;
    _Atomic(verlock_t) lock;
    volatile int height;
    volatile int status; /* 0 = being inserted, 1 = valid, 2 = being deleted / deleted */
    struct sl_node** nexts;
} sl_node_t;

typedef struct sl_intset {
    sl_node_t *head;
} sl_intset_t;

void *xmalloc(size_t size);
int rand_100();

int get_rand_level(void);
int floor_log_2(unsigned int n);

/*
 * Create a new node without setting its next fields.
 */
sl_node_t *sl_new_simple_node(val_t val, int destination_height, ptst_t *ptst);
/*
 * Create a new node with its next field.
 * If next=NULL, then this create a tail node.
 */
sl_node_t *sl_new_node(val_t val, sl_node_t *nexts, int destination_height, ptst_t *ptst);
void sl_delete_node(sl_node_t *n, ptst_t *ptst);
sl_intset_t *sl_set_new(ptst_t *ptst);
void sl_set_delete(sl_intset_t *set, ptst_t *ptst);
int sl_set_size(sl_intset_t *set);

/*
 * Returns a pseudo-random value in [1;range).
 * Depending on the symbolic constant RAND_MAX>=32767 defined in stdlib.h,
 * the granularity of rand() could be lower-bounded by the 32767^th which might
 * be too high for given values of range and initial.
 */
long rand_range(long r);

void set_subsystem_init(void);
