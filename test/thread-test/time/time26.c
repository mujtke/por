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

#ifndef TRUE
#define TRUE (_Bool)1
#endif
#ifndef FALSE
#define FALSE (_Bool)0
#endif
#ifndef NULL
#define NULL ((void*)0)
#endif


void * P0(void *arg);


void * P1(void *arg);


void * P2(void *arg);


int cnt = 0;


int x = 0;


int y = 0;


void * P0(void *arg)
{
	__VERIFIER_atomic_begin();
  cnt = cnt + 1;
	__VERIFIER_atomic_end();
}



void * P1(void *arg)
{
	__VERIFIER_atomic_begin();
  cnt = cnt + 1;
	__VERIFIER_atomic_end();
}


void * P2(void *arg)
{
  // p2_EAX = y;
  // y = 2;
  __VERIFIER_atomic_begin();
	cnt = cnt + 1;
  __VERIFIER_atomic_end();
}

void * P3(void *arg)
{
  cnt = cnt + 1;
}

int main()
{
  pthread_t t1843;
  pthread_create(&t1843, NULL, P0, NULL);
  pthread_t t1844;
  pthread_create(&t1844, NULL, P1, NULL);
  pthread_t t1845;
  pthread_create(&t1845, NULL, P2, NULL);
  pthread_t t1846;
  // pthread_create(&t1846, NULL, P3, NULL);
  __VERIFIER_atomic_begin();
	cnt = cnt + 1;
  __VERIFIER_atomic_end();
}
