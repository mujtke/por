# 0 "race-1_3-join.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "race-1_3-join.c"
# 9 "race-1_3-join.c"
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;


extern void assert(int);
extern void pthread_mutex_lock(pthread_mutex_t *);
extern void pthread_mutex_unlock(pthread_mutex_t *);
extern void pthread_mutex_init(pthread_mutex_t *, void *);
extern void pthread_mutex_destroy(pthread_mutex_t *);
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_join(pthread_t, void *);




extern void abort(void);
extern void __VERIFIER_atomic_begin(void);
extern void __VERIFIER_atomic_end(void);

void reach_error() { assert(0); }
int __VERIFIER_nondet_int(void);
void ldv_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();}}; return; }

pthread_t t1;
pthread_mutex_t mutex;
int pdev;

void *thread1(void *arg) {
   pthread_mutex_lock(&mutex);
   __VERIFIER_atomic_begin();
   pdev = 6;
   __VERIFIER_atomic_end();
   pthread_mutex_unlock(&mutex);
   return 0;
}

int module_init() {
   pthread_mutex_init(&mutex, ((void *) 0));

   pdev = 1;
   ldv_assert(pdev==1);
   if(__VERIFIER_nondet_int()) {

      pthread_create(&t1, ((void *) 0), thread1, ((void *) 0));



      return 0;
   }

   pdev = 3;
   ldv_assert(pdev==3);
   pthread_mutex_destroy(&mutex);
   return -1;
}

void module_exit() {
   void *status;

   __VERIFIER_atomic_begin();
   pdev = 4;
   __VERIFIER_atomic_end();
   __VERIFIER_atomic_begin();
   ldv_assert(pdev==4);
   __VERIFIER_atomic_end();
   pthread_join(t1, &status);
   pthread_mutex_destroy(&mutex);

   pdev = 5;
   ldv_assert(pdev==5);
}

int main(void) {
    if(module_init()!=0) goto module_exit;
    module_exit();
module_exit:
    return 0;
}
