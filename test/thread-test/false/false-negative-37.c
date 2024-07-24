extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
// #include <assert.h>
extern void assert(int);
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);


// #include <assert.h>
extern void assert(int);
extern void abort(void);

// #include <assert.h>
// #include <pthread.h>
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

int EA1 = 0;

int EB1 = 0;



_Bool main$tmp_guard1;

int x = 0;

int y = 0;

int z = 0;

int z$w_buff0;

void * P0(void *arg)
{
  z$w_buff0 = 1;

  z = z$w_buff0;

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 2;
  y = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  EA1 = y;
  z$w_buff0 = __VERIFIER_nondet_bool();
  EB1 = z;
  __VERIFIER_atomic_end();

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}

int main()
{
  _Bool main$tmp_guard0;
  pthread_t t1089;
  pthread_create(&t1089, NULL, P0, NULL);
  pthread_t t1090;
  pthread_create(&t1090, NULL, P1, NULL);
  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (main$tmp_guard0 == 0) abort();
  __VERIFIER_atomic_end();

//   if (main$tmp_guard0 == 0) abort();

  __VERIFIER_atomic_begin();
  main$tmp_guard1 = !(x == 2 && EA1 == 1 && EB1 == 0);
  __VERIFIER_atomic_end();

  if (main$tmp_guard1 == 0)
	  ERROR: reach_error();

  return 0;
}

