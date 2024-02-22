# 0 "fib_unsafe-1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "fib_unsafe-1.c"
# 13 "fib_unsafe-1.c"
# 1 "/usr/include/assert.h" 1 3 4
# 35 "/usr/include/assert.h" 3 4
# 1 "/usr/include/features.h" 1 3 4
# 392 "/usr/include/features.h" 3 4
# 1 "/usr/include/features-time64.h" 1 3 4
# 20 "/usr/include/features-time64.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/wordsize.h" 1 3 4
# 21 "/usr/include/features-time64.h" 2 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/timesize.h" 1 3 4
# 19 "/usr/include/x86_64-linux-gnu/bits/timesize.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/wordsize.h" 1 3 4
# 20 "/usr/include/x86_64-linux-gnu/bits/timesize.h" 2 3 4
# 22 "/usr/include/features-time64.h" 2 3 4
# 393 "/usr/include/features.h" 2 3 4
# 486 "/usr/include/features.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 1 3 4
# 559 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/wordsize.h" 1 3 4
# 560 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 2 3 4
# 1 "/usr/include/x86_64-linux-gnu/bits/long-double.h" 1 3 4
# 561 "/usr/include/x86_64-linux-gnu/sys/cdefs.h" 2 3 4
# 487 "/usr/include/features.h" 2 3 4
# 510 "/usr/include/features.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 1 3 4
# 10 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 3 4
# 1 "/usr/include/x86_64-linux-gnu/gnu/stubs-64.h" 1 3 4
# 11 "/usr/include/x86_64-linux-gnu/gnu/stubs.h" 2 3 4
# 511 "/usr/include/features.h" 2 3 4
# 36 "/usr/include/assert.h" 2 3 4
# 66 "/usr/include/assert.h" 3 4




# 69 "/usr/include/assert.h" 3 4
extern void __assert_fail (const char *__assertion, const char *__file,
      unsigned int __line, const char *__function)
     __attribute__ ((__nothrow__ , __leaf__)) __attribute__ ((__noreturn__));


extern void __assert_perror_fail (int __errnum, const char *__file,
      unsigned int __line, const char *__function)
     __attribute__ ((__nothrow__ , __leaf__)) __attribute__ ((__noreturn__));




extern void __assert (const char *__assertion, const char *__file, int __line)
     __attribute__ ((__nothrow__ , __leaf__)) __attribute__ ((__noreturn__));



# 14 "fib_unsafe-1.c" 2



# 16 "fib_unsafe-1.c"
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int i, j;

extern void __VERIFIER_atomic_begin(void);
extern void __VERIFIER_atomic_end(void);

int p, q;
void *t1(void *arg) {


  for (p = 0; p < 1; p++) {
    __VERIFIER_atomic_begin();
    i = i + j;
    __VERIFIER_atomic_end();
  }


  return ((void *) 0);
}

void *t2(void *arg) {


  for (q = 0; q < 1; q++) {
    __VERIFIER_atomic_begin();
    j = j + i;
    __VERIFIER_atomic_end();
  }


  return ((void *) 0);
}

int cur = 1, prev = 0, next = 0;
int x;
int fib() {
  for (x = 0; x < 4; x++) {
    next = prev + cur;
    prev = cur;
    cur = next;
  }
  return prev;
}

int main(int argc, char **argv) {
  pthread_t id1, id2;



  __VERIFIER_atomic_begin();
  i = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  j = 1;
  __VERIFIER_atomic_end();

  pthread_create(&id1, ((void *) 0), t1, ((void *) 0));
  pthread_create(&id2, ((void *) 0), t2, ((void *) 0));

  int correct = fib();
  
# 78 "fib_unsafe-1.c" 3 4
 ((void) sizeof ((
# 78 "fib_unsafe-1.c"
 i <= correct && j <= correct
# 78 "fib_unsafe-1.c" 3 4
 ) ? 1 : 0), __extension__ ({ if (
# 78 "fib_unsafe-1.c"
 i <= correct && j <= correct
# 78 "fib_unsafe-1.c" 3 4
 ) ; else __assert_fail (
# 78 "fib_unsafe-1.c"
 "i <= correct && j <= correct"
# 78 "fib_unsafe-1.c" 3 4
 , "fib_unsafe-1.c", 78, __extension__ __PRETTY_FUNCTION__); }))
# 78 "fib_unsafe-1.c"
                                     ;

  return 0;
}
