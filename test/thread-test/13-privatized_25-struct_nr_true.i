# 1 "13-privatized_25-struct_nr_true.c"
# 1 "<built-in>" 1
# 1 "<built-in>" 3
# 418 "<built-in>" 3
# 1 "<command line>" 1
# 1 "<built-in>" 2
# 1 "13-privatized_25-struct_nr_true.c" 2







typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);


extern void assert(int);
extern void abort(void);
enum {
 PTHREAD_MUTEX_INITIALIZER
};


extern void abort(void);
void reach_error() { assert(0); }
void __VERIFIER_assert(int cond) { if(!(cond)) { ERROR: {reach_error();abort();} } }





struct lock {
  pthread_mutex_t mutex;
};

int glob1 = 5;
struct lock lock1 = {.mutex = PTHREAD_MUTEX_INITIALIZER};


void *t_fun(void *arg) {
  int t;
  pthread_mutex_lock(&lock1.mutex);
  t = glob1;
  __VERIFIER_assert(t == 5);
  glob1 = -10;
  __VERIFIER_assert(glob1 == -10);
  glob1 = t;
  pthread_mutex_unlock(&lock1.mutex);
  return ((void *) 0);
}

int main(void) {
  pthread_t id;
  __VERIFIER_assert(glob1 == 5);
  pthread_create(&id, ((void *) 0), t_fun, ((void *) 0));
  pthread_mutex_lock(&lock1.mutex);
  glob1++;
  __VERIFIER_assert(glob1 == 6);
  glob1--;
  pthread_mutex_unlock(&lock1.mutex);
  pthread_join (id, ((void *) 0));
  return 0;
}
