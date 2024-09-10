// The pthread relative.
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

// Assertions.
extern void assert(int);
extern void abort(void);
extern void reach_error();

// Atomic block.
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

int X = 0;

void *P0(void *arg) {
	int a = X;
}

void *P1(void *arg) {
	int b = X;
}

int main() {

	pthread_t t1, t2;
	pthread_create(&t1, NULL, P0, NULL);
	pthread_create(&t2, NULL, P1, NULL);

	X = 2;
	return 0;
}
