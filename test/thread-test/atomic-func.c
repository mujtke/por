extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
#include <assert.h>
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

typedef unsigned pthread_t;
#define NULL ((void *) 0)

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 0, Y = 0;

void *P0(void *arg) {

	__VERIFIER_atomic_begin();
	__VERIFIER_assert(!(X > 0 && Y < 0));
	__VERIFIER_atomic_end();

	return (void *) 0;
}

int main() {
 
	pthread_t t;
	pthread_create(&t, NULL, P0, NULL);
	X = 1;
	Y = -1;

	return 0;
}
