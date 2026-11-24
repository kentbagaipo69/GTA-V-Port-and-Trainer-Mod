#pragma once
typedef struct { bool prop; unsigned slot, drawable, texture; uint32_t model; } WardrobeChoice;
static bool wardrobe_parse(const char *s, WardrobeChoice *out) {
    if (!s || (s[0]!='c' && s[0]!='p') || s[1]!='_') return false;
    bool prop=s[0]=='p'; s+=2;
    uint64_t values[4]={0};
    for(int i=0;i<4;i++) {
        int digits=0;
        while(*s>='0'&&*s<='9') {
            if(++digits>10)return false;
            values[i]=values[i]*10+(unsigned)(*s++-'0');
            if(values[i]>UINT32_MAX)return false;
        }
        if(!digits)return false;
        if(i<3){if(*s++!='_')return false;}else if(*s)return false;
    }
    if(values[0]>(prop?12:11)||values[1]>4095||values[2]>4095)return false;
    *out=(WardrobeChoice){prop,(unsigned)values[0],(unsigned)values[1],(unsigned)values[2],(uint32_t)values[3]};
    return true;
}
