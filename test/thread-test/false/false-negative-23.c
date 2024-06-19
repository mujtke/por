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

extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void reach_error() { assert(0); }
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


void fence();


void isync();


void lwfence();


int __unbuffered_cnt;


int __unbuffered_cnt = 0;


int __unbuffered_p1_EAX;


int __unbuffered_p1_EAX = 0;


int __unbuffered_p1_EBX;


int __unbuffered_p1_EBX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;


int z;


int z = 0;


_Bool z$flush_delayed;


int z$mem_tmp;


_Bool z$r_buff0_thd0;


_Bool z$r_buff0_thd1;


_Bool z$r_buff0_thd2;


_Bool z$r_buff1_thd0;


_Bool z$r_buff1_thd1;


_Bool z$r_buff1_thd2;


int z$w_buff0;


_Bool z$w_buff0_used;


int z$w_buff1;


_Bool z$w_buff1_used;


void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  z$w_buff1 = z$w_buff0;
  z$w_buff0 = 1;
  z$w_buff1_used = z$w_buff0_used;
  z$w_buff0_used = TRUE;
  z$r_buff0_thd1 = TRUE;
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  z = z$w_buff0_used && z$r_buff0_thd1 ? z$w_buff0 : (z$w_buff1_used && z$r_buff1_thd1 ? z$w_buff1 : z);
  __VERIFIER_atomic_end();

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 2;
  y = 1;
  __unbuffered_p1_EAX = y;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  z$w_buff0_used = !z$r_buff1_thd2 ? z$w_buff0_used : (z$w_buff0_used && z$r_buff0_thd2 ? FALSE : z$w_buff0_used);
  __unbuffered_p1_EBX = z;
  __VERIFIER_atomic_end();

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}


int main()
{
  pthread_t t1089;
  pthread_create(&t1089, NULL, P0, NULL);
  pthread_t t1090;
  pthread_create(&t1090, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (!main$tmp_guard0)
	  abort();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  z$w_buff0_used = z$w_buff0_used && z$r_buff0_thd0 ? FALSE : z$w_buff0_used;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  /* Program proven to be relaxed for X86, model checker says YES. */
  main$tmp_guard1 = !(x == 2 && __unbuffered_p1_EAX == 1 && __unbuffered_p1_EBX == 0);
  __VERIFIER_atomic_end();

  /* Program proven to be relaxed for X86, model checker says YES. */
  if (!main$tmp_guard1) {
	  ERROR: reach_error();
  }
  return 0;
}

