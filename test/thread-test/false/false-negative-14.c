extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
#include <assert.h>
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <assert.h>
// #include <pthread.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

extern void abort(void);
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

int __unbuffered_cnt = 0;

int EA0 = 0;

int EB0 = 0;

int EA1 = 0;

int EB1 = 0;

_Bool main$tmp_guard0;

_Bool main$tmp_guard1;

int x = 0;

_Bool x$flush_delayed;

int y = 0;

void * P0(void *arg)
{
  y = 1;
  EA0 = y;

  __VERIFIER_atomic_begin();
  EB0 = x;
  x = __VERIFIER_nondet_bool();
  __VERIFIER_atomic_end();

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}

void * P1(void *arg)
{

  __VERIFIER_atomic_begin();
  x$flush_delayed = __VERIFIER_nondet_bool();
  EA1 = 1;
  x = x$flush_delayed ? x : 1;
  __VERIFIER_atomic_end();

  EB1 = y;

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}

int main()
{
  pthread_t t0;
  pthread_t t1;
  pthread_create(&t0, NULL, P0, NULL);
  pthread_create(&t1, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (main$tmp_guard0 == 0) abort();
  __VERIFIER_atomic_end();

//   if (main$tmp_guard0 == 0) abort();

  __VERIFIER_atomic_begin();
//   main$tmp_guard1 = !(EA0 == 1 && EB0 == 0 && EA1 == 1 && EB1 == 0);
  main$tmp_guard1 = !(EB0 == 0 && EA1 == 1 && EB1 == 0);
  __VERIFIER_atomic_end();

  if (main$tmp_guard1 == 0)
	  ERROR: reach_error();
  return 0;
}

