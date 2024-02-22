
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 0, Y = 0, Z = 0;

void *thread1(void *arg) {

    Y = Z;

    return NULL;
}

void *thread2(void *arg) {

    Z = X;
    
    return NULL;
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, NULL, thread1, NULL);
    pthread_create(&t1, NULL, thread2, NULL);

    X = Y;
    
    return 0;
}