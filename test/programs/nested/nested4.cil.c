// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 1 "nested4.c"
int main(void) 
{ int a ;
  int b ;
  int c ;
  int d ;

  {
#line 2
  a = 6;
#line 3
  b = 6;
#line 4
  c = 6;
#line 5
  d = 6;
#line 8
  a = 0;
  {
#line 8
  while (1) {
    while_0_continue: /* CIL Label */ ;
#line 8
    if (a < 6) {

    } else {
      goto while_0_break;
    }
#line 9
    b = 0;
    {
#line 9
    while (1) {
      while_1_continue: /* CIL Label */ ;
#line 9
      if (b < 6) {

      } else {
        goto while_1_break;
      }
#line 10
      c = 0;
      {
#line 10
      while (1) {
        while_2_continue: /* CIL Label */ ;
#line 10
        if (c < 6) {

        } else {
          goto while_2_break;
        }
#line 11
        d = 0;
        {
#line 11
        while (1) {
          while_3_continue: /* CIL Label */ ;
#line 11
          if (d < 6) {

          } else {
            goto while_3_break;
          }
#line 11
          d = d + 1;
        }
        while_3_break: /* CIL Label */ ;
        }
#line 10
        c = c + 1;
      }
      while_2_break: /* CIL Label */ ;
      }
#line 9
      b = b + 1;
    }
    while_1_break: /* CIL Label */ ;
    }
#line 8
    a = a + 1;
  }
  while_0_break: /* CIL Label */ ;
  }
#line 17
  if (a == 6) {
#line 17
    if (b == 6) {
#line 17
      if (c == 6) {
#line 17
        if (d == 6) {

        } else {
          goto ERROR;
        }
      } else {
        goto ERROR;
      }
    } else {
      goto ERROR;
    }
  } else {
    ERROR: 
    goto ERROR;
  }
#line 20
  return (1);
}
}
