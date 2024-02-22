# 0 "unwind-6-qrcu-1-m.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "unwind-6-qrcu-1-m.c"
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



# 2 "unwind-6-qrcu-1-m.c" 2








# 9 "unwind-6-qrcu-1-m.c"
union anonymous$0;



union anonymous;



struct __pthread_internal_list;



struct __pthread_mutex_s;



union pthread_attr_t;


typedef union pthread_attr_t pthread_attr_t;
typedef union anonymous pthread_mutex_t;
typedef union anonymous$0 pthread_mutexattr_t;
typedef unsigned long int pthread_t;



extern void abort(void);


void assume_abort_if_not(signed int cond);


extern signed int pthread_create(pthread_t *, const pthread_attr_t *, void * (*)(void *), void *);


extern signed int pthread_join(pthread_t, void **);


extern signed int pthread_mutex_destroy(pthread_mutex_t *);


extern signed int pthread_mutex_init(pthread_mutex_t *, const pthread_mutexattr_t *);


extern signed int pthread_mutex_lock(pthread_mutex_t *);


extern signed int pthread_mutex_unlock(pthread_mutex_t *);


void * qrcu_reader1(void *arg);


void * qrcu_reader2(void *arg);


void * qrcu_updater(void *arg);


void reach_error(void);

union anonymous$0
{

  char __size[4l];

  signed int __align;
};

typedef struct __pthread_internal_list
{

  struct __pthread_internal_list *__prev;

  struct __pthread_internal_list *__next;
} __pthread_list_t;

struct __pthread_mutex_s
{

  signed int __lock;

  unsigned int __count;

  signed int __owner;

  unsigned int __nusers;

  signed int __kind;

  signed short int __spins;

  signed short int __elision;

  __pthread_list_t __list;
};

union anonymous
{

  struct __pthread_mutex_s __data;

  char __size[40l];

  signed long int __align;
};

union pthread_attr_t
{

  char __size[56l];

  signed long int __align;
};




signed int ctr1=1;


signed int ctr2=0;


signed int idx=0;


pthread_mutex_t mutex;


signed int readerprogress1=0;


signed int readerprogress2=0;



void assume_abort_if_not(signed int cond)
{
  if(cond == 0)
    abort();

}



signed int main(void)
{
  pthread_t t1;
  pthread_t t2;
  pthread_t t3;
  pthread_mutex_init(&mutex, ((const pthread_mutexattr_t *)((void*)0)));
  pthread_create(&t1, ((const pthread_attr_t *)((void*)0)), qrcu_reader1, ((void*)0));
  pthread_create(&t2, ((const pthread_attr_t *)((void*)0)), qrcu_reader2, ((void*)0));
  pthread_create(&t3, ((const pthread_attr_t *)((void*)0)), qrcu_updater, ((void*)0));
  pthread_join(t1, ((void **)((void*)0)));
  pthread_join(t2, ((void **)((void*)0)));
  pthread_join(t3, ((void **)((void*)0)));
  pthread_mutex_destroy(&mutex);
  return 0;
}



void * qrcu_reader1(void *arg)
{
  signed int myidx;
  signed int return_value___VERIFIER_nondet_int;
  {
    myidx = idx;
    signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
    if(!(return_value___VERIFIER_nondet_int$0 == 0))
    {
      __VERIFIER_atomic_begin(myidx);
      goto __CPROVER_DUMP_L19;
    }

    else
    {
      return_value___VERIFIER_nondet_int = nondet_signed_int();
      if(!(return_value___VERIFIER_nondet_int == 0))
      {
        __VERIFIER_atomic_begin(myidx);
        goto __CPROVER_DUMP_L19;
      }

    }
    {
      myidx = idx;
      signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
      if(!(return_value___VERIFIER_nondet_int$0 == 0))
      {
        __VERIFIER_atomic_begin(myidx);
        goto __CPROVER_DUMP_L19;
      }

      else
      {
        return_value___VERIFIER_nondet_int = nondet_signed_int();
        if(!(return_value___VERIFIER_nondet_int == 0))
        {
          __VERIFIER_atomic_begin(myidx);
          goto __CPROVER_DUMP_L19;
        }

      }
      {
        myidx = idx;
        signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
        if(!(return_value___VERIFIER_nondet_int$0 == 0))
        {
          __VERIFIER_atomic_begin(myidx);
          goto __CPROVER_DUMP_L19;
        }

        else
        {
          return_value___VERIFIER_nondet_int = nondet_signed_int();
          if(!(return_value___VERIFIER_nondet_int == 0))
          {
            __VERIFIER_atomic_begin(myidx);
            goto __CPROVER_DUMP_L19;
          }

        }
        {
          myidx = idx;
          signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
          if(!(return_value___VERIFIER_nondet_int$0 == 0))
          {
            __VERIFIER_atomic_begin(myidx);
            goto __CPROVER_DUMP_L19;
          }

          else
          {
            return_value___VERIFIER_nondet_int = nondet_signed_int();
            if(!(return_value___VERIFIER_nondet_int == 0))
            {
              __VERIFIER_atomic_begin(myidx);
              goto __CPROVER_DUMP_L19;
            }

          }
          {
            myidx = idx;
            signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
            if(!(return_value___VERIFIER_nondet_int$0 == 0))
            {
              __VERIFIER_atomic_begin(myidx);
              goto __CPROVER_DUMP_L19;
            }

            else
            {
              return_value___VERIFIER_nondet_int = nondet_signed_int();
              if(!(return_value___VERIFIER_nondet_int == 0))
              {
                __VERIFIER_atomic_begin(myidx);
                goto __CPROVER_DUMP_L19;
              }

            }
            {
              myidx = idx;
              signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
              if(!(return_value___VERIFIER_nondet_int$0 == 0))
              {
                __VERIFIER_atomic_begin(myidx);
                goto __CPROVER_DUMP_L19;
              }

              else
              {
                signed int return_value___VERIFIER_nondet_int=nondet_signed_int();
                if(!(return_value___VERIFIER_nondet_int == 0))
                {
                  __VERIFIER_atomic_begin(myidx);
                  goto __CPROVER_DUMP_L19;
                }

              }

              
# 292 "unwind-6-qrcu-1-m.c" 3 4
             ((void) sizeof ((
# 292 "unwind-6-qrcu-1-m.c"
             !(1 != 0)
# 292 "unwind-6-qrcu-1-m.c" 3 4
             ) ? 1 : 0), __extension__ ({ if (
# 292 "unwind-6-qrcu-1-m.c"
             !(1 != 0)
# 292 "unwind-6-qrcu-1-m.c" 3 4
             ) ; else __assert_fail (
# 292 "unwind-6-qrcu-1-m.c"
             "!(1 != 0)"
# 292 "unwind-6-qrcu-1-m.c" 3 4
             , "unwind-6-qrcu-1-m.c", 292, __extension__ __PRETTY_FUNCTION__); }))
# 292 "unwind-6-qrcu-1-m.c"
                              ;
              __CPROVER_assume(!(1 != 0));
            }
          }
        }
      }
    }
  }

__CPROVER_DUMP_L19:
  ;
  readerprogress1 = 1;
  readerprogress1 = 2;
  __VERIFIER_atomic_end(myidx);
  return ((void*)0);
}



void * qrcu_reader2(void *arg)
{
  signed int myidx;
  signed int return_value___VERIFIER_nondet_int;
  {
    myidx = idx;
    signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
    if(!(return_value___VERIFIER_nondet_int$0 == 0))
    {
      __VERIFIER_atomic_begin(myidx);
      goto __CPROVER_DUMP_L19;
    }

    else
    {
      return_value___VERIFIER_nondet_int = nondet_signed_int();
      if(!(return_value___VERIFIER_nondet_int == 0))
      {
        __VERIFIER_atomic_begin(myidx);
        goto __CPROVER_DUMP_L19;
      }

    }
    {
      myidx = idx;
      signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
      if(!(return_value___VERIFIER_nondet_int$0 == 0))
      {
        __VERIFIER_atomic_begin(myidx);
        goto __CPROVER_DUMP_L19;
      }

      else
      {
        return_value___VERIFIER_nondet_int = nondet_signed_int();
        if(!(return_value___VERIFIER_nondet_int == 0))
        {
          __VERIFIER_atomic_begin(myidx);
          goto __CPROVER_DUMP_L19;
        }

      }
      {
        myidx = idx;
        signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
        if(!(return_value___VERIFIER_nondet_int$0 == 0))
        {
          __VERIFIER_atomic_begin(myidx);
          goto __CPROVER_DUMP_L19;
        }

        else
        {
          return_value___VERIFIER_nondet_int = nondet_signed_int();
          if(!(return_value___VERIFIER_nondet_int == 0))
          {
            __VERIFIER_atomic_begin(myidx);
            goto __CPROVER_DUMP_L19;
          }

        }
        {
          myidx = idx;
          signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
          if(!(return_value___VERIFIER_nondet_int$0 == 0))
          {
            __VERIFIER_atomic_begin(myidx);
            goto __CPROVER_DUMP_L19;
          }

          else
          {
            return_value___VERIFIER_nondet_int = nondet_signed_int();
            if(!(return_value___VERIFIER_nondet_int == 0))
            {
              __VERIFIER_atomic_begin(myidx);
              goto __CPROVER_DUMP_L19;
            }

          }
          {
            myidx = idx;
            signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
            if(!(return_value___VERIFIER_nondet_int$0 == 0))
            {
              __VERIFIER_atomic_begin(myidx);
              goto __CPROVER_DUMP_L19;
            }

            else
            {
              return_value___VERIFIER_nondet_int = nondet_signed_int();
              if(!(return_value___VERIFIER_nondet_int == 0))
              {
                __VERIFIER_atomic_begin(myidx);
                goto __CPROVER_DUMP_L19;
              }

            }
            {
              myidx = idx;
              signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
              if(!(return_value___VERIFIER_nondet_int$0 == 0))
              {
                __VERIFIER_atomic_begin(myidx);
                goto __CPROVER_DUMP_L19;
              }

              else
              {
                signed int return_value___VERIFIER_nondet_int=nondet_signed_int();
                if(!(return_value___VERIFIER_nondet_int == 0))
                {
                  __VERIFIER_atomic_begin(myidx);
                  goto __CPROVER_DUMP_L19;
                }

              }

              
# 430 "unwind-6-qrcu-1-m.c" 3 4
             ((void) sizeof ((
# 430 "unwind-6-qrcu-1-m.c"
             !(1 != 0)
# 430 "unwind-6-qrcu-1-m.c" 3 4
             ) ? 1 : 0), __extension__ ({ if (
# 430 "unwind-6-qrcu-1-m.c"
             !(1 != 0)
# 430 "unwind-6-qrcu-1-m.c" 3 4
             ) ; else __assert_fail (
# 430 "unwind-6-qrcu-1-m.c"
             "!(1 != 0)"
# 430 "unwind-6-qrcu-1-m.c" 3 4
             , "unwind-6-qrcu-1-m.c", 430, __extension__ __PRETTY_FUNCTION__); }))
# 430 "unwind-6-qrcu-1-m.c"
                              ;
              __CPROVER_assume(!(1 != 0));
            }
          }
        }
      }
    }
  }

__CPROVER_DUMP_L19:
  ;
  readerprogress2 = 1;
  readerprogress2 = 2;
  __VERIFIER_atomic_end(myidx);
  return ((void*)0);
}



void * qrcu_updater(void *arg)
{
  signed int i;
  signed int readerstart1;
  signed int readerstart2;
  signed int sum;
  __VERIFIER_atomic_take_snapshot(&readerstart1, &readerstart2);
  signed int return_value___VERIFIER_nondet_int=nondet_signed_int();
  if(!(return_value___VERIFIER_nondet_int == 0))
  {
    sum = ctr1;
    sum = sum + ctr2;
  }

  else
  {
    sum = ctr2;
    sum = sum + ctr1;
  }
  if(!(sum >= 2))
  {
    signed int return_value___VERIFIER_nondet_int$0=nondet_signed_int();
    if(!(return_value___VERIFIER_nondet_int$0 == 0))
    {
      sum = ctr1;
      sum = sum + ctr2;
    }

    else
    {
      sum = ctr2;
      sum = sum + ctr1;
    }
  }

  if(sum >= 2)
  {
    pthread_mutex_lock(&mutex);
    if(!(idx >= 1))
    {
      ctr2 = ctr2 + 1;
      idx = 1;
      ctr1 = ctr1 - 1;
    }

    else
    {
      ctr1 = ctr1 + 1;
      idx = 0;
      ctr2 = ctr2 - 1;
    }
    if(!(idx >= 1))
    {
      if(ctr1 >= 1)
      {
        if(ctr1 >= 1)
        {
          if(ctr1 >= 1)
          {
            if(ctr1 >= 1)
            {
              if(ctr1 >= 1)
              {
                if(ctr1 >= 1)
                {

                  
# 515 "unwind-6-qrcu-1-m.c" 3 4
                 ((void) sizeof ((
# 515 "unwind-6-qrcu-1-m.c"
                 !(ctr1 > 0)
# 515 "unwind-6-qrcu-1-m.c" 3 4
                 ) ? 1 : 0), __extension__ ({ if (
# 515 "unwind-6-qrcu-1-m.c"
                 !(ctr1 > 0)
# 515 "unwind-6-qrcu-1-m.c" 3 4
                 ) ; else __assert_fail (
# 515 "unwind-6-qrcu-1-m.c"
                 "!(ctr1 > 0)"
# 515 "unwind-6-qrcu-1-m.c" 3 4
                 , "unwind-6-qrcu-1-m.c", 515, __extension__ __PRETTY_FUNCTION__); }))
# 515 "unwind-6-qrcu-1-m.c"
                                    ;
                  __CPROVER_assume(!(ctr1 > 0));
                }

              }

            }

          }

        }

      }

    }

    else
      if(ctr2 >= 1)
      {
        if(ctr2 >= 1)
        {
          if(ctr2 >= 1)
          {
            if(ctr2 >= 1)
            {
              if(ctr2 >= 1)
              {
                if(ctr2 >= 1)
                {

                  
# 545 "unwind-6-qrcu-1-m.c" 3 4
                 ((void) sizeof ((
# 545 "unwind-6-qrcu-1-m.c"
                 !(ctr2 > 0)
# 545 "unwind-6-qrcu-1-m.c" 3 4
                 ) ? 1 : 0), __extension__ ({ if (
# 545 "unwind-6-qrcu-1-m.c"
                 !(ctr2 > 0)
# 545 "unwind-6-qrcu-1-m.c" 3 4
                 ) ; else __assert_fail (
# 545 "unwind-6-qrcu-1-m.c"
                 "!(ctr2 > 0)"
# 545 "unwind-6-qrcu-1-m.c" 3 4
                 , "unwind-6-qrcu-1-m.c", 545, __extension__ __PRETTY_FUNCTION__); }))
# 545 "unwind-6-qrcu-1-m.c"
                                    ;
                  __CPROVER_assume(!(ctr2 > 0));
                }

              }

            }

          }

        }

      }

    pthread_mutex_unlock(&mutex);
  }

  __VERIFIER_atomic_check_progress1(readerstart1);
  __VERIFIER_atomic_check_progress2(readerstart2);
  return ((void*)0);
}



void reach_error(void)
{

  
# 572 "unwind-6-qrcu-1-m.c" 3 4
 ((void) sizeof ((
# 572 "unwind-6-qrcu-1-m.c"
 0 != 0
# 572 "unwind-6-qrcu-1-m.c" 3 4
 ) ? 1 : 0), __extension__ ({ if (
# 572 "unwind-6-qrcu-1-m.c"
 0 != 0
# 572 "unwind-6-qrcu-1-m.c" 3 4
 ) ; else __assert_fail (
# 572 "unwind-6-qrcu-1-m.c"
 "0 != 0"
# 572 "unwind-6-qrcu-1-m.c" 3 4
 , "unwind-6-qrcu-1-m.c", 572, __extension__ __PRETTY_FUNCTION__); }))
# 572 "unwind-6-qrcu-1-m.c"
               ;
}
