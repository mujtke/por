# 0 "false-negative-14.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "false-negative-14.c"
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
void assume_abort_if_not(int cond) {
  if(!cond) {abort();}
}
extern _Bool __VERIFIER_nondet_bool(void);
extern void abort(void);
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/assert.h" 1 3
# 15 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/assert.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/crtdefs.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/crtdefs.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_mac.h" 1 3
# 98 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_mac.h" 3
             
# 107 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_mac.h" 3
             
# 306 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_mac.h" 3
       
# 384 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_mac.h" 3
       
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_secapi.h" 1 3
# 12 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 2 3
# 282 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 1 3
# 9 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 1 3
# 661 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sdks/_mingw_ddk.h" 1 3
# 662 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 2 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 2 3




#pragma pack(push,_CRT_PACKING)
# 24 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 3
  
# 24 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 3
 typedef __builtin_va_list __gnuc_va_list;






  typedef __gnuc_va_list va_list;
# 103 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/vadefs.h" 3
#pragma pack(pop)
# 283 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 2 3
# 580 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
void __attribute__((__cdecl__)) __debugbreak(void);
extern __inline__ __attribute__((__always_inline__,__gnu_inline__)) void __attribute__((__cdecl__)) __debugbreak(void)
{

  __asm__ __volatile__("int {$}3":);







}
# 601 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
void __attribute__((__cdecl__)) __attribute__ ((__noreturn__)) __fastfail(unsigned int code);
extern __inline__ __attribute__((__always_inline__,__gnu_inline__)) void __attribute__((__cdecl__)) __attribute__ ((__noreturn__)) __fastfail(unsigned int code)
{

  __asm__ __volatile__("int {$}0x29"::"c"(code));
# 615 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
  __builtin_unreachable();
}
# 641 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw.h" 3
const char *__mingw_get_crt_info (void);
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 2 3




#pragma pack(push,_CRT_PACKING)
# 35 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
__extension__ typedef unsigned long long size_t;
# 45 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
__extension__ typedef long long ssize_t;






typedef size_t rsize_t;
# 62 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
__extension__ typedef long long intptr_t;
# 75 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
__extension__ typedef unsigned long long uintptr_t;
# 88 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
__extension__ typedef long long ptrdiff_t;
# 98 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
typedef unsigned short wchar_t;







typedef unsigned short wint_t;
typedef unsigned short wctype_t;





typedef int errno_t;




typedef long __time32_t;




__extension__ typedef long long __time64_t;
# 138 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
typedef __time64_t time_t;
# 430 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
struct threadlocaleinfostruct;
struct threadmbcinfostruct;
typedef struct threadlocaleinfostruct *pthreadlocinfo;
typedef struct threadmbcinfostruct *pthreadmbcinfo;
struct __lc_time_data;

typedef struct localeinfo_struct {
  pthreadlocinfo locinfo;
  pthreadmbcinfo mbcinfo;
} _locale_tstruct,*_locale_t;



typedef struct tagLC_ID {
  unsigned short wLanguage;
  unsigned short wCountry;
  unsigned short wCodePage;
} LC_ID,*LPLC_ID;




typedef struct threadlocaleinfostruct {





  int refcount;
  unsigned int lc_codepage;
  unsigned int lc_collate_cp;
  unsigned long lc_handle[6];
  LC_ID lc_id[6];
  struct {
    char *locale;
    wchar_t *wlocale;
    int *refcount;
    int *wrefcount;
  } lc_category[6];
  int lc_clike;
  int mb_cur_max;
  int *lconv_intl_refcount;
  int *lconv_num_refcount;
  int *lconv_mon_refcount;
  struct lconv *lconv;
  int *ctype1_refcount;
  unsigned short *ctype1;
  const unsigned short *pctype;
  const unsigned char *pclmap;
  const unsigned char *pcumap;
  struct __lc_time_data *lc_time_curr;

} threadlocinfo;
# 501 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt.h" 3
#pragma pack(pop)
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/crtdefs.h" 2 3
# 16 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/assert.h" 2 3
# 24 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/assert.h" 3
__attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) __attribute__ ((__noreturn__)) _wassert(const wchar_t *_Message,const wchar_t *_File,unsigned _Line);
__attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) __attribute__ ((__noreturn__)) _assert (const char *_Message, const char *_File, unsigned _Line);
# 9 "false-negative-14.c" 2

# 9 "false-negative-14.c"
void reach_error() { 
# 9 "false-negative-14.c" 3
                    (void) ((!!(
# 9 "false-negative-14.c"
                    0
# 9 "false-negative-14.c" 3
                    )) || (_assert(
# 9 "false-negative-14.c"
                    "0"
# 9 "false-negative-14.c" 3
                    ,"false-negative-14.c",9),0))
# 9 "false-negative-14.c"
                             ; }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();



typedef unsigned pthread_t;
typedef unsigned pthread_mutex_t;

extern void pthread_create(pthread_t *, void *, void *(*)(void *), void *);
extern void pthread_mutex_lock(pthread_t *);
extern void pthread_mutex_unlock(pthread_t *);
extern void pthread_mutex_init(pthread_mutex_t *, int);
extern void pthread_join(pthread_t , int);
extern void pthread_mutex_destroy(pthread_mutex_t *);

extern void abort(void);
# 47 "false-negative-14.c"
void * P0(void *arg);

void * P1(void *arg);

int __unbuffered_cnt = 0;

int EA0 = 0;

int EB0 = 0;

int EA1 = 0;

int EB1 = 0;

_Bool main$tmp_guard0;

_Bool main$tmp_guard1;

int x = 0;

_Bool x$flush_delayed;

int y = 0;

void * P0(void *arg)
{
  y = 1;
  EA0 = y;

  __VERIFIER_atomic_begin();
  EB0 = x;
  x = __VERIFIER_nondet_bool();
  __VERIFIER_atomic_end();

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}

void * P1(void *arg)
{

  __VERIFIER_atomic_begin();
  x$flush_delayed = __VERIFIER_nondet_bool();
  EA1 = 1;
  x = x$flush_delayed ? x : 1;
  __VERIFIER_atomic_end();

  EB1 = y;

  __unbuffered_cnt = __unbuffered_cnt + 1;

  return 0;
}

int main()
{
  pthread_t t0;
  pthread_t t1;
  pthread_create(&t0, ((void *) 0), P0, ((void *) 0));
  pthread_create(&t1, ((void *) 0), P1, ((void *) 0));

  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  if (main$tmp_guard0 == 0) abort();
  __VERIFIER_atomic_end();



  __VERIFIER_atomic_begin();

  main$tmp_guard1 = !(EB0 == 0 && EA1 == 1 && EB1 == 0);
  __VERIFIER_atomic_end();

  if (main$tmp_guard1 == 0)
   ERROR: reach_error();
  return 0;
}
