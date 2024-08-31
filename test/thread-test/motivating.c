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
extern void reach_error(void);


// #include <assert.h>
extern void assert(int);
extern void abort(void);

int cnt = 0;

void *thd1(void *arg) {
	cnt = cnt + 1;
}

void *thd2(void *arg) {
	cnt = cnt + 2;
}

int main() {

	pthread_t t1, t2;
	pthread_create(&t1, NULL, thd1, NULL);
	pthread_create(&t2, NULL, thd2, NULL);
	cnt = 2;

	return 0;
}
