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
extern void reach_error();


// #include <assert.h>
extern void assert(int);
extern void abort(void);

int A = 0, B = 1;
pthread_mutex_t l;

void *thread1(void *arg) {

	pthread_mutex_lock(&l);
	int b = B;
	A = 2;
	pthread_mutex_unlock(&l);

	return NULL;
}

int main() {
	pthread_t t1;
	pthread_create(&t1, NULL, thread1, NULL);

	pthread_mutex_lock(&l);
	B = 3;
	if (A > 0) {
		B = 4;
	}
	pthread_mutex_unlock(&l);

	if (B == 4) {
ERROR:
		reach_error();
	}

	return 0;
}
