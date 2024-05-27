// The pthread relative.
typedef unsigned pthread_t;
#define NULL ((void *) 0)
extern void *pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void reach_error();

// Atomic block.
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

int X = 1, Y = 2, Z = 3;

void *thread1(void *arg) {
	__VERIFIER_atomic_begin();
	int a = Z;
	Y = a;
	__VERIFIER_atomic_end();
	return NULL;
}

void *thread2(void *arg) {
	__VERIFIER_atomic_begin();
	int b = X;
	Z = b;
	__VERIFIER_atomic_end();
	return NULL;
}

int main() {
	pthread_t t1, t2;
	pthread_create(&t1, NULL, thread1, NULL);
	pthread_create(&t2, NULL, thread2, NULL);

	__VERIFIER_atomic_begin();
	int c = Y;
	X = c;
	__VERIFIER_atomic_end();

	if (X == 2 && Y == 1 && Z == 1)
		ERROR: reach_error();

	return 0;
}
