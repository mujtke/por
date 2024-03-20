/***********************************************************************************
                         C-DAC Tech Workshop : hyPACK-2013
                             October 15-18,2013
  Example     : pthread-demo-datarace.c

  Objective   : Write Pthread code to illustrate Data Race Condition
            and its solution using MUTEX.

  Input       : Nothing.

  Output      : Value of Global variable with and without using Mutex.

  Created     :MAY-2013

  E-mail      : hpcfte@cdac.in

****************************************************************************/
// #include <pthraed.h>
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern int pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern int pthread_join(pthread_t , void *);
extern void pthread_mutex_destroy(pthread_mutex_t *);
extern void printf(char *);
extern void printf_int(char *, int);
extern void exit(int);

enum {
	PTHREAD_MUTEX_INITIALIZER,
};

// #include <assert.h>
extern void assert(int);
extern void abort(void);

/*
Modifications are made to remove non-standard libary dependencies by Yihao from
VSL of University of Delaware.
 */
extern void abort(void);
// #include <assert.h>
void reach_error() { assert(0); }
void __VERIFIER_assert(int cond) { if (!(cond)) { ERROR: {reach_error();abort();} } return; }
// #include <pthread.h>
// #include <stdlib.h>
// #include <stdio.h>

int myglobal;                                        // declaration of global variable
pthread_mutex_t mymutex = PTHREAD_MUTEX_INITIALIZER;  // initialization of MUTEX variable


void *thread_function_datarace(void *arg)       // Function which operates on myglobal without using mutex
{
    int i,j;
    for ( i=0; i<20; i++ )
    {
        j=myglobal;
        j=j+1;
        // printf("\nIn thread_function_datarace..\t");    // Incrementing j and assign myglobal value of j
//          fflush(stdout);
        myglobal=j;
    }
    return NULL;
}

int main(void)
{
    pthread_t mythread;
    int i;

    if ( pthread_create( &mythread, NULL, thread_function_datarace, NULL) )   // calling thread_function_datarace
    {
      exit(-1);
    }

    // printf("\n\t\t---------------------------------------------------------------------------");
    // printf("\n\t\t Centre for Development of Advanced Computing (C-DAC)");
    // printf("\n\t\t Email : hpcfte@cdac.in");
    // printf("\n\t\t---------------------------------------------------------------------------");
    // printf("\n\t\t Objective : Pthread code to illustrate data race condition and its solution \n ");
    // printf("\n\t\t..........................................................................\n");

    for ( i=0; i<20; i++)
    {
        myglobal=myglobal+1;
    //      fflush(stdout);
    }

    if ( pthread_join ( mythread, NULL ) )
    {
      exit(-1);
    }
    __VERIFIER_assert(myglobal != 40);
    // printf_int("\nValue of myglobal in thread_function_datarace is :  %d\n",myglobal);
    // printf("\n ----------------------------------------------------------------------------------------------------\n");

    exit(0);
}
