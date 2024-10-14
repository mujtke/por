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


void * P0(void *arg);


void * P1(void *arg);


void * P2(void *arg);


int cnt = 0;


void * P0(void *arg)
{
  cnt = cnt + 1;
	// return NULL;
}



void * P1(void *arg)
{
  cnt = cnt + 1;
	// return NULL;
}


void * P2(void *arg)
{
  cnt = cnt + 1;
	// return NULL;
}


int main()
{
  pthread_t t1843;
  pthread_create(&t1843, NULL, P0, NULL);
  pthread_t t1844;
  pthread_create(&t1844, NULL, P1, NULL);
  pthread_t t1845;
  pthread_create(&t1845, NULL, P2, NULL);
  __VERIFIER_atomic_begin();
  // if (cnt != 3) abort();
  // if (y == 2 && p0_EAX == 2 && p0_EBX == 0 && p2_EAX == 1) {
// ERROR: reach_error();
	// }
  __VERIFIER_atomic_end();
	// return 0;
}
