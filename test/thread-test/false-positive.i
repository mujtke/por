# 1 "false-positive.c"
# 1 "<built-in>" 1
# 1 "<built-in>" 3
# 418 "<built-in>" 3
# 1 "<command line>" 1
# 1 "<built-in>" 2
# 1 "false-positive.c" 2
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern void abort(void);

extern void assert(int);
void reach_error() { assert(0); }
extern void __VERIFIER_atomic_begin(void);
extern void __VERIFIER_atomic_end(void);


typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);



extern void assert(int);
extern void abort(void);





int w=0, r=0, x, y;

void __VERIFIER_atomic_w()
{


    w = 1;
}

void* thr1(void* arg) {

  w = 1;
  x = 3;
  __VERIFIER_atomic_begin();
  w = 0;
  __VERIFIER_atomic_end();

  return 0;
}

void __VERIFIER_atomic_r()
{
    assume_abort_if_not(w==0);
    r = r+1;
}

void* thr2(void* arg) {

  r = r + 1;
  __VERIFIER_atomic_begin();
  int l = x;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  y = l;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  int ly = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  int lx = x;
  __VERIFIER_atomic_end();
  { if(!(ly == lx)) { ERROR: {reach_error();abort();}(void)0; } };
  __VERIFIER_atomic_begin();
  int lr = r;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  r = lr-1;
  __VERIFIER_atomic_end();

  return 0;
}

int main()
{
  pthread_t t;

  pthread_create(&t, 0, thr1, 0);
  thr2(0);

  return 0;
}
