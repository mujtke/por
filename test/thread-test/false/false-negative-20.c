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


int cnt = 0;


int p0_EAX = 0;


_Bool main_tmp_guard0;


_Bool main_tmp_guard1;


int x = 0;


// _Bool flush_delayed;


// int mem_tmp;


// _Bool r_b0t0;


// _Bool r_b0t1;


// _Bool r_b0t2;


// _Bool r_b1t0;


// _Bool r_b1t1;


// _Bool r_b1t2;


int w_buff0;


_Bool w_buff0_used;


int w_buff1;


_Bool w_buff1_used;


int y = 0;


// _Bool weak$$choice0;


// _Bool weak$$choice2;


void * P0(void *arg)
{
  y = 2;
  __VERIFIER_atomic_begin();
//   weak$$choice0 = __VERIFIER_nondet_bool();
//   mem_tmp = x;
//   _Bool tmp1 = w_buff0_used;
//   _Bool tmp2 = w_buff1;
//   x = tmp1 ? x : tmp2;
  x = w_buff0_used ? x : w_buff1;
//   w_buff0_used = tmp1 ? w_buff0_used : tmp2;
  w_buff0_used = w_buff1 ? w_buff0_used : w_buff1;
  p0_EAX = x;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();

  return 0;
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  w_buff1 = w_buff0;
//   w_buff0 = 1;
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
  y = 1;
  __VERIFIER_atomic_end();

//   y = 1;

//   __VERIFIER_atomic_begin();
//   x = w_buff0_used && r_b0t2 ? w_buff0 : (w_buff1_used && r_b1t2 ? w_buff1 : x);
//   w_buff0_used = w_buff0_used && r_b0t2 ? FALSE : w_buff0_used;
//   __VERIFIER_atomic_end();
  cnt = cnt + 1;
  return 0;
}

int main()
{
  pthread_t t2561;
  pthread_create(&t2561, NULL, P0, NULL);
  pthread_t t2562;
  pthread_create(&t2562, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main_tmp_guard0 = cnt == 2;
  __VERIFIER_atomic_end();
  if (main_tmp_guard0 == 0) {
	  abort();
  }

//   __VERIFIER_atomic_begin();
//   x = w_buff0_used && r_b0t0 ? w_buff0 : (w_buff1_used && r_b1t0 ? w_buff1 : x);
//   w_buff0_used = w_buff0_used && r_b0t0 ? FALSE : w_buff0_used;
//   __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  main_tmp_guard1 = !(y == 2 && p0_EAX == 0);
  __VERIFIER_atomic_end();

  if (main_tmp_guard1 == 0) {
ERROR:reach_error();
  }

  return 0;
}

