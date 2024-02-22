
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void reach_error();

int X = -1, Y = -1;

void *thread1(void *arg) {

  __VERIFIER_atomic_begin();
  if (X < 0) {
    if (Y < 0) {
      X = 5;
    }
  }
  __VERIFIER_atomic_end();

    return NULL;
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, NULL, thread1, NULL);

    __VERIFIER_atomic_begin();
    X = 1;
    Y = 1;
    __VERIFIER_atomic_end();

	if (X == 5) reach_error();

    return 0;
}
