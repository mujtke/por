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


void * P0(void *arg);


void * P1(void *arg);


void * P2(void *arg);



int cnt = 0;


int p1_EAX = 0;


int p1_EBX = 0;


int p2_EAX = 0;


int p2_EBX = 0;


int x = 0;


int y = 0;


int z = 0;


void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  z = 1;
  // x = 1;
  // cnt = cnt + 1;
  cnt = 2;
  __VERIFIER_atomic_end();
}


void * P1(void *arg)
{
  // __VERIFIER_atomic_begin();
  // x = 2;
  // p1_EAX = x;
  // p1_EBX = y;
  // y = 0;
  // cnt = cnt + 1;
  cnt = 1;
  // __VERIFIER_atomic_end();
}

void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  //y = 0;
  p2_EBX = z;
  cnt = 3;
  __VERIFIER_atomic_end();

  // cnt = cnt + 1;
  // cnt = 3;
}


int main()
{
  pthread_t t156;
  pthread_create(&t156, NULL, P0, NULL);
  pthread_t t157;
  pthread_create(&t157, NULL, P1, NULL);
  pthread_t t158;
  pthread_create(&t158, NULL, P2, NULL);

  // __VERIFIER_atomic_begin();
  if (cnt != 3)
	  abort();
  // __VERIFIER_atomic_end();

  // __VERIFIER_atomic_begin();
  /* Program proven to be relaxed for X86, model checker says YES. */
  if (p2_EBX == 0)
	  ERROR: reach_error();
  /* Program proven to be relaxed for X86, model checker says YES. */
  // __VERIFIER_atomic_end();
}
