# 0 "mo-test1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "mo-test1.c"

typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);
extern void reach_error(void);
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



extern void assert(int);
extern void abort(void);

int X = 0, Y = -1;

void *thd1(void *arg) {
 X = 1;
 return ((void *) 0);
}

void *thd2(void *arg) {
 __VERIFIER_atomic_begin();
 Y = X;
 X = Y;
 __VERIFIER_atomic_end();
 return ((void *) 0);
}

int main() {

 pthread_t t1, t2;
 pthread_create(&t1, ((void *) 0), thd1, ((void *) 0));
 pthread_create(&t2, ((void *) 0), thd2, ((void *) 0));
 X = 2;

 if (Y == 2) {
ERROR: reach_error();
 }

 return 0;
}
