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
extern _Bool __VERIFIER_nondet_bool(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
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


int cnt;


int cnt = 0;


int p0_EAX;


int p0_EAX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


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


int z;


int z = 0;


_Bool weakchoice0;


_Bool weakchoice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  z = 2;
  w_buff0_used = w_buff0_used;
  w_buff1_used = w_buff1_used;
  p0_EAX = x;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
  r_buff0_thd2 = TRUE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = 1;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 2;
  z = 1;
  __VERIFIER_atomic_end();

  cnt = cnt + 1;
}


int main()
{
  pthread_t t2513;
  pthread_create(&t2513, NULL, P0, NULL);
  pthread_t t2514;
  pthread_create(&t2514, NULL, P1, NULL);
  pthread_t t2515;
  pthread_create(&t2515, NULL, P2, NULL);

  __VERIFIER_atomic_begin();
  if (cnt != 3)
	  abort();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  /* Program was expected to be safe for X86, model checker should have said NO.
This likely is a bug in the tool chain. */
  if (y == 2 && z == 2 && p0_EAX == 0)
	  ERROR: reach_error();
  /* Program was expected to be safe for X86, model checker should have said NO.
This likely is a bug in the tool chain. */
  __VERIFIER_atomic_end();
}
