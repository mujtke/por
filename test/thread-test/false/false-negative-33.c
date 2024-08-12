// The pthread relative.
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

// Assertions.
extern void assert(int);
extern void abort(void);
extern void reach_error();

// Atomic block.
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
extern _Bool __VERIFIER_nondet_bool(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }

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


void * P2(void *arg);


void fence();


void isync();


void lwfence();




int cnt;


int cnt = 0;


int p0_EAX;


int p0_EAX = 0;


int p0_EBX;


int p0_EBX = 0;


int p2_EAX;


int p2_EAX = 0;


_Bool guard0;


_Bool guard1;


int x;


int x = 0;


_Bool flush_delayed;


int mem_tmp;


_Bool r_buff0_thd0;


_Bool r_buff0_thd1;


_Bool r_buff0_thd2;


_Bool r_buff0_thd3;


_Bool r_buff1_thd0;


_Bool r_buff1_thd1;


_Bool r_buff1_thd2;


_Bool r_buff1_thd3;


_Bool read_delayed;


int *read_delayed_var;


int w_buff0;


_Bool w_buff0_used;


int w_buff1;


_Bool w_buff1_used;


int y;


int y = 0;


_Bool weak$$choice0;


_Bool weak$$choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  p0_EAX = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  weak$$choice0 = __VERIFIER_nondet_bool();
  weak$$choice2 = __VERIFIER_nondet_bool();
  flush_delayed = weak$$choice2;
  mem_tmp = x;
  x = !w_buff0_used || !r_buff0_thd1 && !w_buff1_used || !r_buff0_thd1 && !r_buff1_thd1 ? x : (w_buff0_used && r_buff0_thd1 ? w_buff0 : w_buff1);
  p0_EBX = x;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  x = w_buff0_used && r_buff0_thd2 ? w_buff0 : (w_buff1_used && r_buff1_thd2 ? w_buff1 : x);
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  p2_EAX = y;
  y = 2;
  x = w_buff0_used && r_buff0_thd3 ? w_buff0 : (w_buff1_used && r_buff1_thd3 ? w_buff1 : x);
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}

int main()
{
  pthread_t t1828;
  pthread_create(&t1828, NULL, P0, NULL);
  pthread_t t1829;
  pthread_create(&t1829, NULL, P1, NULL);
  pthread_t t1830;
  pthread_create(&t1830, NULL, P2, NULL);
  __VERIFIER_atomic_begin();
  guard0 = cnt == 3;
  __VERIFIER_atomic_end();
  if (!guard0) abort();

  __VERIFIER_atomic_begin();
  guard1 = !(y == 2 && p0_EAX == 2 && p0_EBX == 0 && p2_EAX == 1);
  if (!guard1)
	  ERROR: reach_error();
  __VERIFIER_atomic_end();

  return 0;
}

