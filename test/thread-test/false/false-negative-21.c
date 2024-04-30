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


int cnt;


int cnt = 0;


int p1_EAX;


int p1_EAX = 0;


int p1_EBX;


int p1_EBX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;


int z;


int z = 0;


_Bool flush_delayed;


int mem_tmp;


_Bool r_buff0_thd0;


_Bool r_buff0_thd1;


_Bool r_buff0_thd2;


_Bool r_buff1_thd0;


_Bool r_buff1_thd1;


_Bool r_buff1_thd2;


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
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
//   __VERIFIER_assert(!(w_buff1_used && w_buff0_used));
//   r_buff1_thd0 = r_buff0_thd0;
//   r_buff1_thd1 = r_buff0_thd1;
//   r_buff1_thd2 = r_buff0_thd2;
//   r_buff0_thd1 = TRUE;
  x = 1;
  __VERIFIER_atomic_end();

//   x = 1;

  __VERIFIER_atomic_begin();
//   z = w_buff0_used && r_buff0_thd1 ? w_buff0 : (w_buff1_used && r_buff1_thd1 ? w_buff1 : z);
//   w_buff0_used = w_buff0_used && r_buff0_thd1 ? FALSE : w_buff0_used;
  w_buff0_used = w_buff0_used;
//   w_buff1_used = w_buff0_used && r_buff0_thd1 || w_buff1_used && r_buff1_thd1 ? FALSE : w_buff1_used;
  w_buff1_used = w_buff1_used;
//   r_buff0_thd1 = w_buff0_used && r_buff0_thd1 ? FALSE : r_buff0_thd1;
//   r_buff1_thd1 = w_buff0_used && r_buff0_thd1 || w_buff1_used && r_buff1_thd1 ? FALSE : r_buff1_thd1;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();

//   cnt = cnt + 1;

  return 0;
}



void * P1(void *arg)
{
//   x = 2;
//   y = 1;
//   p1_EAX = y;

  __VERIFIER_atomic_begin();
  x = 2;
  y = 1;
  p1_EAX = y;
//   weak$$choice0 = __VERIFIER_nondet_bool();
//   weak$$choice2 = __VERIFIER_nondet_bool();
//   flush_delayed = weak$$choice2;
//   mem_tmp = z;
//   z = !w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? z : (w_buff0_used && r_buff0_thd2 ? w_buff0 : w_buff1);
//   w_buff0 = weak$$choice2 ? w_buff0 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff0 : (w_buff0_used && r_buff0_thd2 ? w_buff0 : w_buff0));
  w_buff0 = w_buff0;
//   w_buff1 = weak$$choice2 ? w_buff1 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff1 : (w_buff0_used && r_buff0_thd2 ? w_buff1 : w_buff1));
//   w_buff0_used = weak$$choice2 ? w_buff0_used : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff0_used : (w_buff0_used && r_buff0_thd2 ? FALSE : w_buff0_used));
//   w_buff1_used = weak$$choice2 ? w_buff1_used : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff1_used : (w_buff0_used && r_buff0_thd2 ? FALSE : FALSE));
//   r_buff0_thd2 = weak$$choice2 ? r_buff0_thd2 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? r_buff0_thd2 : (w_buff0_used && r_buff0_thd2 ? FALSE : r_buff0_thd2));
//   r_buff1_thd2 = weak$$choice2 ? r_buff1_thd2 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? r_buff1_thd2 : (w_buff0_used && r_buff0_thd2 ? FALSE : FALSE));
  p1_EBX = z;
//   z = flush_delayed ? mem_tmp : z;
//   flush_delayed = FALSE;
  __VERIFIER_atomic_end();

//   __VERIFIER_atomic_begin();
//   z = w_buff0_used && r_buff0_thd2 ? w_buff0 : (w_buff1_used && r_buff1_thd2 ? w_buff1 : z);
//   w_buff0_used = w_buff0_used && r_buff0_thd2 ? FALSE : w_buff0_used;
//   w_buff1_used = w_buff0_used && r_buff0_thd2 || w_buff1_used && r_buff1_thd2 ? FALSE : w_buff1_used;
//   r_buff0_thd2 = w_buff0_used && r_buff0_thd2 ? FALSE : r_buff0_thd2;
//   r_buff1_thd2 = w_buff0_used && r_buff0_thd2 || w_buff1_used && r_buff1_thd2 ? FALSE : r_buff1_thd2;
//   __VERIFIER_atomic_end();

//   __VERIFIER_atomic_begin();
  cnt = cnt + 1;
//   __VERIFIER_atomic_end();

  return 0;
}


int main()
{
  pthread_t t1089;
  pthread_create(&t1089, NULL, P0, NULL);
  pthread_t t1090;
  pthread_create(&t1090, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = cnt == 2;
  __VERIFIER_atomic_end();
//   assume_abort_if_not(main$tmp_guard0);
  if (main$tmp_guard0)
	  abort();

  __VERIFIER_atomic_begin();
//   z = w_buff0_used && r_buff0_thd0 ? w_buff0 : (w_buff1_used && r_buff1_thd0 ? w_buff1 : z);
//   w_buff0_used = w_buff0_used && r_buff0_thd0 ? FALSE : w_buff0_used;
  w_buff0_used = w_buff0_used ? FALSE : w_buff0_used;
//   w_buff1_used = w_buff0_used && r_buff0_thd0 || w_buff1_used && r_buff1_thd0 ? FALSE : w_buff1_used;
  w_buff1_used = w_buff1_used;
//   r_buff0_thd0 = w_buff0_used && r_buff0_thd0 ? FALSE : r_buff0_thd0;
//   r_buff1_thd0 = w_buff0_used && r_buff0_thd0 || w_buff1_used && r_buff1_thd0 ? FALSE : r_buff1_thd0;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
//   main$tmp_guard1 = !(x == 2 && p1_EAX == 1 && p1_EBX == 0);
  main$tmp_guard1 = !(x == 2 && p1_EAX == 1);
  __VERIFIER_atomic_end();

//   __VERIFIER_assert(main$tmp_guard1);
  if (!main$tmp_guard1)
	  ERROR: reach_error();

  return 0;
}

