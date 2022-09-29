// #include <pthread.h>
typedef unsigned long pthread_t;
typedef unsigned long pthread_attr_t;

extern int pthread_create (pthread_t *__restrict __newthread,
      const pthread_attr_t *__restrict __attr,
      void *(*__start_routine) (void *),
      void *__restrict __arg) __attribute__ ((__nothrow__)) __attribute__ ((__nonnull__ (1, 3)));

pthread_t t1, t2, t3, t4;

void *thread1(void *arg) {
	int y = 1;
}

void *thread2(void *arg) {
	int z = 2;
}

void main() {
	pthread_create(&t1, ((void *)0), thread1, ((void *)0));
	pthread_create(&t2, ((void *)0), thread2, ((void *)0));
	int m = 3;

	return;
}
