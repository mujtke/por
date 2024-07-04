// The pthread relative.
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

// Assertions.
extern void assert(int);
extern void abort(void);
extern void reach_error();

// Atomic block.
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
extern _Bool __VERIFIER_nondet_bool(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
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

int cnt = 0;


int p0_EAX = 0;


int p0_EBX = 0;


int p2_EAX = 0;


int x = 0;


int y = 0;

void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  p0_EAX = y;
  // x = x;
  p0_EBX = x;
  __VERIFIER_atomic_end();

  cnt = cnt + 1;

  return 0;
}


void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}


void * P2(void *arg)
{

  __VERIFIER_atomic_begin();
  p2_EAX = y;
  y = 2;
  // x = x;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();

  return 0;
}

int main()
{
	__VERIFIER_atomic_begin();
  pthread_t t1828;
  pthread_create(&t1828, NULL, P0, NULL);
  pthread_t t1829;
  pthread_create(&t1829, NULL, P1, NULL);
  pthread_t t1830;
  pthread_create(&t1830, NULL, P2, NULL);
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  if (cnt != 3) 
	  abort();
  if (y == 2 && p0_EAX == 2 && p0_EBX == 0 && p2_EAX == 1)
	  ERROR: reach_error();
  __VERIFIER_atomic_end();

  return 0;
}

