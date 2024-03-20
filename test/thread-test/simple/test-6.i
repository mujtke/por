# 0 "test-6.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "test-6.c"
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;


extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern int __VERIFIER_nondet_int();

extern void reach_error();

int A = -1;


void *thread1(void *arg) {

 A = __VERIFIER_nondet_int();

 return ((void *) 0);
}

int main() {

 pthread_t t1;
 pthread_create(&t1, ((void *) 0), thread1, ((void *) 0));

 if (A < 0) {
  if (A > 0) {
ERROR: reach_error();
  }
 }

 return 0;
}
