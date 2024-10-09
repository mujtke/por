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


void fence();


void isync();


void lwfence();




int cnt;


int cnt = 0;


int p1_EAX;


int p1_EAX = 0;


int p1_EBX;


int p1_EBX = 0;


_Bool tmp_guard0;


_Bool tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;


_Bool flush_delayed;


int mem_tmp;


_Bool r_buff0_thd0;


_Bool r_buff0_thd1;


_Bool r_buff0_thd2;


_Bool r_buff1_thd0;


_Bool r_buff1_thd1;


_Bool r_buff1_thd2;


_Bool read_delayed;


int *read_delayed_var;


int w_buff0;


_Bool w_buff0_used;


int w_buff1;


_Bool w_buff1_used;


_Bool weak$$choice0;


_Bool weak$$choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  w_buff1 = w_buff0;
  w_buff0 = 1;
  w_buff0_used = TRUE;
  r_buff0_thd1 = TRUE;
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = w_buff0_used && r_buff0_thd1 ? w_buff0 : y;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P1(void *arg)
{
  p1_EAX = x;

  __VERIFIER_atomic_begin();
  weak$$choice2 = __VERIFIER_nondet_bool();
  y = !r_buff1_thd2 ? y : (w_buff1);
  w_buff0 = weak$$choice2 ? w_buff0 : (!r_buff1_thd2 ? w_buff0 : (w_buff0));
  p1_EBX = y;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  w_buff0_used = w_buff0_used;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


int main()
{
  pthread_t t2305;
  pthread_create(&t2305, NULL, P0, NULL);
  pthread_t t2306;
  pthread_create(&t2306, NULL, P1, NULL);
  __VERIFIER_atomic_begin();
  if (cnt != 2)
	  abort();
  if (p1_EAX == 1 && p1_EBX == 0)
	  ERROR: reach_error();
  __VERIFIER_atomic_end();
}

