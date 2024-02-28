// #include <pthraed.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);
extern int __VERIFIER_nondet_int();

// #include <assert.h>
extern void assert(int);
extern void abort(void);
extern void reach_error();

int A = -1, B = -1;

void *thread1(void *arg) {
	A = __VERIFIER_nondet_int();

	B = __VERIFIER_nondet_int();

	A = __VERIFIER_nondet_int();

	return NULL;
}

int main() {

	pthread_t t1;
	pthread_create(&t1, NULL, thread1, NULL);

	if (A > 0) {
		if (B > 0) {
			if (A < 0) {
				reach_error();
			}
		}
	}
	
	return 0;
}
