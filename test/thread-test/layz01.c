// This file is part of the SV-Benchmarks collection of verification tasks:
// https://github.com/sosy-lab/sv-benchmarks
//
// SPDX-FileCopyrightText: 2011-2020 The SV-Benchmarks community
// SPDX-FileCopyrightText: 2020 The ESBMC project
//
// SPDX-License-Identifier: Apache-2.0

extern void abort(void);
// #include <assert.h>
extern void assert(int);
void reach_error() { assert(0); }

// #include <pthread.h>
// #include <assert.h>
typedef unsigned pthread_mutex_t;
extern void pthread_mutex_lock(pthread_mutex_t *);
extern void pthread_mutex_unlock(pthread_mutex_t *);

pthread_mutex_t  mutex;
int data = 0;

void *thread1(void *arg)
{
  pthread_mutex_lock(&mutex);
  data++;
  pthread_mutex_unlock(&mutex);
  return 0;
}


void *thread2(void *arg)
{
  pthread_mutex_lock(&mutex);
  data+=2;
  pthread_mutex_unlock(&mutex);
  return 0;
}


void *thread3(void *arg)
{
  pthread_mutex_lock(&mutex);
  if (data >= 3){
    ERROR: {reach_error();abort();}
    ;
  }
  pthread_mutex_unlock(&mutex);    
  return 0;
}

typedef unsigned pthread_t;
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_create(pthread_t *, int, void *(*)(void *), int);
extern void pthread_join(pthread_t, int);

int main()
{
  pthread_mutex_init(&mutex, 0);

  pthread_t t1, t2, t3;

  pthread_create(&t1, 0, thread1, 0);
  pthread_create(&t2, 0, thread2, 0);
  pthread_create(&t3, 0, thread3, 0);

  pthread_join(t1, 0);
  pthread_join(t2, 0);
  pthread_join(t3, 0);
  
  return 0;
}
