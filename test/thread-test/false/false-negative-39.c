extern void abort(void);
// The pthread relative.
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

// Assertions.
extern void assert(int);
extern void abort(void);
extern void reach_error();

// Atomic block.
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
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


void * P2(void *arg);


void fence();


void isync();


void lwfence();




int cnt;


int cnt = 0;


int p1_EAX;


int p1_EAX = 0;


int p1_EBX;


int p1_EBX = 0;


int p2_EAX;


int p2_EAX = 0;


int p2_EBX;


int p2_EBX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;


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


int z;


int z = 0;


_Bool weakchoice0;


_Bool weakchoice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  z = 1;
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 2;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  p1_EAX = x;
  y = !w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? y : (w_buff0_used && r_buff0_thd2 ? w_buff0 : w_buff1);
  p1_EBX = y;
  y = y;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  w_buff1 = w_buff0;
  w_buff0 = 1;
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
  r_buff1_thd0 = r_buff0_thd0;
  r_buff1_thd1 = r_buff0_thd1;
  r_buff1_thd2 = r_buff0_thd2;
  r_buff1_thd3 = r_buff0_thd3;
  r_buff0_thd3 = TRUE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = !w_buff0_used || !r_buff0_thd3 && !w_buff1_used || !r_buff0_thd3 && !r_buff1_thd3 ? y : (w_buff0_used && r_buff0_thd3 ? w_buff0 : w_buff1);
  p2_EAX = y;
  y = y;
  p2_EBX = z;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


int main()
{
  pthread_t t156;
  pthread_create(&t156, NULL, P0, NULL);
  pthread_t t157;
  pthread_create(&t157, NULL, P1, NULL);
  pthread_t t158;
  pthread_create(&t158, NULL, P2, NULL);

  __VERIFIER_atomic_begin();
  if (cnt != 3)
	  abort();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  /* Program proven to be relaxed for X86, model checker says YES. */
  if (x == 2 && p1_EAX == 2 && p1_EBX == 0 && p2_EAX == 1 && p2_EBX == 0)
	  ERROR: reach_error();
  /* Program proven to be relaxed for X86, model checker says YES. */
  __VERIFIER_atomic_end();
}
