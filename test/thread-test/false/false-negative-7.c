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

typedef unsigned int pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

void * P0(void *arg);


void * P1(void *arg);

int __unbuffered_cnt;

int __unbuffered_cnt = 0;


// _Bool main$tmp_guard0;
_Bool guard0;


// _Bool main$tmp_guard1;
_Bool guard1;


int x;


int x = 0;


// _Bool x$r_buff0_thd0;
_Bool A;


// _Bool x$r_buff0_thd1;
_Bool B;


// _Bool x$r_buff0_thd2;
_Bool C;



// _Bool x$r_buff1_thd0;
_Bool D;


// _Bool r_buff1_thd1;
_Bool E;


// _Bool x$r_buff1_thd2;
_Bool F;


// int x$w_buff0;
_Bool G;

// _Bool x$w_buff0_used;
_Bool H;


// int x$w_buff1;
int I;


// _Bool x$w_buff1_used;
_Bool J;


int y;


int y = 0;

_Bool weak$$choice0;
_Bool weak$$choice2;

void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 2;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = H && B ? G : (J && E ? I : x);
  H = __VERIFIER_nondet_bool();
  J = __VERIFIER_nondet_bool();
  B = __VERIFIER_nondet_bool();
  E = __VERIFIER_nondet_bool();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  I = G;
  G = 2;
  J = H;
  H = TRUE;
  __VERIFIER_assert(!(J && H));
  D = A;
  E = B;
  F = C;
  C = TRUE;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  y = 1;
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  x = H && C ? G : (J && F ? I : x);
  H = __VERIFIER_nondet_bool();
  J = __VERIFIER_nondet_bool();
  C = __VERIFIER_nondet_bool();
  F = __VERIFIER_nondet_bool();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}


int main()
{
  pthread_t t2475;
  pthread_create(&t2475, NULL, P0, NULL);
  pthread_t t2476;
  pthread_create(&t2476, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  guard0 = __unbuffered_cnt == 2;
  __VERIFIER_atomic_end();

  assume_abort_if_not(guard0);

  __VERIFIER_atomic_begin();
  H = __VERIFIER_nondet_bool();
  J = __VERIFIER_nondet_bool();
  A = __VERIFIER_nondet_bool();
  D = __VERIFIER_nondet_bool();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  weak$$choice0 = __VERIFIER_nondet_bool();
  weak$$choice2 = __VERIFIER_nondet_bool();
  x = !H || !A && !J || !A && !D ? x : (H && A ? G : I);
  G = __VERIFIER_nondet_bool();
  I = __VERIFIER_nondet_bool();
  H = __VERIFIER_nondet_bool();
  J = __VERIFIER_nondet_bool();
  A = __VERIFIER_nondet_bool();
  D = __VERIFIER_nondet_bool();
  guard1 = !(x == 2 && y == 2);
  __VERIFIER_atomic_end();

  __VERIFIER_assert(guard1);
  return 0;
}

