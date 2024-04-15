typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);
extern void assert(int);
extern void abort(void);
extern int __VERIFIER_nondet_int();

int A = 0;

void *thd1(void *arg) {

	A = __VERIFIER_nondet_int();
	A = __VERIFIER_nondet_int();

	return NULL;
}

int main() {

	pthread_t t1;
	pthread_create(&t1, NULL, thd1, NULL);

	if (A < 0) {
		int a;
	}

	return 0;
}
