// Built-in replay_controller skip prompt, verified against the supplied script archive.
static bool mission_gate(int64_t state,int64_t available,int64_t whole) {
    return (state==1||state==2)&&available==1&&whole==1;
}
static bool mission_read_global(unsigned index,int64_t *value) {
#ifdef BRIDGE_TEST
    extern bool test_mission_global(unsigned,int64_t *);
    return test_mission_global(index,value);
#else
    uintptr_t block=0;
    struct iovec local={&block,sizeof(block)},remote={(void *)(game+0x46cfc30),sizeof(block)};
    if(process_vm_readv(getpid(),&local,1,&remote,1,0)!=(ssize_t)sizeof(block)||!block||(block&7))return false;
    if(index>=262144||block>UINTPTR_MAX-(uintptr_t)index*8)return false;
    local=(struct iovec){value,sizeof(*value)};remote=(struct iovec){(void *)(block+(uintptr_t)index*8),sizeof(*value)};
    return process_vm_readv(getpid(),&local,1,&remote,1,0)==(ssize_t)sizeof(*value);
#endif
}
static void mission_skip_prompt(void) {
    int64_t state,available,whole;
    if(!mission_read_global(100441,&state)||!mission_read_global(94617,&available)||!mission_read_global(94619,&whole)) {
        finish("FAILED","Mission state is unavailable");return;
    }
    if(!mission_gate(state,available,whole)) {
        finish("FAILED","Use the mission-failed screen when the game offers Skip mission; section-only skips are not supported");return;
    }
    if(!C3(SET_CONTROL_VALUE_NEXT_FRAME,2,203,farg(1.0f))) {
        finish("FAILED","The game rejected the skip input");return;
    }
    finish("CALLED","Skip input sent. Confirm in the game if its Skip mission prompt appears; completion is not yet confirmed.");
}
