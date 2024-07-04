// #include <pthraed.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern int __VERIFIER_nondet_int();

extern void reach_error();

int A = -1, B = -1;

void *thread1(void *arg) {
	A = __VERIFIER_nondet_int();
// 	A = 1;

	B = __VERIFIER_nondet_int();
// 	B = 2;

	A = __VERIFIER_nondet_int();
// 	A = -1;

	return NULL;
}

int main() {

	pthread_t t1;
	pthread_create(&t1, NULL, thread1, NULL);

	if (A > 0) {
		if (B > 0) {
			if (A < 0) {
ERROR: reach_error();
			}
		}
	}
	
	return 0;
}
