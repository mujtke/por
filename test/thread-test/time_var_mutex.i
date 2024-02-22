# 0 "time_var_mutex.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "time_var_mutex.c"
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern void abort(void);


extern void assert(int);
void reach_error() { assert(0); }
# 20 "time_var_mutex.c"
typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);




int block;
int busy;
int inode;
pthread_mutex_t m_inode;
pthread_mutex_t m_busy;

void *allocator(void *_){
  pthread_mutex_lock(&m_inode);
  if(inode == 0){
    pthread_mutex_lock(&m_busy);
    busy = 1;
    pthread_mutex_unlock(&m_busy);
    inode = 1;
  }
  block = 1;
  if (!(block == 1)) ERROR: reach_error();
  pthread_mutex_unlock(&m_inode);
  return ((void *) 0);
}

void *de_allocator(void *_){
  pthread_mutex_lock(&m_busy);
  if(busy == 0){
    block = 0;
    if (!(block == 0)) ERROR: reach_error();
  }
  pthread_mutex_unlock(&m_busy);
  return ((void *)0);
}

int main() {
  pthread_t t1, t2;
  assume_abort_if_not(inode == busy);
  pthread_mutex_init(&m_inode, 0);
  pthread_mutex_init(&m_busy, 0);
  pthread_create(&t1, 0, allocator, 0);
  pthread_create(&t2, 0, de_allocator, 0);
  pthread_join(t1, 0);
  pthread_join(t2, 0);
  pthread_mutex_destroy(&m_inode);
  pthread_mutex_destroy(&m_busy);
  return 0;
}
