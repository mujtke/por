#include <assert.h>
void reach_error() { assert(0); }
extern void abort(void);
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }

extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

#include <pthread.h>

int x = -1, y = -1, z = -1;

void *P0(void *arg) {
	__VERIFIER_atomic_begin();
	x = 0;
	__VERIFIER_atomic_end();
}

void *P1(void *arg) {
	__VERIFIER_atomic_begin();
	y = x + 1;
	__VERIFIER_atomic_end();
}

int main() {

	pthread_t t0, t1;
	pthread_create(&t0, NULL, P0, NULL);
	pthread_create(&t1, NULL, P1, NULL);

	__VERIFIER_atomic_begin();
	z = x + 1;
	__VERIFIER_atomic_end();

	__VERIFIER_assert(z);

	return 0;
}

