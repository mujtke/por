extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
extern void assert(int);
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }

// The pthread relative.
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

// Assertions.
extern void abort(void);
extern void reach_error();

// Atomic block.
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


int __unbuffered_cnt = 0;


int __unbuffered_p1_EAX = 0;


int __unbuffered_p1_EBX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x = 0;

int y = 0;


_Bool weak$$choice0;


_Bool weak$$choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}


void * P1(void *arg)
{

  __VERIFIER_atomic_begin();
  x = x;
  __unbuffered_p1_EAX = x;
  __unbuffered_p1_EBX = y;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}


int main()
{
  pthread_t t597;
  pthread_create(&t597, NULL, P0, NULL);
  pthread_t t598;
  pthread_create(&t598, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (!main$tmp_guard0) abort();
  x = x;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  main$tmp_guard1 = !(x == 2 && __unbuffered_p1_EAX == 2 && __unbuffered_p1_EBX == 0);
  if (!main$tmp_guard1) 
	  ERROR: reach_error();
  __VERIFIER_atomic_end();
  return 0;
}
