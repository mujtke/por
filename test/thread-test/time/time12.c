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

extern _Bool __VERIFIER_nondet_bool();

#define FALSE 0
#define TRUE 1

void * P0(void *arg);


void * P1(void *arg);


void * P2(void *arg);


int cnt = 0;


int p0_EAX = 0;


_Bool p0_EAX$r_buff0_thd0;


_Bool p0_EAX$r_buff0_thd1;


_Bool p0_EAX$r_buff0_thd2;


_Bool p0_EAX$r_buff0_thd3;


_Bool p0_EAX$r_buff1_thd0;


_Bool p0_EAX$r_buff1_thd1;


_Bool p0_EAX$r_buff1_thd2;


_Bool p0_EAX$r_buff1_thd3;


_Bool p0_EAX$read_delayed;


int *p0_EAX$read_delayed_var;


int p0_EAX$w_buff0;


_Bool p0_EAX$w_buff0_used;


int p0_EAX$w_buff1;


_Bool p0_EAX$w_buff1_used;


int p0_EBX;


int p0_EBX = 0;


int p2_EAX;


int p2_EAX = 0;


_Bool tmp_guard0;


_Bool tmp_guard1;


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


_Bool choice0;


_Bool choice1;


_Bool choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  choice0 = __VERIFIER_nondet_bool();
  choice2 = __VERIFIER_nondet_bool();
  flush_delayed = choice2;
  mem_tmp = z;
  choice1 = __VERIFIER_nondet_bool();
  z = !w_buff0_used ? z : (w_buff0_used && r_buff0_thd1 ? w_buff0 : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? (choice0 ? z : (choice1 ? w_buff0 : w_buff1)) : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? (choice0 ? w_buff1 : w_buff0) : (choice0 ? w_buff0 : z))));
  w_buff0 = choice2 ? w_buff0 : (!w_buff0_used ? w_buff0 : (w_buff0_used && r_buff0_thd1 ? w_buff0 : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? w_buff0 : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? w_buff0 : w_buff0))));
  w_buff1 = choice2 ? w_buff1 : (!w_buff0_used ? w_buff1 : (w_buff0_used && r_buff0_thd1 ? w_buff1 : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? w_buff1 : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? w_buff1 : w_buff1))));
  w_buff0_used = choice2 ? w_buff0_used : (!w_buff0_used ? w_buff0_used : (w_buff0_used && r_buff0_thd1 ? FALSE : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? choice0 || !choice1 : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? choice0 : choice0))));
  w_buff1_used = choice2 ? w_buff1_used : (!w_buff0_used ? w_buff1_used : (w_buff0_used && r_buff0_thd1 ? FALSE : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? choice0 : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? FALSE : FALSE))));
  r_buff0_thd1 = choice2 ? r_buff0_thd1 : (!w_buff0_used ? r_buff0_thd1 : (w_buff0_used && r_buff0_thd1 ? FALSE : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? r_buff0_thd1 : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? FALSE : FALSE))));
  r_buff1_thd1 = choice2 ? r_buff1_thd1 : (!w_buff0_used ? r_buff1_thd1 : (w_buff0_used && r_buff0_thd1 ? FALSE : (w_buff0_used && !r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? (choice0 ? r_buff1_thd1 : FALSE) : (w_buff0_used && r_buff1_thd1 && w_buff1_used && !r_buff0_thd1 ? FALSE : FALSE))));
  p0_EAX$read_delayed = TRUE;
  p0_EAX$read_delayed_var = &z;
  p0_EAX = z;
  z = flush_delayed ? mem_tmp : z;
  flush_delayed = FALSE;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  p0_EBX = x;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  // return NULL;
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  y = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
	// return NULL;
}



void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  p2_EAX = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  z = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  z = w_buff0_used && r_buff0_thd3 ? w_buff0 : (w_buff1_used && r_buff1_thd3 ? w_buff1 : z);
  w_buff0_used = w_buff0_used && r_buff0_thd3 ? FALSE : w_buff0_used;
  w_buff1_used = w_buff0_used && r_buff0_thd3 || w_buff1_used && r_buff1_thd3 ? FALSE : w_buff1_used;
  r_buff0_thd3 = w_buff0_used && r_buff0_thd3 ? FALSE : r_buff0_thd3;
  r_buff1_thd3 = w_buff0_used && r_buff0_thd3 || w_buff1_used && r_buff1_thd3 ? FALSE : r_buff1_thd3;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
	// return NULL;
}



int main()
{
  pthread_t t2049;
  pthread_create(&t2049, NULL, P0, NULL);
  pthread_t t2050;
  pthread_create(&t2050, NULL, P1, NULL);
  pthread_t t2051;
  pthread_create(&t2051, NULL, P2, NULL);
  __VERIFIER_atomic_begin();
  tmp_guard0 = cnt == 3;
  __VERIFIER_atomic_end();
  if (!tmp_guard0) {
		abort();
	}

  __VERIFIER_atomic_begin();
  z = w_buff0_used && r_buff0_thd0 ? w_buff0 : (w_buff1_used && r_buff1_thd0 ? w_buff1 : z);
  w_buff0_used = w_buff0_used && r_buff0_thd0 ? FALSE : w_buff0_used;
  w_buff1_used = w_buff0_used && r_buff0_thd0 || w_buff1_used && r_buff1_thd0 ? FALSE : w_buff1_used;
  r_buff0_thd0 = w_buff0_used && r_buff0_thd0 ? FALSE : r_buff0_thd0;
  r_buff1_thd0 = w_buff0_used && r_buff0_thd0 || w_buff1_used && r_buff1_thd0 ? FALSE : r_buff1_thd0;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
                                                                                
  choice1 = __VERIFIER_nondet_bool();
                                                                                
                                          
  p0_EAX = p0_EAX$read_delayed ? (choice1 ? *p0_EAX$read_delayed_var : p0_EAX) : p0_EAX;
                                                                                
                                          
  tmp_guard1 = !(p0_EAX == 1 && p0_EBX == 0 && p2_EAX == 1);
  __VERIFIER_atomic_end();
                                                                                
                                          
  if (!tmp_guard1)
		ERROR: reach_error();
}

