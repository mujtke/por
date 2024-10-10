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
extern _Bool __VERIFIER_nondet_bool(void);

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


int p1_EBX = 0;


int x = 0;


int y = 0;


int w_buff0;


_Bool w_buff0_used;


int w_buff1;


_Bool w_buff1_used;


void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  w_buff1 = w_buff0;
  w_buff0 = 1;
  w_buff1_used = w_buff0_used;
  w_buff0_used = TRUE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = 1;
  y = w_buff0_used ? w_buff0 : (y);
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  p1_EAX = x;
  y = !w_buff1_used ? y : (w_buff1);
  w_buff0 = w_buff0;
  p1_EBX = y;
  w_buff0_used = w_buff0_used;
  __VERIFIER_atomic_end();

  cnt = cnt + 1;
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

