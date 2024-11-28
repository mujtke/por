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
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
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


void * P0(void *arg);


void * P1(void *arg);


void * P2(void *arg);


void fence();


void isync();


void lwfence();




int cnt;


int cnt = 0;


int p0_EAX;


int p0_EAX = 0;


int p0_EBX;


int p0_EBX = 0;


int p2_EAX;


int p2_EAX = 0;


_Bool main_tmp_guard0;


_Bool main_tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  p0_EAX = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  p0_EBX = x;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  y = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
}



void * P2(void *arg)
{
  __VERIFIER_atomic_begin();
  p2_EAX = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  y = 2;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  cnt = cnt + 1;
  __VERIFIER_atomic_end();
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
  main_tmp_guard0 = cnt == 3;
  __VERIFIER_atomic_end();
  if (!main_tmp_guard0) {
		abort();
	}
  __VERIFIER_atomic_begin();
  main_tmp_guard1 = !(y == 2 && p0_EAX == 2 && p0_EBX == 0 && p2_EAX == 1);
  __VERIFIER_atomic_end();
  if (!main_tmp_guard1) {
ERROR: reach_error();
	}
}
