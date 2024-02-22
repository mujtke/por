# 0 "recursion-1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "recursion-1.c"

int X = 1;

int f(int a) {

 int c = f(a + 1);

 return c;
}

int main() {

 int b = f(X);

 return 0;
}
