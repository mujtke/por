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


int __unbuffered_cnt = 0;


int __unbuffered_p0_EAX = 0;


int __unbuffered_p2_EAX = 0;


int x = 0;


int y = 0;


void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  __unbuffered_p0_EAX = y;

  // x = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  y = 1;
  // x = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}

void * P2(void *arg)
{
  __unbuffered_p2_EAX = y;

  __VERIFIER_atomic_begin();
  y = 2;
  // x = x;
  __unbuffered_cnt = __unbuffered_cnt + 1;
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
  if (__unbuffered_cnt != 3)
	  abort();
  if (y == 2 && __unbuffered_p0_EAX == 2 &&  __unbuffered_p2_EAX == 1) {
ERROR: reach_error();
  }
  __VERIFIER_atomic_end();

  return 0;
}
