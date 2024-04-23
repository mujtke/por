extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);

extern void assert(int);
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();


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


int cnt = 0;


int p1_EAX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x = 0;


int y = 0;


_Bool r0_thd0;


_Bool r0_thd1;


int w0;


_Bool w0_used;


void * P0(void *arg)
{
  __VERIFIER_atomic_begin();

  w0 = 1;

  w0_used = TRUE;

  r0_thd1 = TRUE;
  x = 1;
  __VERIFIER_atomic_end();



  __VERIFIER_atomic_begin();

  y = w0_used ? w0 : y;

  w0_used = FALSE;

  cnt = cnt + 1;
  __VERIFIER_atomic_end();

  return 0;
}



void * P1(void *arg)
{
  x = 2;

  __VERIFIER_atomic_begin();

  w0 = (!w0_used ? w0 : w0);

  p1_EAX = y;

  __VERIFIER_atomic_end();

  cnt = cnt + 1;

  return 0;
}


int main()
{
  pthread_t t1441;
  pthread_create(&t1441, NULL, P0, NULL);
  pthread_t t1442;
  pthread_create(&t1442, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = cnt == 2;
  __VERIFIER_atomic_end();

  if (main$tmp_guard0 == 0) abort();

  __VERIFIER_atomic_begin();
  /* Program proven to be relaxed for X86, model checker says YES. */
  main$tmp_guard1 = !(x == 2 && p1_EAX == 0);
  __VERIFIER_atomic_end();

  /* Program proven to be relaxed for X86, model checker says YES. */

  if (main$tmp_guard1 == 0)
	  ERROR: reach_error();
  return 0;
}

