# 0 "branch-4.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "branch-4.c"
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();
void reach_error();



typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

void reach_error() {
ERROR:
 return;
}

int X = 1, Y = -1;
int z, m = 1, n, p, q, r, s = 1;

void *thread1(void *arg) {

 __VERIFIER_atomic_begin();

 z = !m || !n && !s || !n && !p ? z : (m && n ? q : r);


 __VERIFIER_atomic_end();

 if (X > 0) reach_error();

    return ((void *) 0);
}

int main() {

    pthread_t t0;
    pthread_create(&t0, ((void *) 0), thread1, ((void *) 0));

 if (z > 0) {
  X = -1;
 }

    return 0;
}
