// #include <pthraed.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

// #include <assert.h>
extern void assert(int);
extern void abort(void);
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {
	  // int kkkkkk;
	  abort();
  }
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
// #include <assert.h>
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <assert.h>
// #include <pthread.h>
#ifndef TRUE
#define TRUE (_Bool)1
#endif
#ifndef FALSE
#define FALSE (_Bool)0
#endif
#ifndef NULL
#define NULL ((void*)0)
#endif
#ifndef FENCE
#define FENCE(x) ((void)0)
#endif
#ifndef IEEE_FLOAT_EQUAL
#define IEEE_FLOAT_EQUAL(x,y) (x==y)
#endif
#ifndef IEEE_FLOAT_NOTEQUAL
#define IEEE_FLOAT_NOTEQUAL(x,y) (x!=y)
#endif

void * P0(void *arg);

void * P1(void *arg);

int CNT = 0;

int EAX = 0;

_Bool A;

_Bool B;

int x = 0;

int y = 0;

void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 2;
  __VERIFIER_atomic_end();
  
  __VERIFIER_atomic_begin();
  EAX = x;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  CNT = CNT + 1;
  __VERIFIER_atomic_end();

  return 0;
}

void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  CNT = CNT + 1;
  __VERIFIER_atomic_end();

  return 0;
}

int main()
{
  pthread_t t2561;
  pthread_create(&t2561, NULL, P0, NULL);
  pthread_t t2562;
  pthread_create(&t2562, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  A = CNT == 2;
  __VERIFIER_atomic_end();

//   assume_abort_if_not(A);
  if (A == 0)
	  abort();

  __VERIFIER_atomic_begin();
  B = !(y == 2 && EAX == 0);
  __VERIFIER_atomic_end();

//   __VERIFIER_assert(B);
  if (B == 0) {
ERROR: reach_error();
  }

  return 0;
}
