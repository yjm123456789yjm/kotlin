# include "documentation_examples.h"
# include "posix_invoke.h"
# include "stddef.h"

#if __APPLE__
   # include "macos.h"
#elif __linux__
   # include "linux.h"
#elif _WIN32
   # include "windows.h"
#endif



#if __APPLE__
    int max(int num1, int num2);
#elif __linux__
    int max(int num1, int num2);

#elif _WIN32
        //code...
#endif

int updatedHeaderFile();

int foo();


int *b;
const int *f(void);
enum COLOR {RED, GREEN, BLUE} c;



struct X;
struct Y { int n; };

extern int n;
const size_t N;


struct parent {
    int  foo;
    char *bar;
};

struct child {
    struct parent base;
    int bar;
};

#if __APPLE__
    print1(struct parent a);
    print2(int a);
    int a;

#elif __linux__
    print1(struct child a);
    print2(int const a);
    int const a;

#elif _WIN32
    print2(int a);

#endif


///typealiases
typedef int value_t;



#if __APPLE__
    typedef struct {
      int a;
      double c;
    } MyStructApple;
    typedef value_t data_t_apple;
    typedef value_t data_t_common;
    typedef value_t data_t_common2;
    typedef MyStructApple CommonAlias;

    typedef int alias;

    void print_n(int n);
    typedef long printer_t;

    typedef void (*printer_a)(int);
    printer_a p = &print_n;


    typedef struct {
          printer_t a;
          data_t_apple c;
    } MyStructFollowAliasForField;

#elif __linux__
    typedef struct {
      int b;

    } MyStructLinux;
    typedef value_t data_t_linux;
    typedef int data_t_common;
    typedef MyStructLinux CommonAlias;
    value_t data_t_common2();
    int alias();

    void print_n(int n);
    typedef int printer_t;

    typedef void (*printer_a)(int);
    printer_a p = &print_n;

    typedef struct {
        printer_t a;
        data_t_linux c;
    } MyStructFollowAliasForField;


#elif _WIN32
    typedef value_t data_t_windows;
    int data_t_common();
    int alias();
#endif



/*

// Target A
class X
fun useX(x: X)
// Target B
class B
typealias X = B
fun useX(x: X)

*/

#if __APPLE__
    struct X { int n; };
    typedef struct X C;
    C useX(C s, int k);

    typedef int ss;
    ss useY(ss s, int k);
#elif __linux__
    struct X { int n; };
    typedef struct X B;
    B useX( B s, int k);

    int useY(int s, int k);

#elif _WIN32
#endif