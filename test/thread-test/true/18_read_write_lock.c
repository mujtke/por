extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern void abort(void);
// #include <assert.h>
extern void reach_error();
extern void __VERIFIER_atomic_begin(void);
extern void __VERIFIER_atomic_end(void);

// #include <pthraed.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

// #include <assert.h>
extern void assert(int);
extern void abort(void);

// #include <pthread.h>

#define assume(e) assume_abort_if_not(e)
#undef assert
#define assert(e) { if(!(e)) { ERROR: {reach_error();abort();}(void)0; } }

int w=0, r=0, x, y;

void __VERIFIER_atomic_w()
{
//     assume(w==0);
//     assume(r==0);
    if (!(w==0)) abort();
    if (!(r==0)) abort();
    w = 1;
}

void* thr1(void* arg) { //writer
  __VERIFIER_atomic_w();
  x = 3;
  __VERIFIER_atomic_begin();
  w = 0;
  __VERIFIER_atomic_end();

  return 0;
}

void __VERIFIER_atomic_r()
{
//     assume(w==0);
    if (!(w==0)) abort();
    r = r+1;
}

void* thr2(void* arg) { //reader
  __VERIFIER_atomic_r();
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
//   assert(ly == lx);
  if (ly != lx){
ERROR: reach_error();
  }
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

