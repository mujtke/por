# 0 "revisit-1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "revisit-1.c"

extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



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

    return ((void *) 0);
}

int main() {

    pthread_t t0, t1;
    pthread_create(&t0, ((void *) 0), thread1, ((void *) 0));

    __VERIFIER_atomic_begin();
    X = 1;
    Y = 1;
    __VERIFIER_atomic_end();

 if (X == 5) reach_error();

    return 0;
}
