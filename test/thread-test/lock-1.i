# 0 "lock-1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "lock-1.c"
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



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

    return ((void *) 0);
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, ((void *) 0), thread1, ((void *) 0));

 X = -1;
 int b = Y;

    return 0;
}
