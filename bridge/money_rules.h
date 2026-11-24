#include <limits.h>
#define MONEY_FLOOR 1000000000
static int money_add_clamped(int current,int amount) {
    if(current<0||amount<0)return -1;
    int64_t sum=(int64_t)current+amount;
    return sum>INT_MAX?INT_MAX:(int)sum;
}
static int money_profile(const char *action) {
    if(!strcmp(action,"money.add_michael")||!strcmp(action,"money.unlimited_michael"))return 0;
    if(!strcmp(action,"money.add_franklin")||!strcmp(action,"money.unlimited_franklin"))return 1;
    if(!strcmp(action,"money.add_trevor")||!strcmp(action,"money.unlimited_trevor"))return 2;
    return -1;
}
