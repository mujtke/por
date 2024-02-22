# 0 "triangular-2.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "triangular-2.c"
# 11 "triangular-2.c"
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_exit(void *);

extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

extern void abort(void);
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



# 20 "triangular-2.c" 2

# 20 "triangular-2.c"
void reach_error() { 
# 20 "triangular-2.c" 3 4
                    ((void) sizeof ((
# 20 "triangular-2.c"
                    0
# 20 "triangular-2.c" 3 4
                    ) ? 1 : 0), __extension__ ({ if (
# 20 "triangular-2.c"
                    0
# 20 "triangular-2.c" 3 4
                    ) ; else __assert_fail (
# 20 "triangular-2.c"
                    "0"
# 20 "triangular-2.c" 3 4
                    , "triangular-2.c", 20, __extension__ __PRETTY_FUNCTION__); }))
# 20 "triangular-2.c"
                             ; }

int i = 3, j = 6;




void *t1(void *arg) {
  for (int k = 0; k < 2; k++) {
    __VERIFIER_atomic_begin();
    i = j + 1;
    __VERIFIER_atomic_end();
  }
  pthread_exit(((void *) 0));
}

void *t2(void *arg) {
  for (int k = 0; k < 2; k++) {
    __VERIFIER_atomic_begin();
    j = i + 1;
    __VERIFIER_atomic_end();
  }
  pthread_exit(((void *) 0));
}

int main(int argc, char **argv) {
  pthread_t id1, id2;

  pthread_create(&id1, ((void *) 0), t1, ((void *) 0));
  pthread_create(&id2, ((void *) 0), t2, ((void *) 0));

  __VERIFIER_atomic_begin();
  int condI = i >= (2*2 +6);
  __VERIFIER_atomic_end();

  __VERIFIER_atomic_begin();
  int condJ = j >= (2*2 +6);
  __VERIFIER_atomic_end();

  if (condI || condJ) {
    ERROR: {reach_error();abort();}
  }

  return 0;
}
