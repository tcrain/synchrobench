ROOT = ../../..

include $(ROOT)/common/Makefile.common

BINS = $(BINDIR)/versioned-tower-skiplist

CFLAGS += -std=gnu11 -g -Wall -Wextra -pedantic -Wpadded

.PHONY:	all clean

all:	main

ptst.o: ptst.h
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/ptst.o ptst.c -I.

garbagecoll.o: garbagecoll.h ptst.h
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/garbagecoll.o garbagecoll.c -I.

versioned-lock.o: ../../utils/versioned-lock/versioned-lock.h ../../utils/versioned-lock/versioned-lock.c
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/versioned-lock.o ../../utils/versioned-lock/versioned-lock.c -I.

skiplist-verlock.o: skiplist-verlock.c skiplist-verlock.h
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/skiplist-verlock.o skiplist-verlock.c -I.

versioned-tower.o: versioned-tower.c versioned-tower.h
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/versioned-tower.o versioned-tower.c -I.

test.o: skiplist-verlock.h versioned-tower.h
	$(CC) $(CFLAGS) -c -o $(BUILDIR)/test.o test.c -I.

main: skiplist-verlock.o versioned-tower.o versioned-lock.o test.o ptst.o garbagecoll.o
	$(CC) $(CFLAGS) $(BUILDIR)/garbagecoll.o $(BUILDIR)/ptst.o $(BUILDIR)/skiplist-verlock.o $(BUILDIR)/versioned-tower.o $(BUILDIR)/versioned-lock.o $(BUILDIR)/test.o -o $(BINS) $(LDFLAGS)

clean:
	-rm -f $(BINS) *.o
