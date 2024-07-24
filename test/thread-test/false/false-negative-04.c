extern void assert(int);
void reach_error() { assert(0); }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

void * P0(void *arg);

void * P1(void *arg);

int cnt = 0;

void * P0(void *arg)
{

  cnt = cnt + 1;

  return NULL;
}


void * P1(void *arg)
{
  cnt = cnt + 2;

  return NULL;
}

int main()
{
  pthread_t t1089;
  pthread_create(&t1089, NULL, P0, NULL);
  pthread_t t1090;
  pthread_create(&t1090, NULL, P1, NULL);

  __VERIFIER_atomic_begin();
  if (cnt == 2)
	  ERROR: reach_error();
  __VERIFIER_atomic_end();

  return 0;
}
