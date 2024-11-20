extern void abort(void);
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
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void reach_error() { assert(0); }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

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
  x = 1;

  cnt = cnt + 1;
}



void * P1(void *arg)
{
  x = 2;

  __VERIFIER_atomic_begin();
  p1_EAX = x;
  p1_EBX = y;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


void * P2(void *arg)
{

  __VERIFIER_atomic_begin();
  y = y;
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}


int main()
{
  pthread_t t156;
  pthread_create(&t156, NULL, P0, NULL);
  pthread_t t157;
  pthread_create(&t157, NULL, P1, NULL);
  pthread_t t158;
  pthread_create(&t158, NULL, P2, NULL);

  __VERIFIER_atomic_begin();
  if (cnt != 3)
	  abort();
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  if (x == 2 && p1_EAX == 2 && p1_EBX == 0 && p2_EAX == 1 && p2_EBX == 0)
	  ERROR: reach_error();
  __VERIFIER_atomic_end();
}
