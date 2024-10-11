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
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

#ifndef TRUE
#define TRUE (_Bool)1
#endif
#ifndef FALSE
#define FALSE (_Bool)0
#endif

void * P0(void *arg);


void * P1(void *arg);


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
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
  r_buff1_thd0 = r_buff0_thd0;
  r_buff1_thd1 = r_buff0_thd1;
  r_buff1_thd2 = r_buff0_thd2;
  r_buff0_thd1 = TRUE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = w_buff0_used && r_buff0_thd1 ? w_buff0 : (w_buff1_used && r_buff1_thd1 ? w_buff1 : y);
  w_buff0_used = w_buff0_used && r_buff0_thd1 ? FALSE : w_buff0_used;
  w_buff1_used = w_buff0_used && r_buff0_thd1 || w_buff1_used && r_buff1_thd1 ? FALSE : w_buff1_used;
  r_buff0_thd1 = w_buff0_used && r_buff0_thd1 ? FALSE : r_buff0_thd1;
  r_buff1_thd1 = w_buff0_used && r_buff0_thd1 || w_buff1_used && r_buff1_thd1 ? FALSE : r_buff1_thd1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  p1_EAX = x;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  weak$$choice0 = __VERIFIER_nondet_bool();
  weak$$choice2 = __VERIFIER_nondet_bool();
  flush_delayed = weak$$choice2;
  mem_tmp = y;
  y = !w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? y : (w_buff0_used && r_buff0_thd2 ? w_buff0 : w_buff1);
  w_buff0 = weak$$choice2 ? w_buff0 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff0 : (w_buff0_used && r_buff0_thd2 ? w_buff0 : w_buff0));
  w_buff1 = weak$$choice2 ? w_buff1 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff1 : (w_buff0_used && r_buff0_thd2 ? w_buff1 : w_buff1));
  w_buff0_used = weak$$choice2 ? w_buff0_used : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff0_used : (w_buff0_used && r_buff0_thd2 ? FALSE : w_buff0_used));
  w_buff1_used = weak$$choice2 ? w_buff1_used : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? w_buff1_used : (w_buff0_used && r_buff0_thd2 ? FALSE : FALSE));
  r_buff0_thd2 = weak$$choice2 ? r_buff0_thd2 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? r_buff0_thd2 : (w_buff0_used && r_buff0_thd2 ? FALSE : r_buff0_thd2));
  r_buff1_thd2 = weak$$choice2 ? r_buff1_thd2 : (!w_buff0_used || !r_buff0_thd2 && !w_buff1_used || !r_buff0_thd2 && !r_buff1_thd2 ? r_buff1_thd2 : (w_buff0_used && r_buff0_thd2 ? FALSE : FALSE));
  p1_EBX = y;
  y = flush_delayed ? mem_tmp : y;
  flush_delayed = FALSE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = w_buff0_used && r_buff0_thd2 ? w_buff0 : (w_buff1_used && r_buff1_thd2 ? w_buff1 : y);
  w_buff0_used = w_buff0_used && r_buff0_thd2 ? FALSE : w_buff0_used;
  w_buff1_used = w_buff0_used && r_buff0_thd2 || w_buff1_used && r_buff1_thd2 ? FALSE : w_buff1_used;
  r_buff0_thd2 = w_buff0_used && r_buff0_thd2 ? FALSE : r_buff0_thd2;
  r_buff1_thd2 = w_buff0_used && r_buff0_thd2 || w_buff1_used && r_buff1_thd2 ? FALSE : r_buff1_thd2;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
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
	if (cnt != 2) {
		abort();
	}
  if (p1_EAX == 1 && p1_EBX == 0) {
ERROR: 
		reach_error();
	}
  __VERIFIER_atomic_end();
}

