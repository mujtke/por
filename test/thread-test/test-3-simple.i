# 0 "test-3-simple.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "test-3-simple.c"



int x = 0, y = 0;


typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

pthread_t t1, t2;

void *thread1(void *arg) {
 x = 1;
 return ((void *) 0);
}


void *thread2(void *arg) {
 y = 2;
 return ((void *) 0);
}

int main() {

 pthread_create(&t1, ((void *) 0), thread1, ((void *) 0));

 int a = x;
 int b = y;

 return 0;
}
