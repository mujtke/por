extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

// #include <pthread.h>
#define NULL ((void *) 0)
typedef unsigned pthread_t;
extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);

int X = 1, Y = -1;

int main() {

 	for (int i = 0; i < 3; i++) {
 		while (X > 2 && Y < 0);
 		X = X + 1;
 	}
   	
//	int i = 0;
// 	for (; i < 3; i++) {
// 		while (X > 2 && Y < 0);
// 		X = X + 1;
// 	}

// 	int i = 0;
//  	for (; i < 3;) {
//  		while (X > 2 && Y < 0);
//  		X = X + 1;
//  	}
    
// 	int i = 0;
//  	for (; i < 3 && X < 4;) {
//  		while (X > 2 && Y < 0);
//  		X = X + 1;
//  	}

// 	int i = 0;
//  	for (;;) {
//  		while (X > 2 && Y < 0);
//  		X = X + 1;
//  	}
    
// 	int i = 0;
//  	for (; i < 3 || X < 4;) {
//  		while (X > 2 && Y < 0);
//  		X = X + 1;
//  	}
    
// 	int i = 0;
//  	for (; i < 3;) {
//  		while (X > 2 && Y < 0);
//  		X = X + 1;
//  	}
//	if (X > 0) {
//		int b = X;
//	}

//	int i = 0;
//	while (i < 3 || X < 4);

//	int i = 0, a, b, c;
//	while (a > 0 && b > 0 || c < 0);

//	int a, b, c;
//	while (!(a > 0) && !(b > 0));

//	int a, b, c;
//	while(a < 0 && b < 0) {
//		if (c > 0);
//	}

    return 0;
}
