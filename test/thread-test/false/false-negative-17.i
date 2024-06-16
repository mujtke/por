# 0 "false-negative-17.c"
# 0 "<built-in>"
# 0 "<command-line>"
# 1 "false-negative-17.c"
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
# 8 "false-negative-17.c" 2

# 8 "false-negative-17.c"
void reach_error() { 
# 8 "false-negative-17.c" 3
                    (void) ((!!(
# 8 "false-negative-17.c"
                    0
# 8 "false-negative-17.c" 3
                    )) || (_assert(
# 8 "false-negative-17.c"
                    "0"
# 8 "false-negative-17.c" 3
                    ,"false-negative-17.c",8),0))
# 8 "false-negative-17.c"
                             ; }
void __VERIFIER_assert(int expression) { if (!expression) { ERROR: {reach_error();abort();} }; return; }
extern void __VERIFIER_atomic_begin();
extern void __VERIFIER_atomic_end();

# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/assert.h" 1 3
# 14 "false-negative-17.c" 2
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 1 3
# 62 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/stddef.h" 1 3 4
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/stddef.h" 1 3 4
# 18 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/stddef.h" 3 4
  
# 18 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/stddef.h" 3
 __attribute__ ((__dllimport__)) extern int *__attribute__((__cdecl__)) _errno(void);

  errno_t __attribute__((__cdecl__)) _set_errno(int _Value);
  errno_t __attribute__((__cdecl__)) _get_errno(int *_Value);


  __attribute__ ((__dllimport__)) extern unsigned long __attribute__((__cdecl__)) __threadid(void);

  __attribute__ ((__dllimport__)) extern uintptr_t __attribute__((__cdecl__)) __threadhandle(void);
# 424 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/stddef.h" 3 4
typedef struct {
  long long __max_align_ll __attribute__((__aligned__(__alignof__(long long))));
  long double __max_align_ld __attribute__((__aligned__(__alignof__(long double))));
} max_align_t;
# 2 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/stddef.h" 2 3 4
# 63 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/errno.h" 1 3
# 64 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/types.h" 1 3
# 43 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/types.h" 3
typedef unsigned short _ino_t;

typedef unsigned short ino_t;





typedef unsigned int _dev_t;

typedef unsigned int dev_t;
# 62 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/types.h" 3
__extension__
typedef long long _pid_t;




typedef _pid_t pid_t;





typedef unsigned short _mode_t;


typedef _mode_t mode_t;



# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_off_t.h" 1 3




  typedef long _off_t;

  typedef long off32_t;





  __extension__ typedef long long _off64_t;

  __extension__ typedef long long off64_t;
# 26 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_mingw_off_t.h" 3
typedef off32_t off_t;
# 82 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/types.h" 2 3


typedef unsigned int useconds_t;




struct timespec {
  time_t tv_sec;
  long tv_nsec;
};

struct itimerspec {
  struct timespec it_interval;
  struct timespec it_value;
};





__extension__
typedef unsigned long long _sigset_t;
# 65 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3

# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/process.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/process.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt_startup.h" 1 3
# 14 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/corecrt_startup.h" 3
__attribute__ ((__dllimport__)) char **__attribute__((__cdecl__)) __p__acmdln(void);


__attribute__ ((__dllimport__)) wchar_t **__attribute__((__cdecl__)) __p__wcmdln(void);


typedef void (__attribute__((__cdecl__)) *_PVFV)(void);
typedef int (__attribute__((__cdecl__)) *_PIFV)(void);
typedef void (__attribute__((__cdecl__)) *_PVFI)(int);

typedef struct _onexit_table_t {
    _PVFV* _first;
    _PVFV* _last;
    _PVFV* _end;
} _onexit_table_t;

typedef int (__attribute__((__cdecl__)) *_onexit_t)(void);

__attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _initialize_onexit_table(_onexit_table_t*);
__attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _register_onexit_function(_onexit_table_t*,_onexit_t);
__attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _execute_onexit_table(_onexit_table_t*);
__attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _crt_atexit(_PVFV func);
__attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _crt_at_quick_exit(_PVFV func);
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/process.h" 2 3
# 32 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/process.h" 3
  typedef void (__attribute__((__cdecl__)) *_beginthread_proc_type)(void *);
  typedef unsigned (__attribute__((__stdcall__)) *_beginthreadex_proc_type)(void *);

  __attribute__ ((__dllimport__)) uintptr_t __attribute__((__cdecl__)) _beginthread(_beginthread_proc_type _StartAddress,unsigned _StackSize,void *_ArgList);
  __attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) _endthread(void) __attribute__ ((__noreturn__));
  __attribute__ ((__dllimport__)) uintptr_t __attribute__((__cdecl__)) _beginthreadex(void *_Security,unsigned _StackSize,_beginthreadex_proc_type _StartAddress,void *_ArgList,unsigned _InitFlag,unsigned *_ThrdAddr);
  __attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) _endthreadex(unsigned _Retval) __attribute__ ((__noreturn__));



  void __attribute__((__cdecl__)) __attribute__ ((__nothrow__)) exit(int _Code) __attribute__ ((__noreturn__));
  void __attribute__((__cdecl__)) __attribute__ ((__nothrow__)) _exit(int _Code) __attribute__ ((__noreturn__));






  void __attribute__((__cdecl__)) _Exit(int) __attribute__ ((__noreturn__));






       

  void __attribute__((__cdecl__)) __attribute__ ((__noreturn__)) abort(void);
       



  typedef void (__attribute__((__stdcall__)) *_tls_callback_type)(void*,unsigned long,void*);
  __attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) _register_thread_local_exe_atexit_callback(_tls_callback_type callback);

  void __attribute__((__cdecl__)) __attribute__ ((__nothrow__)) _cexit(void);
  void __attribute__((__cdecl__)) __attribute__ ((__nothrow__)) _c_exit(void);

  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _getpid(void);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _cwait(int *_TermStat,intptr_t _ProcHandle,int _Action);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execl(const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execle(const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execlp(const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execlpe(const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execv(const char *_Filename,const char *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execve(const char *_Filename,const char *const *_ArgList,const char *const *_Env);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execvp(const char *_Filename,const char *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _execvpe(const char *_Filename,const char *const *_ArgList,const char *const *_Env);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnl(int _Mode,const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnle(int _Mode,const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnlp(int _Mode,const char *_Filename,const char *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnlpe(int _Mode,const char *_Filename,const char *_ArgList,...);



  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnv(int _Mode,const char *_Filename,const char *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnve(int _Mode,const char *_Filename,const char *const *_ArgList,const char *const *_Env);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnvp(int _Mode,const char *_Filename,const char *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _spawnvpe(int _Mode,const char *_Filename,const char *const *_ArgList,const char *const *_Env);




  int __attribute__((__cdecl__)) system(const char *_Command);




  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecl(const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecle(const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexeclp(const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexeclpe(const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecv(const wchar_t *_Filename,const wchar_t *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecve(const wchar_t *_Filename,const wchar_t *const *_ArgList,const wchar_t *const *_Env);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecvp(const wchar_t *_Filename,const wchar_t *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wexecvpe(const wchar_t *_Filename,const wchar_t *const *_ArgList,const wchar_t *const *_Env);




  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnl(int _Mode,const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnle(int _Mode,const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnlp(int _Mode,const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnlpe(int _Mode,const wchar_t *_Filename,const wchar_t *_ArgList,...);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnv(int _Mode,const wchar_t *_Filename,const wchar_t *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnve(int _Mode,const wchar_t *_Filename,const wchar_t *const *_ArgList,const wchar_t *const *_Env);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnvp(int _Mode,const wchar_t *_Filename,const wchar_t *const *_ArgList);
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) _wspawnvpe(int _Mode,const wchar_t *_Filename,const wchar_t *const *_ArgList,const wchar_t *const *_Env);




  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) _wsystem(const wchar_t *_Command);




  intptr_t __attribute__((__cdecl__)) _loaddll(char *_Filename);
  int __attribute__((__cdecl__)) _unloaddll(intptr_t _Handle);
  int (__attribute__((__cdecl__)) *__attribute__((__cdecl__)) _getdllprocaddr(intptr_t _Handle,char *_ProcedureName,intptr_t _Ordinal))(void);
# 161 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/process.h" 3
  int __attribute__((__cdecl__)) getpid(void) ;



  intptr_t __attribute__((__cdecl__)) cwait(int *_TermStat,intptr_t _ProcHandle,int _Action) ;

  int __attribute__((__cdecl__)) execl(const char *_Filename,const char *_ArgList,...) ;
  int __attribute__((__cdecl__)) execle(const char *_Filename,const char *_ArgList,...) ;
  int __attribute__((__cdecl__)) execlp(const char *_Filename,const char *_ArgList,...) ;
  int __attribute__((__cdecl__)) execlpe(const char *_Filename,const char *_ArgList,...) ;






  intptr_t __attribute__((__cdecl__)) spawnl(int,const char *_Filename,const char *_ArgList,...) ;
  intptr_t __attribute__((__cdecl__)) spawnle(int,const char *_Filename,const char *_ArgList,...) ;
  intptr_t __attribute__((__cdecl__)) spawnlp(int,const char *_Filename,const char *_ArgList,...) ;
  intptr_t __attribute__((__cdecl__)) spawnlpe(int,const char *_Filename,const char *_ArgList,...) ;





  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) execv(const char *_Filename,char *const _ArgList[]) ;
  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) execve(const char *_Filename,char *const _ArgList[],char *const _Env[]) ;
  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) execvp(const char *_Filename,char *const _ArgList[]) ;
  __attribute__ ((__dllimport__)) int __attribute__((__cdecl__)) execvpe(const char *_Filename,char *const _ArgList[],char *const _Env[]) ;






  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) spawnv(int,const char *_Filename,char *const _ArgList[]) ;
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) spawnve(int,const char *_Filename,char *const _ArgList[],char *const _Env[]) ;
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) spawnvp(int,const char *_Filename,char *const _ArgList[]) ;
  __attribute__ ((__dllimport__)) intptr_t __attribute__((__cdecl__)) spawnvpe(int,const char *_Filename,char *const _ArgList[],char *const _Env[]) ;
# 67 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 1 3 4
# 34 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 3 4
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/syslimits.h" 1 3 4






# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 1 3 4
# 205 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 3 4
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/limits.h" 1 3 4
# 206 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 2 3 4
# 8 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/syslimits.h" 2 3 4
# 35 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/lib/gcc/x86_64-w64-mingw32/13.2.0/include/limits.h" 2 3 4
# 68 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/signal.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/signal.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_signal.h" 1 3
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/signal.h" 2 3







  typedef int sig_atomic_t;
# 48 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/signal.h" 3
  typedef void (*__p_sig_fn_t)(int);
# 57 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/signal.h" 3
  extern void **__attribute__((__cdecl__)) __pxcptinfoptrs(void);


  __p_sig_fn_t __attribute__((__cdecl__)) signal(int _SigNum,__p_sig_fn_t _Func);
  int __attribute__((__cdecl__)) raise(int _SigNum);
# 69 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 1 3
# 25 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 1 3
# 15 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 3
#pragma pack(push,_CRT_PACKING)
# 53 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 3
  struct __timeb32 {
    __time32_t time;
    unsigned short millitm;
    short timezone;
    short dstflag;
  };


  struct timeb {
    time_t time;
    unsigned short millitm;
    short timezone;
    short dstflag;
  };


  struct __timeb64 {
    __time64_t time;
    unsigned short millitm;
    short timezone;
    short dstflag;
  };



  __attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) _ftime64(struct __timeb64 *_Time);
  __attribute__ ((__dllimport__)) void __attribute__((__cdecl__)) _ftime32(struct __timeb32 *_Time);
# 89 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 3
struct _timespec32 {
  __time32_t tv_sec;
  long tv_nsec;
};

struct _timespec64 {
  __time64_t tv_sec;
  long tv_nsec;
};
# 113 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 3
  void __attribute__((__cdecl__)) ftime (struct timeb *);
# 133 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 3
#pragma pack(pop)

# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sec_api/sys/timeb_s.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sec_api/sys/timeb_s.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 1 3
# 11 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sec_api/sys/timeb_s.h" 2 3





  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _ftime32_s(struct __timeb32 *_Time);
  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _ftime64_s(struct __timeb64 *_Time);
# 136 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/sys/timeb.h" 2 3
# 26 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 2 3

#pragma pack(push,_CRT_PACKING)
# 63 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  typedef long clock_t;
# 100 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  struct tm {
    int tm_sec;
    int tm_min;
    int tm_hour;
    int tm_mday;
    int tm_mon;
    int tm_year;
    int tm_wday;
    int tm_yday;
    int tm_isdst;
  };
# 129 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  extern __attribute__ ((__dllimport__)) int _daylight;
  extern __attribute__ ((__dllimport__)) long _dstbias;
  extern __attribute__ ((__dllimport__)) long _timezone;
  extern __attribute__ ((__dllimport__)) char * _tzname[2];
# 145 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _get_daylight(int *_Daylight);
  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _get_dstbias(long *_Daylight_savings_bias);
  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _get_timezone(long *_Timezone);
  __attribute__ ((__dllimport__)) errno_t __attribute__((__cdecl__)) _get_tzname(size_t *_ReturnValue,char *_Buffer,size_t _SizeInBytes,int _Index);
  char *__attribute__((__cdecl__)) asctime(const struct tm *_Tm) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) asctime_s (char *_Buf,size_t _SizeInWords,const struct tm *_Tm);
  __attribute__ ((__dllimport__)) char *__attribute__((__cdecl__)) _ctime32(const __time32_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _ctime32_s (char *_Buf,size_t _SizeInBytes,const __time32_t *_Time);
  clock_t __attribute__((__cdecl__)) clock(void);
  __attribute__ ((__dllimport__)) double __attribute__((__cdecl__)) _difftime32(__time32_t _Time1,__time32_t _Time2);
  __attribute__ ((__dllimport__)) struct tm *__attribute__((__cdecl__)) _gmtime32(const __time32_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _gmtime32_s (struct tm *_Tm,const __time32_t *_Time);
  __attribute__ ((__dllimport__)) struct tm *__attribute__((__cdecl__)) _localtime32(const __time32_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _localtime32_s (struct tm *_Tm,const __time32_t *_Time);
  size_t __attribute__((__cdecl__)) strftime(char * __restrict__ _Buf,size_t _SizeInBytes,const char * __restrict__ _Format,const struct tm * __restrict__ _Tm) __attribute__((__format__ (ms_strftime, 3, 0)));
  __attribute__ ((__dllimport__)) size_t __attribute__((__cdecl__)) _strftime_l(char * __restrict__ _Buf,size_t _Max_size,const char * __restrict__ _Format,const struct tm * __restrict__ _Tm,_locale_t _Locale);
  __attribute__ ((__dllimport__)) char *__attribute__((__cdecl__)) _strdate(char *_Buffer) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _strdate_s (char *_Buf,size_t _SizeInBytes);
  __attribute__ ((__dllimport__)) char *__attribute__((__cdecl__)) _strtime(char *_Buffer) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _strtime_s (char *_Buf ,size_t _SizeInBytes);
  __attribute__ ((__dllimport__)) __time32_t __attribute__((__cdecl__)) _time32(__time32_t *_Time);



  __attribute__ ((__dllimport__)) __time32_t __attribute__((__cdecl__)) _mktime32(struct tm *_Tm);
  __attribute__ ((__dllimport__)) __time32_t __attribute__((__cdecl__)) _mkgmtime32(struct tm *_Tm);


  void __attribute__((__cdecl__)) tzset(void) ;



  __attribute__ ((__dllimport__))

  void __attribute__((__cdecl__)) _tzset(void);


  __attribute__ ((__dllimport__)) double __attribute__((__cdecl__)) _difftime64(__time64_t _Time1,__time64_t _Time2);
  __attribute__ ((__dllimport__)) char *__attribute__((__cdecl__)) _ctime64(const __time64_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _ctime64_s (char *_Buf,size_t _SizeInBytes,const __time64_t *_Time);
  __attribute__ ((__dllimport__)) struct tm *__attribute__((__cdecl__)) _gmtime64(const __time64_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _gmtime64_s (struct tm *_Tm,const __time64_t *_Time);
  __attribute__ ((__dllimport__)) struct tm *__attribute__((__cdecl__)) _localtime64(const __time64_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _localtime64_s (struct tm *_Tm,const __time64_t *_Time);
  __attribute__ ((__dllimport__)) __time64_t __attribute__((__cdecl__)) _mktime64(struct tm *_Tm);
  __attribute__ ((__dllimport__)) __time64_t __attribute__((__cdecl__)) _mkgmtime64(struct tm *_Tm);
  __attribute__ ((__dllimport__)) __time64_t __attribute__((__cdecl__)) _time64(__time64_t *_Time);



  unsigned __attribute__((__cdecl__)) _getsystime(struct tm *_Tm);
  unsigned __attribute__((__cdecl__)) _setsystime(struct tm *_Tm,unsigned _MilliSec);


  __attribute__ ((__dllimport__)) wchar_t *__attribute__((__cdecl__)) _wasctime(const struct tm *_Tm);
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _wasctime_s (wchar_t *_Buf,size_t _SizeInWords,const struct tm *_Tm);
  wchar_t *__attribute__((__cdecl__)) _wctime32(const __time32_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _wctime32_s (wchar_t *_Buf,size_t _SizeInWords,const __time32_t *_Time);
  size_t __attribute__((__cdecl__)) wcsftime(wchar_t * __restrict__ _Buf,size_t _SizeInWords,const wchar_t * __restrict__ _Format,const struct tm * __restrict__ _Tm);
  __attribute__ ((__dllimport__)) size_t __attribute__((__cdecl__)) _wcsftime_l(wchar_t * __restrict__ _Buf,size_t _SizeInWords,const wchar_t * __restrict__ _Format,const struct tm * __restrict__ _Tm,_locale_t _Locale);
  __attribute__ ((__dllimport__)) wchar_t *__attribute__((__cdecl__)) _wstrdate(wchar_t *_Buffer) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _wstrdate_s (wchar_t *_Buf,size_t _SizeInWords);
  __attribute__ ((__dllimport__)) wchar_t *__attribute__((__cdecl__)) _wstrtime(wchar_t *_Buffer) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _wstrtime_s (wchar_t *_Buf,size_t _SizeInWords);
  __attribute__ ((__dllimport__)) wchar_t *__attribute__((__cdecl__)) _wctime64(const __time64_t *_Time) ;
  __attribute__((dllimport)) errno_t __attribute__((__cdecl__)) _wctime64_s (wchar_t *_Buf,size_t _SizeInWords,const __time64_t *_Time);



  wchar_t *__attribute__((__cdecl__)) _wctime(const time_t *) ;
# 226 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  errno_t __attribute__((__cdecl__)) _wctime_s(wchar_t *, size_t, const time_t *);
# 256 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
static __inline time_t __attribute__((__cdecl__)) time(time_t *_Time) { return _time64(_Time); }



static __inline double __attribute__((__cdecl__)) difftime(time_t _Time1,time_t _Time2) { return _difftime64(_Time1,_Time2); }
static __inline struct tm *__attribute__((__cdecl__)) localtime(const time_t *_Time) { return _localtime64(_Time); }
static __inline errno_t __attribute__((__cdecl__)) localtime_s(struct tm *_Tm,const time_t *_Time) { return _localtime64_s(_Tm,_Time); }
static __inline struct tm *__attribute__((__cdecl__)) gmtime(const time_t *_Time) { return _gmtime64(_Time); }
static __inline errno_t __attribute__((__cdecl__)) gmtime_s(struct tm *_Tm, const time_t *_Time) { return _gmtime64_s(_Tm, _Time); }
static __inline char *__attribute__((__cdecl__)) ctime(const time_t *_Time) { return _ctime64(_Time); }
static __inline errno_t __attribute__((__cdecl__)) ctime_s(char *_Buf,size_t _SizeInBytes,const time_t *_Time) { return _ctime64_s(_Buf,_SizeInBytes,_Time); }
static __inline time_t __attribute__((__cdecl__)) mktime(struct tm *_Tm) { return _mktime64(_Tm); }
static __inline time_t __attribute__((__cdecl__)) _mkgmtime(struct tm *_Tm) { return _mkgmtime64(_Tm); }
# 285 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
  __attribute__ ((__dllimport__)) extern int daylight ;
  __attribute__ ((__dllimport__)) extern long timezone ;
  __attribute__ ((__dllimport__)) extern char *tzname[2] ;
  void __attribute__((__cdecl__)) tzset(void) ;


# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_timeval.h" 1 3
# 10 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/_timeval.h" 3
struct timeval
{
 long tv_sec;
 long tv_usec;
};
# 292 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 2 3



struct timezone {
  int tz_minuteswest;
  int tz_dsttime;
};

  extern int __attribute__((__cdecl__)) mingw_gettimeofday (struct timeval *p, struct timezone *z);


#pragma pack(pop)
# 334 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_time.h" 1 3
# 49 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_time.h" 3
typedef int clockid_t;
# 82 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_time.h" 3
       





int __attribute__((__cdecl__)) nanosleep(const struct timespec *request, struct timespec *remain);

int __attribute__((__cdecl__)) clock_nanosleep(clockid_t clock_id, int flags, const struct timespec *request, struct timespec *remain);
int __attribute__((__cdecl__)) clock_getres(clockid_t clock_id, struct timespec *res);
int __attribute__((__cdecl__)) clock_gettime(clockid_t clock_id, struct timespec *tp);
int __attribute__((__cdecl__)) clock_settime(clockid_t clock_id, const struct timespec *tp);

       
# 335 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/time.h" 2 3
# 70 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3



# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_compat.h" 1 3
# 74 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 165 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
void * pthread_timechange_handler_np(void * dummy);
int pthread_delay_np (const struct timespec *interval);
int pthread_num_processors_np(void);
int pthread_set_num_processors_np(int n);
# 185 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
typedef long pthread_once_t;
typedef unsigned pthread_mutexattr_t;
typedef unsigned pthread_key_t;
typedef void *pthread_barrierattr_t;
typedef int pthread_condattr_t;
typedef int pthread_rwlockattr_t;
# 201 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
typedef uintptr_t pthread_t;

typedef struct _pthread_cleanup _pthread_cleanup;
struct _pthread_cleanup
{
    void (*func)(void *);
    void *arg;
    _pthread_cleanup *next;
};
# 230 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
struct sched_param {
  int sched_priority;
};

int sched_yield(void);
int sched_get_priority_min(int pol);
int sched_get_priority_max(int pol);
int sched_getscheduler(pid_t pid);
int sched_setscheduler(pid_t pid, int pol, const struct sched_param *param);



typedef struct pthread_attr_t pthread_attr_t;
struct pthread_attr_t
{
    unsigned p_state;
    void *stack;
    size_t s_size;
    struct sched_param param;
};

int pthread_attr_setschedparam(pthread_attr_t *attr, const struct sched_param *param);
int pthread_attr_getschedparam(const pthread_attr_t *attr, struct sched_param *param);
int pthread_getschedparam(pthread_t thread, int *pol, struct sched_param *param);
int pthread_setschedparam(pthread_t thread, int pol, const struct sched_param *param);
int pthread_attr_setschedpolicy (pthread_attr_t *attr, int pol);
int pthread_attr_getschedpolicy (const pthread_attr_t *attr, int *pol);


typedef intptr_t pthread_spinlock_t;
typedef intptr_t pthread_mutex_t;
typedef intptr_t pthread_cond_t;
typedef intptr_t pthread_rwlock_t;
typedef void *pthread_barrier_t;
# 282 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
extern void (**_pthread_key_dest)(void *);
int pthread_key_create(pthread_key_t *key, void (* dest)(void *));
int pthread_key_delete(pthread_key_t key);
void * pthread_getspecific(pthread_key_t key);
int pthread_setspecific(pthread_key_t key, const void *value);

pthread_t pthread_self(void);
int pthread_once(pthread_once_t *o, void (*func)(void));
void pthread_testcancel(void);
int pthread_equal(pthread_t t1, pthread_t t2);
void pthread_tls_init(void);
void _pthread_cleanup_dest(pthread_t t);
int pthread_get_concurrency(int *val);
int pthread_set_concurrency(int val);
void pthread_exit(void *res);
void _pthread_invoke_cancel(void);
int pthread_cancel(pthread_t t);
int pthread_kill(pthread_t t, int sig);
unsigned _pthread_get_state(const pthread_attr_t *attr, unsigned flag);
int _pthread_set_state(pthread_attr_t *attr, unsigned flag, unsigned val);
int pthread_setcancelstate(int state, int *oldstate);
int pthread_setcanceltype(int type, int *oldtype);
int pthread_create_wrapper(void *args);
int pthread_create(pthread_t *th, const pthread_attr_t *attr, void *(* func)(void *), void *arg);
int pthread_join(pthread_t t, void **res);
int pthread_detach(pthread_t t);
int pthread_setname_np(pthread_t thread, const char *name);
int pthread_getname_np(pthread_t thread, char *name, size_t len);


int pthread_rwlock_init(pthread_rwlock_t *rwlock_, const pthread_rwlockattr_t *attr);
int pthread_rwlock_wrlock(pthread_rwlock_t *l);
int pthread_rwlock_timedwrlock(pthread_rwlock_t *rwlock, const struct timespec *ts);
int pthread_rwlock_rdlock(pthread_rwlock_t *l);
int pthread_rwlock_timedrdlock(pthread_rwlock_t *l, const struct timespec *ts);
int pthread_rwlock_unlock(pthread_rwlock_t *l);
int pthread_rwlock_tryrdlock(pthread_rwlock_t *l);
int pthread_rwlock_trywrlock(pthread_rwlock_t *l);
int pthread_rwlock_destroy (pthread_rwlock_t *l);

int pthread_cond_init(pthread_cond_t *cv, const pthread_condattr_t *a);
int pthread_cond_destroy(pthread_cond_t *cv);
int pthread_cond_signal (pthread_cond_t *cv);
int pthread_cond_broadcast (pthread_cond_t *cv);
int pthread_cond_wait (pthread_cond_t *cv, pthread_mutex_t *external_mutex);
int pthread_cond_timedwait(pthread_cond_t *cv, pthread_mutex_t *external_mutex, const struct timespec *t);
int pthread_cond_timedwait_relative_np(pthread_cond_t *cv, pthread_mutex_t *external_mutex, const struct timespec *t);

int pthread_mutex_lock(pthread_mutex_t *m);
int pthread_mutex_timedlock(pthread_mutex_t *m, const struct timespec *ts);
int pthread_mutex_unlock(pthread_mutex_t *m);
int pthread_mutex_trylock(pthread_mutex_t *m);
int pthread_mutex_init(pthread_mutex_t *m, const pthread_mutexattr_t *a);
int pthread_mutex_destroy(pthread_mutex_t *m);

int pthread_barrier_destroy(pthread_barrier_t *b);
int pthread_barrier_init(pthread_barrier_t *b, const void *attr, unsigned int count);
int pthread_barrier_wait(pthread_barrier_t *b);

int pthread_spin_init(pthread_spinlock_t *l, int pshared);
int pthread_spin_destroy(pthread_spinlock_t *l);

int pthread_spin_lock(pthread_spinlock_t *l);
int pthread_spin_trylock(pthread_spinlock_t *l);
int pthread_spin_unlock(pthread_spinlock_t *l);

int pthread_attr_init(pthread_attr_t *attr);
int pthread_attr_destroy(pthread_attr_t *attr);
int pthread_attr_setdetachstate(pthread_attr_t *a, int flag);
int pthread_attr_getdetachstate(const pthread_attr_t *a, int *flag);
int pthread_attr_setinheritsched(pthread_attr_t *a, int flag);
int pthread_attr_getinheritsched(const pthread_attr_t *a, int *flag);
int pthread_attr_setscope(pthread_attr_t *a, int flag);
int pthread_attr_getscope(const pthread_attr_t *a, int *flag);
int pthread_attr_getstack(const pthread_attr_t *attr, void **stack, size_t *size);
int pthread_attr_setstack(pthread_attr_t *attr, void *stack, size_t size);
int pthread_attr_getstackaddr(const pthread_attr_t *attr, void **stack);
int pthread_attr_setstackaddr(pthread_attr_t *attr, void *stack);
int pthread_attr_getstacksize(const pthread_attr_t *attr, size_t *size);
int pthread_attr_setstacksize(pthread_attr_t *attr, size_t size);

int pthread_mutexattr_init(pthread_mutexattr_t *a);
int pthread_mutexattr_destroy(pthread_mutexattr_t *a);
int pthread_mutexattr_gettype(const pthread_mutexattr_t *a, int *type);
int pthread_mutexattr_settype(pthread_mutexattr_t *a, int type);
int pthread_mutexattr_getpshared(const pthread_mutexattr_t *a, int *type);
int pthread_mutexattr_setpshared(pthread_mutexattr_t * a, int type);
int pthread_mutexattr_getprotocol(const pthread_mutexattr_t *a, int *type);
int pthread_mutexattr_setprotocol(pthread_mutexattr_t *a, int type);
int pthread_mutexattr_getprioceiling(const pthread_mutexattr_t *a, int * prio);
int pthread_mutexattr_setprioceiling(pthread_mutexattr_t *a, int prio);
int pthread_getconcurrency(void);
int pthread_setconcurrency(int new_level);

int pthread_condattr_destroy(pthread_condattr_t *a);
int pthread_condattr_init(pthread_condattr_t *a);
int pthread_condattr_getpshared(const pthread_condattr_t *a, int *s);
int pthread_condattr_setpshared(pthread_condattr_t *a, int s);






int pthread_condattr_getclock (const pthread_condattr_t *attr,
       clockid_t *clock_id);
int pthread_condattr_setclock(pthread_condattr_t *attr,
       clockid_t clock_id);
int __pthread_clock_nanosleep(clockid_t clock_id, int flags, const struct timespec *rqtp, struct timespec *rmtp);

int pthread_barrierattr_init(void **attr);
int pthread_barrierattr_destroy(void **attr);
int pthread_barrierattr_setpshared(void **attr, int s);
int pthread_barrierattr_getpshared(void **attr, int *s);


struct _pthread_cleanup ** pthread_getclean (void);
void * pthread_gethandle (pthread_t t);
void * pthread_getevent (void);

unsigned long long _pthread_rel_time_in_ms(const struct timespec *ts);
unsigned long long _pthread_time_in_ms(void);
unsigned long long _pthread_time_in_ms_from_timespec(const struct timespec *ts);
int _pthread_tryjoin (pthread_t t, void **res);
int pthread_rwlockattr_destroy(pthread_rwlockattr_t *a);
int pthread_rwlockattr_getpshared(pthread_rwlockattr_t *a, int *s);
int pthread_rwlockattr_init(pthread_rwlockattr_t *a);
int pthread_rwlockattr_setpshared(pthread_rwlockattr_t *a, int s);
# 421 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 3
# 1 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread_unistd.h" 1 3
# 422 "/usr/local/Cellar/mingw-w64/11.0.1/toolchain-x86_64/x86_64-w64-mingw32/include/pthread.h" 2 3
# 15 "false-negative-17.c" 2
# 36 "false-negative-17.c"

# 36 "false-negative-17.c"
void * P0(void *arg);


void * P1(void *arg);


void fence();


void isync();


void lwfence();




int __unbuffered_cnt;


int __unbuffered_cnt = 0;


int __unbuffered_p1_EAX;


int __unbuffered_p1_EAX = 0;


int __unbuffered_p1_EBX;


int __unbuffered_p1_EBX = 0;


_Bool main$tmp_guard0;


_Bool main$tmp_guard1;


int x;


int x = 0;


int y;


int y = 0;


int z;


int z = 0;


_Bool z$flush_delayed;


int z$mem_tmp;


_Bool z$r_buff0_thd0;


_Bool z$r_buff0_thd1;


_Bool z$r_buff0_thd2;


_Bool z$r_buff1_thd0;


_Bool z$r_buff1_thd1;


_Bool z$r_buff1_thd2;


_Bool z$read_delayed;


int *z$read_delayed_var;


int z$w_buff0;


_Bool z$w_buff0_used;


int z$w_buff1;


_Bool z$w_buff1_used;


_Bool weak$$choice0;


_Bool weak$$choice2;



void * P0(void *arg)
{
  __VERIFIER_atomic_begin();
  z$w_buff1 = z$w_buff0;
  z$w_buff0 = 1;
  z$w_buff1_used = z$w_buff0_used;
  z$w_buff0_used = (_Bool)1;
  __VERIFIER_assert(!(z$w_buff1_used && z$w_buff0_used));
  z$r_buff1_thd0 = z$r_buff0_thd0;
  z$r_buff1_thd1 = z$r_buff0_thd1;
  z$r_buff1_thd2 = z$r_buff0_thd2;
  z$r_buff0_thd1 = (_Bool)1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  x = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  z = z$w_buff0_used && z$r_buff0_thd1 ? z$w_buff0 : (z$w_buff1_used && z$r_buff1_thd1 ? z$w_buff1 : z);
  z$w_buff0_used = z$w_buff0_used && z$r_buff0_thd1 ? (_Bool)0 : z$w_buff0_used;
  z$w_buff1_used = z$w_buff0_used && z$r_buff0_thd1 || z$w_buff1_used && z$r_buff1_thd1 ? (_Bool)0 : z$w_buff1_used;
  z$r_buff0_thd1 = z$w_buff0_used && z$r_buff0_thd1 ? (_Bool)0 : z$r_buff0_thd1;
  z$r_buff1_thd1 = z$w_buff0_used && z$r_buff0_thd1 || z$w_buff1_used && z$r_buff1_thd1 ? (_Bool)0 : z$r_buff1_thd1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void * P1(void *arg)
{
  __VERIFIER_atomic_begin();
  x = 2;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  y = 1;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __unbuffered_p1_EAX = y;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  weak$$choice0 = __VERIFIER_nondet_bool();
  weak$$choice2 = __VERIFIER_nondet_bool();
  z$flush_delayed = weak$$choice2;
  z$mem_tmp = z;
  z = !z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z : (z$w_buff0_used && z$r_buff0_thd2 ? z$w_buff0 : z$w_buff1);
  z$w_buff0 = weak$$choice2 ? z$w_buff0 : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$w_buff0 : (z$w_buff0_used && z$r_buff0_thd2 ? z$w_buff0 : z$w_buff0));
  z$w_buff1 = weak$$choice2 ? z$w_buff1 : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$w_buff1 : (z$w_buff0_used && z$r_buff0_thd2 ? z$w_buff1 : z$w_buff1));
  z$w_buff0_used = weak$$choice2 ? z$w_buff0_used : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$w_buff0_used : (z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : z$w_buff0_used));
  z$w_buff1_used = weak$$choice2 ? z$w_buff1_used : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$w_buff1_used : (z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : (_Bool)0));
  z$r_buff0_thd2 = weak$$choice2 ? z$r_buff0_thd2 : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$r_buff0_thd2 : (z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : z$r_buff0_thd2));
  z$r_buff1_thd2 = weak$$choice2 ? z$r_buff1_thd2 : (!z$w_buff0_used || !z$r_buff0_thd2 && !z$w_buff1_used || !z$r_buff0_thd2 && !z$r_buff1_thd2 ? z$r_buff1_thd2 : (z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : (_Bool)0));
  __unbuffered_p1_EBX = z;
  z = z$flush_delayed ? z$mem_tmp : z;
  z$flush_delayed = (_Bool)0;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  z = z$w_buff0_used && z$r_buff0_thd2 ? z$w_buff0 : (z$w_buff1_used && z$r_buff1_thd2 ? z$w_buff1 : z);
  z$w_buff0_used = z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : z$w_buff0_used;
  z$w_buff1_used = z$w_buff0_used && z$r_buff0_thd2 || z$w_buff1_used && z$r_buff1_thd2 ? (_Bool)0 : z$w_buff1_used;
  z$r_buff0_thd2 = z$w_buff0_used && z$r_buff0_thd2 ? (_Bool)0 : z$r_buff0_thd2;
  z$r_buff1_thd2 = z$w_buff0_used && z$r_buff0_thd2 || z$w_buff1_used && z$r_buff1_thd2 ? (_Bool)0 : z$r_buff1_thd2;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();
  __unbuffered_cnt = __unbuffered_cnt + 1;
  __VERIFIER_atomic_end();
  return 0;
}



void fence()
{

}



void isync()
{

}



void lwfence()
{

}



int main()
{
  pthread_t t1089;
  pthread_create(&t1089, 
# 241 "false-negative-17.c" 3 4
                        ((void *)0)
# 241 "false-negative-17.c"
                            , P0, 
# 241 "false-negative-17.c" 3 4
                                  ((void *)0)
# 241 "false-negative-17.c"
                                      );
  pthread_t t1090;
  pthread_create(&t1090, 
# 243 "false-negative-17.c" 3 4
                        ((void *)0)
# 243 "false-negative-17.c"
                            , P1, 
# 243 "false-negative-17.c" 3 4
                                  ((void *)0)
# 243 "false-negative-17.c"
                                      );
  __VERIFIER_atomic_begin();
  main$tmp_guard0 = __unbuffered_cnt == 2;
  __VERIFIER_atomic_end();
  assume_abort_if_not(main$tmp_guard0);
  __VERIFIER_atomic_begin();
  z = z$w_buff0_used && z$r_buff0_thd0 ? z$w_buff0 : (z$w_buff1_used && z$r_buff1_thd0 ? z$w_buff1 : z);
  z$w_buff0_used = z$w_buff0_used && z$r_buff0_thd0 ? (_Bool)0 : z$w_buff0_used;
  z$w_buff1_used = z$w_buff0_used && z$r_buff0_thd0 || z$w_buff1_used && z$r_buff1_thd0 ? (_Bool)0 : z$w_buff1_used;
  z$r_buff0_thd0 = z$w_buff0_used && z$r_buff0_thd0 ? (_Bool)0 : z$r_buff0_thd0;
  z$r_buff1_thd0 = z$w_buff0_used && z$r_buff0_thd0 || z$w_buff1_used && z$r_buff1_thd0 ? (_Bool)0 : z$r_buff1_thd0;
  __VERIFIER_atomic_end();
  __VERIFIER_atomic_begin();

  main$tmp_guard1 = !(x == 2 && __unbuffered_p1_EAX == 1 && __unbuffered_p1_EBX == 0);
  __VERIFIER_atomic_end();

  __VERIFIER_assert(main$tmp_guard1);
  return 0;
}
