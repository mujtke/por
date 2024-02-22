extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
void reach_error();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

void reach_error() {
ERROR: 
	return;
}

int X = 1, Y = -1;

void *thread1(void *arg) {

	__VERIFIER_atomic_begin();
	int a = X;
	if (a > 0) {
		X = 2;
	} else {
		X = -2;
	}
	__VERIFIER_atomic_end();
	
	if (X > 0) reach_error();

    return NULL;
}

int main() {

    pthread_t t0;
    pthread_create(&t0, NULL, thread1, NULL);

	X = -1;
    
    return 0;
}
