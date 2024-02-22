# 0 "loop-7.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "loop-7.c"

int main() {

 int x = 1, y = 0;

 while (x < 3 && y >= 0) {
  x = x + 1;
  while (y < 2) {
   y = y + 1;
  }
 }

 return 0;
}
