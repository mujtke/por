typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;
#define NULL ((void *) 0)
extern int pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern int pthread_join(pthread_t , void *);
extern void exit(int);

enum {
	PTHREAD_MUTEX_INITIALIZER,
};

// #include <assert.h>
extern void assert(int);
extern void abort(void);

void reach_error() { assert(0); }
void __VERIFIER_assert(int cond) { if (!(cond)) { ERROR: {reach_error();abort();} } return; }

int myglobal;                                        // declaration of global variable
pthread_mutex_t mymutex = PTHREAD_MUTEX_INITIALIZER;  // initialization of MUTEX variable

int depth = 20;

void *thread_function_datarace(void *arg)       // Function which operates on myglobal without using mutex
{
    int i,j;
    for ( i=0; i<depth; i++ )
    {
        j=myglobal;
        j=j+1;
        myglobal=j;
    }
    return NULL;
}

int main(void)
{
    pthread_t mythread;
    int i;

    pthread_create( &mythread, NULL, thread_function_datarace, NULL);   // calling thread_function_datarace

    for ( i=0; i<depth; i++)
    {
        myglobal=myglobal+1;
    }

    pthread_join ( mythread, NULL );
    __VERIFIER_assert(myglobal != 2 * depth);

    exit(0);
}
