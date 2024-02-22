extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 1, Y = -1;

void *thread1(void *arg) {

	if (X > 0) {
		Y = 1;
	} else {
		Y = 0;
	}

    return NULL;
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, NULL, thread1, NULL);

	X = -1;
	int b = Y;
    
    return 0;
}
