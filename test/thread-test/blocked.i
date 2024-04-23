# 0 "blocked.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "blocked.c"

typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);


extern void assert(int);
extern void abort(void);
extern void reach_error();


extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

int A = 0, B = 1;

void *thread1(void *arg) {
 __VERIFIER_atomic_begin();
 int a = B;
 if (A > 0) {
  A = 1;
 }
 __VERIFIER_atomic_end();

 return ((void *) 0);
}

void *thread2(void *arg) {
 __VERIFIER_atomic_begin();
 if (A >= 0) {
  B = -1;
 }

 return ((void *) 0);
}

int main() {

 pthread_t t1, t2;
 pthread_create(&t1, ((void *) 0), thread1, ((void *) 0));
 pthread_create(&t2, ((void *) 0), thread2, ((void *) 0));

 __VERIFIER_atomic_begin();
 A = -1;
 B = 2;
 __VERIFIER_atomic_end();

 return 0;
}
