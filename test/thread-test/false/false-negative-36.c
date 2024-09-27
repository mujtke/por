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
extern void abort(void);
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();


int cnt = 0;


int p0 = 0;


int p2 = 0;


int y = 0;


void *P0(void *arg)
{
  __VERIFIER_atomic_begin();
  p0 = y;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}

void *P1(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  cnt = 1;
  __VERIFIER_atomic_end();
}

void *P2(void *arg)
{
  __VERIFIER_atomic_begin();
  p2 = y;
  y = 2;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


int main()
{

  pthread_t t1828;
  pthread_create(&t1828, NULL, P0, NULL);
  pthread_t t1829;
  pthread_create(&t1829, NULL, P1, NULL);
  pthread_t t1830;
  pthread_create(&t1830, NULL, P2, NULL);


  __VERIFIER_atomic_begin();
  if (cnt != 3)
	  abort();
  if (p0 == 2 &&  p2 == 1) {
ERROR: reach_error();
  }
  __VERIFIER_atomic_end();

  return 0;
}
