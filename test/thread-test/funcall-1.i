# 0 "funcall-1.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "funcall-1.c"

int f() {

 return 4;

}

int main() {

 int x = 1, y = 0;

 while (x < 3 && y >= 0) {
  x = x + 1;
  while (y < 2) {
   y = y + 1;
   int b = f();
  }
 }

 return 0;
}
