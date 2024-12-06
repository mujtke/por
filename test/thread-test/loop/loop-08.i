# 0 "loop-8.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "/usr/include/stdc-predef.h" 1 3 4
# 0 "<command-line>" 2
# 1 "loop-8.c"

int x = 1, y = 0;

int main() {

 while (x < 3) {
  x = x + 1;
 }

 return 0;
}

void f1() {

 while (x < 3 && y < 2) {
  x = x + 1;
  y = y + 1;
 }

}

void f2() {

 while (x < 3) {
  x = x + 1;
  while (y < 2) {
   y = y + 1;
  }
 }
}
