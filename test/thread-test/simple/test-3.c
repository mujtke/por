#include<pthread.h>
#include<stdio.h>

int x = 0, y = 0;

pthread_t t1, t2;

void *thread1(void *arg) {
	x = 1;
	return NULL;
}


void *thread2(void *arg) {
	y = 2;
	return NULL;
}

int main() {

	pthread_create(&t1, NULL, thread1, NULL);
	pthread_create(&t2, NULL, thread2, NULL);
	int a = x;
	int b = y;

	return 0;
}
