// #include <pthread.h>
typedef unsigned long pthread_t;
typedef unsigned long pthread_attr_t;

extern int pthread_create (pthread_t *__restrict __newthread,
      const pthread_attr_t *__restrict __attr,
      void *(*__start_routine) (void *),
      void *__restrict __arg) __attribute__ ((__nothrow__)) __attribute__ ((__nonnull__ (1, 3)));

int x = 5, y = 0, z = 0, m = 0;

pthread_t t1, t2, t3, t4;

void *thread1(void *arg) {
	x = 1;
}

void *thread2(void *arg) {
	y = x + 1;
}

void *thread3(void *arg) {
	z = x + 1;
}

void main() {
	pthread_create(&t1, ((void *)0), thread1, ((void *)0));
	pthread_create(&t2, ((void *)0), thread2, ((void *)0));
	pthread_create(&t3, ((void *)0), thread3, ((void *)0));
	m = x + 1;

	return;
}
