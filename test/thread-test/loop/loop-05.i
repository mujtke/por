# 0 "loop-5.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "loop-5.c"
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 1, Y = -1;

int main() {

  for (int i = 0; i < 3; i++) {
   while (X > 2 && Y < 0);
   X = X + 1;
  }
# 71 "loop-5.c"
    return 0;
}
