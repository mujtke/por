extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void reach_error(void);

int X = 1, Y = -1;

void *thread1(void *arg) {

	while (X < 5) {
		X = X + 1;
	}

	int a = 1;
    return NULL;
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, NULL, thread1, NULL);
		
		if (X == 5) {
ERROR:reach_error();
		}
    return 0;
}
