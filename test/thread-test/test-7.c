typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern int __VERIFIER_nondet_int();
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

extern void reach_error();

int A = -1;

void *thread1(void *arg) {

	A = __VERIFIER_nondet_int();

	return NULL;
}

int main() {

// 	int a = __VERIFIER_nondet_int();

	pthread_t t1;
	pthread_create(&t1, NULL, thread1, NULL);

	__VERIFIER_atomic_begin();
	if (A > 0) {
// 		if (a > 0) {
ERROR: reach_error();
// 		}
	}
	__VERIFIER_atomic_end();

	return 0;
}
