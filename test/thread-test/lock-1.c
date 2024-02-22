extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_mutex_t *);
extern void pthread_mutex_unlock(pthread_mutex_t *);

int X = 1, Y = -1;
pthread_mutex_t mutex;

void *thread1(void *arg) {

	pthread_mutex_lock(&mutex);
	X++;
	pthread_mutex_unlock(&mutex);

    return NULL;
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, NULL, thread1, NULL);

	X = -1;
	int b = Y;
    
    return 0;
}
