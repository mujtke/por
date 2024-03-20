#include <assert.h>

#ifndef NULL
#define NULL ((void*)0)
#endif

// tag-#anon#UN[ARR4{S8}'__size'|S32'__align']
// file /usr/include/x86_64-linux-gnu/bits/pthreadtypes.h line 32
union anonymous$0;

// tag-#anon#UN[SYM__pthread_mutex_s#0={ST[S32'__lock'|U32'__count'|S32'__owner'|U32'__nusers'|S32'__kind'|S16'__spins'|S16'__elision'|SYM__pthread_internal_list#1={ST[*{SYM__pthread_internal_list#1}'__prev'|*{SYM__pthread_internal_list#1}'__next']}'__list']}'__data'|ARR40{S8}'__size'|S64'__align']
// file /usr/include/x86_64-linux-gnu/bits/pthreadtypes.h line 67
union anonymous;

// tag-__pthread_internal_list
// file /usr/include/x86_64-linux-gnu/bits/thread-shared-types.h line 51
struct __pthread_internal_list;

// tag-__pthread_mutex_s
// file /usr/include/x86_64-linux-gnu/bits/struct_mutex.h line 22
struct __pthread_mutex_s;

// tag-pthread_attr_t
// file /usr/include/x86_64-linux-gnu/bits/pthreadtypes.h line 56
union pthread_attr_t;


typedef union pthread_attr_t pthread_attr_t;
typedef union anonymous pthread_mutex_t;
typedef union anonymous$0 pthread_mutexattr_t;
typedef unsigned long int pthread_t;

// abort
// file qrcu-1.c line 1
extern void abort(void);
// assume_abort_if_not
// file qrcu-1.c line 2
void assume_abort_if_not(signed int cond);
// pthread_create
// file /usr/include/pthread.h line 202
extern signed int pthread_create(pthread_t *, const pthread_attr_t *, void * (*)(void *), void *);
// pthread_join
// file /usr/include/pthread.h line 219
extern signed int pthread_join(pthread_t, void **);
// pthread_mutex_destroy
// file /usr/include/pthread.h line 786
extern signed int pthread_mutex_destroy(pthread_mutex_t *);
// pthread_mutex_init
// file /usr/include/pthread.h line 781
extern signed int pthread_mutex_init(pthread_mutex_t *, const pthread_mutexattr_t *);
// pthread_mutex_lock
// file /usr/include/pthread.h line 794
extern signed int pthread_mutex_lock(pthread_mutex_t *);
// pthread_mutex_unlock
// file /usr/include/pthread.h line 835
extern signed int pthread_mutex_unlock(pthread_mutex_t *);
// qrcu_reader1
// file qrcu-1.c line 80
void * qrcu_reader1(void *arg);
// qrcu_reader2
// file qrcu-1.c line 102
void * qrcu_reader2(void *arg);
// qrcu_updater
// file qrcu-1.c line 124
void * qrcu_updater(void *arg);
// reach_error
// file qrcu-1.c line 7
void reach_error(void);

union anonymous$0
{
  // __size
  char __size[4l];
  // __align
  signed int __align;
};

typedef struct __pthread_internal_list
{
  // __prev
  struct __pthread_internal_list *__prev;
  // __next
  struct __pthread_internal_list *__next;
} __pthread_list_t;

struct __pthread_mutex_s
{
  // __lock
  signed int __lock;
  // __count
  unsigned int __count;
  // __owner
  signed int __owner;
  // __nusers
  unsigned int __nusers;
  // __kind
  signed int __kind;
  // __spins
  signed short int __spins;
  // __elision
  signed short int __elision;
  // __list
  __pthread_list_t __list;
};

union anonymous
{
  // __data
  struct __pthread_mutex_s __data;
  // __size
  char __size[40l];
  // __align
  signed long int __align;
};

union pthread_attr_t
{
  // __size
  char __size[56l];
  // __align
  signed long int __align;
};


// ctr1
// file qrcu-1.c line 25
signed int ctr1=1;
// ctr2
// file qrcu-1.c line 25
signed int ctr2=0;
// idx
// file qrcu-1.c line 22
signed int idx=0;
// mutex
// file qrcu-1.c line 30
pthread_mutex_t mutex;
// readerprogress1
// file qrcu-1.c line 26
signed int readerprogress1=0;
// readerprogress2
// file qrcu-1.c line 26
signed int readerprogress2=0;

// assume_abort_if_not
// file qrcu-1.c line 2
void assume_abort_if_not(signed int cond)
{
  if(cond == 0)
    abort();

}

// main
// file qrcu-1.c line 145
signed int main(void)
{
  pthread_t t1;
  pthread_t t2;
  pthread_t t3;
  pthread_mutex_init(&mutex, ((const pthread_mutexattr_t *)NULL));
  pthread_create(&t1, ((const pthread_attr_t *)NULL), qrcu_reader1, NULL);
  pthread_create(&t2, ((const pthread_attr_t *)NULL), qrcu_reader2, NULL);
  pthread_create(&t3, ((const pthread_attr_t *)NULL), qrcu_updater, NULL);
  pthread_join(t1, ((void **)NULL));
  pthread_join(t2, ((void **)NULL));
  pthread_join(t3, ((void **)NULL));
  pthread_mutex_destroy(&mutex);
  return 0;
}

// qrcu_reader1
// file qrcu-1.c line 80
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
              /* unwinding assertion loop 0 */
              assert(!(1 != 0));
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
  return NULL;
}

// qrcu_reader2
// file qrcu-1.c line 102
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
              /* unwinding assertion loop 0 */
              assert(!(1 != 0));
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
  return NULL;
}

// qrcu_updater
// file qrcu-1.c line 124
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
                  /* unwinding assertion loop 0 */
                  assert(!(ctr1 > 0));
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
                  /* unwinding assertion loop 1 */
                  assert(!(ctr2 > 0));
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
  return NULL;
}

// reach_error
// file qrcu-1.c line 7
void reach_error(void)
{
  /* assertion 0 */
  assert(0 != 0);
}

