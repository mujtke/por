# 0 "c4.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "c4.c"

extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 0, Y = 0, Z = 0;

void *thread1(void *arg) {

    Y = Z;

    return ((void *) 0);
}

void *thread2(void *arg) {

    Z = X;

    return ((void *) 0);
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, ((void *) 0), thread1, ((void *) 0));
    pthread_create(&t1, ((void *) 0), thread2, ((void *) 0));

    X = Y;

    return 0;
}
