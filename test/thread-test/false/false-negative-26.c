typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

extern void assert(int);
extern void abort(void);
extern _Bool __VERIFIER_nondet_bool(void);
extern int __VERIFIER_nondet_int();
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

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


int p0_EAX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x = 0;


_Bool rb0t1;


_Bool rb1t1;


_Bool wb0u;


int y = 0;


void * P0(void *arg)
{

  __VERIFIER_atomic_begin();
  y = 2;
  x = !wb0u || rb0t1 && !rb1t1 ? x : FALSE;
  rb0t1 = __VERIFIER_nondet_bool();
  p0_EAX = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();

  return 0;
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  wb0u = TRUE;
  rb1t1 = rb0t1;
  y = 1;
  x = __VERIFIER_nondet_int();
  __unbuffered_cnt = __unbuffered_cnt + 1;
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
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (main$tmp_guard0 == 0) {
	  abort();
  }
  __VERIFIER_atomic_end();

  x = __VERIFIER_nondet_int();

  __VERIFIER_atomic_begin();
  if (y == 2 && p0_EAX == 0)
	  ERROR:reach_error();
  __VERIFIER_atomic_end();

  return 0;
}
