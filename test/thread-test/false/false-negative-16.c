extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
// #include <assert.h>
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


int cnt = 0;


int p1_EAX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x = 0;


int y = 0;


// _Bool flush_delayed;


// int mem_tmp;


_Bool r0_thd0;


_Bool r0_thd1;


// _Bool r0_thd2;


// _Bool r1_thd0;


// _Bool r1_thd1;


// _Bool r1_thd2;


// _Bool read_delayed;


// int *read_delayed_var;


int w0;


_Bool w0_used;


// int w1;


// _Bool w1_used;


// _Bool choice0;


// _Bool choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
//   w1 = w0;
  w0 = 1;
//   w1_used = w0_used;
  w0_used = TRUE;
//   __VERIFIER_assert(!(w1_used && w0_used));
//   r1_thd0 = r0_thd0;
//   r1_thd1 = r0_thd1;
//   r1_thd2 = r0_thd2;
  r0_thd1 = TRUE;
  x = 1;
  __VERIFIER_atomic_end();

//   x = 1;

  __VERIFIER_atomic_begin();
//   _Bool tmp1 = w0_used && r0_thd1, 
// 		tmp2 = w1_used && r1_thd1;
//   _Bool tmp1 = w0_used && r0_thd1; 
//   y = tmp1 ? w0 : (tmp2 ? w1 : y);
//   y = tmp1 ? w0 : y;
  y = w0_used ? w0 : y;
//   w0_used = tmp1 ? FALSE : w0_used;
  w0_used = FALSE;
//   w1_used = tmp1 || tmp2 ? FALSE : w1_used;
//   r0_thd1 = tmp1 ? FALSE : r0_thd1;
//   r1_thd1 = tmp1 || tmp2 ? FALSE : r1_thd1;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();

//   cnt = cnt + 1;

  return 0;
}



void * P1(void *arg)
{
  x = 2;

  __VERIFIER_atomic_begin();
//   choice0 = __VERIFIER_nondet_bool();
//   choice2 = __VERIFIER_nondet_bool();
//   flush_delayed = choice2;
//   mem_tmp = y;
//   _Bool tmp3 = !r0_thd2 && !w1_used,
// 		tmp4 = !r0_thd2 && !r1_thd2,
// 		tmp5 = w0_used && r0_thd2;
//   y = !w0_used || tmp3 || tmp4 ? y : (tmp5 ? w0 : w1);
//   // w0 = choice2 ? w0 : (!w0_used || tmp3 || tmp4 ? w0 : (tmp5 ? w0 : w0));
//   w0 = (!w0_used || tmp3 || tmp4 ? w0 : (tmp5 ? w0 : w0));
  w0 = (!w0_used ? w0 : w0);
//   w1 = choice2 ? w1 : (!w0_used || tmp3 || tmp4 ? w1 : (tmp5 ? w1 : w1));
//   w0_used = choice2 ? w0_used : (!w0_used || tmp3 || tmp4 ? w0_used : (tmp5 ? FALSE : w0_used));
//   w1_used = choice2 ? w1_used : (!w0_used || tmp3 || tmp4 ? w1_used : (tmp5 ? FALSE : FALSE));
  p1_EAX = y;
//   y = flush_delayed ? mem_tmp : y;
//   flush_delayed = FALSE;
  __VERIFIER_atomic_end();

//   __VERIFIER_atomic_begin();
//   _Bool tmp7 = w0_used && r0_thd2,
// 		tmp8 = w1_used && r1_thd2;
//   y = tmp7 ? w0 : (tmp8 ? w1 : y);
//   w0_used = tmp7 ? FALSE : w0_used;
//   w1_used = tmp7 || w1_used && r1_thd2 ? FALSE : w1_used;
//   r0_thd2 = tmp7 ? FALSE : r0_thd2;
//   r1_thd2 = tmp7 || w1_used && r1_thd2 ? FALSE : r1_thd2;
  cnt = cnt + 1;
//   __VERIFIER_atomic_end();

//   cnt = cnt + 1;

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

//   assume_abort_if_not(main$tmp_guard0);
  if (main$tmp_guard0 == 0) abort();

//   __VERIFIER_atomic_begin();
//   _Bool tmp9 = w0_used && r0_thd0,
// 		tmp10 = w1_used && r1_thd0;
//   y = tmp9 ? w0 : (tmp10 ? w1 : y);
//   w0_used = tmp8 ? FALSE : w0_used;
//   w1_used = tmp8 || tmp9 ? FALSE : w1_used;
//   r0_thd0 = tmp8 ? FALSE : r0_thd0;
//   r1_thd0 = tmp8 || tmp9 ? FALSE : r1_thd0;
//   __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  /* Program proven to be relaxed for X86, model checker says YES. */
  main$tmp_guard1 = !(x == 2 && p1_EAX == 0);
  __VERIFIER_atomic_end();

  /* Program proven to be relaxed for X86, model checker says YES. */
//   __VERIFIER_assert(main$tmp_guard1);
  if (main$tmp_guard1 == 0)
	  ERROR: reach_error();
  return 0;
}

