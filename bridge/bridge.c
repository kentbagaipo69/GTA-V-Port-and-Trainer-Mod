#define _GNU_SOURCE
#include <android/native_activity.h>
#include <android/log.h>
#include <arpa/inet.h>
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <link.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <time.h>
#include <unistd.h>
#include "bridge_config.h"
#include "native_guards.h"
#include "private_hook_profile.h"
#include "vehicle_spawn.h"
#include "money_rules.h"
#include "wardrobe_rules.h"
#include "destinations.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"TrainerBridge",__VA_ARGS__)
#define COUNT(a) (sizeof(a)/sizeof((a)[0]))
typedef struct { uint64_t *result; uint32_t count, pad; uint64_t *args; uint8_t reserved[256]; } NativeInfo;
static uintptr_t game;
static void (*original_wait)(NativeInfo *);
static pthread_mutex_t mutex=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t ready=PTHREAD_COND_INITIALIZER;
static atomic_bool hook_ready=false;
static atomic_llong last_tick=0;
static char init_error[160]="Bridge initializing";
static struct {
    bool active, done, model_requested;
    uintptr_t owner;
    int64_t deadline;
    uint32_t model;
    char action[80], item[96], reply[256];
} pending;
static bool god_mode, no_reload, infinite_ammo, no_traffic;
static bool invisible, super_jump, no_ragdoll, never_wanted, fast_run, fast_swim, paused_clock;
static bool money_locks[3];
static int64_t last_money_check;
static atomic_bool money_lock_failed=false;
static const char *money_stats[]={"SP0_TOTAL_CASH","SP1_TOTAL_CASH","SP2_TOTAL_CASH"};
static const char *money_names[]={"Michael","Franklin","Trevor"};
static bool saved_position;
static uint64_t saved_coords[4];
static uint64_t saved_heading;
static const char *weather_names[]={"EXTRASUNNY","CLEAR","CLOUDS","OVERCAST","RAIN","THUNDER","SMOG","FOGGY"};
static int weather_index, clock_index;
static int cached_ped;
static uint64_t last_effect_frame;
static bool effect_frame_valid;
static _Thread_local bool in_hook;

static int64_t now_ms(void) {
    struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t);
    return (int64_t)t.tv_sec*1000+t.tv_nsec/1000000;
}
static uint64_t farg(float value) { uint32_t v; memcpy(&v,&value,4); return v; }
static float as_float(uint64_t v) { float f; uint32_t b=(uint32_t)v; memcpy(&f,&b,4); return f; }
static void invoke(uintptr_t offset, uint64_t *result, const uint64_t *args, unsigned count) {
#ifdef BRIDGE_TEST
    extern void test_invoke(uintptr_t, uint64_t *, const uint64_t *, unsigned);
    test_invoke(offset,result,args,count);return;
#endif
    uint64_t slots[24]={0};
    if (count>COUNT(slots)) return;
    if (count) memcpy(slots,args,count*8);
    NativeInfo info={0}; info.result=result; info.args=slots; info.count=count;
    ((void (*)(NativeInfo *))(game+offset))(&info);
}
static uint64_t call(uintptr_t offset, const uint64_t *args, unsigned count) {
    uint64_t results[4]={0}; invoke(offset,results,args,count); return results[0];
}
#define C0(n) call(N_##n,NULL,0)
#define C1(n,a) call(N_##n,(uint64_t[]){(uint64_t)(a)},1)
#define C2(n,a,b) call(N_##n,(uint64_t[]){(uint64_t)(a),(uint64_t)(b)},2)
#define C3(n,a,b,c) call(N_##n,(uint64_t[]){(uint64_t)(a),(uint64_t)(b),(uint64_t)(c)},3)
#define C4(n,a,b,c,d) call(N_##n,(uint64_t[]){(uint64_t)(a),(uint64_t)(b),(uint64_t)(c),(uint64_t)(d)},4)
#define C5(n,a,b,c,d,e) call(N_##n,(uint64_t[]){(uint64_t)(a),(uint64_t)(b),(uint64_t)(c),(uint64_t)(d),(uint64_t)(e)},5)
static uint32_t hash(const char *s) {
    uint32_t h=0; for(;*s;s++){unsigned char c=*s; if(c>='A'&&c<='Z')c+=32; h+=c; h+=h<<10; h^=h>>6;}
    h+=h<<3; h^=h>>11; h+=h<<15; return h;
}
static bool listed(const char *s,const char **list,size_t count) {
    for(size_t i=0;i<count;i++)if(!strcmp(s,list[i]))return true; return false;
}
static void finish(const char *status,const char *message) {
    snprintf(pending.reply,sizeof(pending.reply),"%s\t%s\n",status,message);
    pending.done=true; pending.active=false;
    LOG("%s: %s",pending.action,message);
    pthread_cond_signal(&ready);
}
static void checked(bool success,const char *message) {
    finish(success?"APPLIED":"FAILED",success?message:"The game did not confirm the requested change");
}
static bool is_action(const char *name) { return !strcmp(pending.action,name); }
#include "wardrobe_impl.h"
#include "mission_impl.h"

static void apply_ammo(int ped) {
    for(size_t i=0;i<COUNT(weapon_ids);i++) {
        uint32_t h=hash(weapon_ids[i]);
        if(C1(IS_WEAPON_VALID,h)) C3(SET_PED_INFINITE_AMMO,ped,infinite_ammo,h);
    }
}
static bool read_cash(int profile,int *cash) {
    *cash=0;
    return C3(STAT_GET_INT,hash(money_stats[profile]),(uintptr_t)cash,-1)!=0 && *cash>=0;
}
static bool write_cash(int profile,int amount,bool save) {
    if(!C3(STAT_SET_INT,hash(money_stats[profile]),amount,save))return false;
    int after;
    return read_cash(profile,&after)&&after==amount;
}
static void refill_cash(void) {
    if(now_ms()-last_money_check<1000)return;
    last_money_check=now_ms();
    for(int p=0;p<3;p++)if(money_locks[p]) {
        int cash;
        if(!read_cash(p,&cash)||(cash<MONEY_FLOOR&&!write_cash(p,MONEY_FLOOR,false))) {
            money_locks[p]=false;atomic_store(&money_lock_failed,true);
            LOG("Cash refill disabled for %s: stat access failed",money_names[p]);
        }
    }
}
static void process_selection(int ped,int player,uintptr_t owner) {
    if(is_action("mission.skip")){mission_skip_prompt();return;}
    if(is_action("clothing.inspect")||is_action("clothing.apply")||is_action("clothing.remove_prop")) {
        wardrobe_selection(ped);return;
    }
    if(is_action("teleport.destination")) {
        float x=0,y=0,z=0;
        if(!strcmp(pending.item,"waypoint")) {
            if(!C0(IS_WAYPOINT_ACTIVE)){finish("FAILED","Place a waypoint on the game map first");return;}
            int blip=(int)C1(GET_FIRST_BLIP_INFO_ID,8);
            if(!C1(DOES_BLIP_EXIST,blip)){finish("FAILED","Map waypoint unavailable");return;}
            uint64_t pos[4]={0};invoke(N_GET_BLIP_INFO_ID_COORD,pos,(uint64_t[]){blip},1);
            x=as_float(pos[0]);y=as_float(pos[1]);
            if(!isfinite(x)||!isfinite(y)){finish("FAILED","Invalid waypoint coordinates");return;}
            C3(REQUEST_COLLISION_AT_COORD,farg(x),farg(y),farg(1000));
            uint64_t args[]={farg(x),farg(y),farg(1000),(uintptr_t)&z,0,0};
            if(!call(N_GET_GROUND_Z_FOR_3D_COORD,args,6)) {
                finish("FAILED","Waypoint ground is not loaded. Choose a named destination nearby first");return;
            }
        } else {
            bool found=false;
            for(size_t i=0;i<COUNT(destinations);i++)if(!strcmp(pending.item,destinations[i].id)) {
                x=destinations[i].x;y=destinations[i].y;z=destinations[i].z;found=true;break;
            }
            if(!found){finish("FAILED","Unknown destination");return;}
        }
        if(!isfinite(x)||!isfinite(y)||!isfinite(z)){finish("FAILED","Invalid destination coordinates");return;}
        int vehicle=(int)C2(GET_VEHICLE_PED_IS_IN,ped,0);
        int entity=vehicle&&C1(DOES_ENTITY_EXIST,vehicle)?vehicle:ped;
        C3(REQUEST_COLLISION_AT_COORD,farg(x),farg(y),farg(z));
        C4(SET_ENTITY_VELOCITY,entity,farg(0),farg(0),farg(0));
        uint64_t args[]={entity,farg(x),farg(y),farg(z+0.5f),0,0,1};call(N_SET_ENTITY_COORDS_NO_OFFSET,args,7);
        finish("CALLED","Teleport requested with your current vehicle; allow the destination to load");return;
    }
    if(!strncmp(pending.action,"money.",6)) {
        if(is_action("money.balances")) {
            int cash[3];
            if(!read_cash(0,&cash[0])||!read_cash(1,&cash[1])||!read_cash(2,&cash[2])) {
                finish("FAILED","Story cash stats unavailable; load a story save first");return;
            }
            char message[190];snprintf(message,sizeof(message),"Michael $%d / Franklin $%d / Trevor $%d",cash[0],cash[1],cash[2]);
            finish("APPLIED",message);return;
        }
        int p=money_profile(pending.action);
        if(p<0){finish("FAILED","Unknown story character");return;}
        if(!strncmp(pending.action,"money.unlimited_",16)&&!strcmp(pending.item,"off")) {
            money_locks[p]=false;
            char message[120];snprintf(message,sizeof(message),"%s cash refill stopped; current balance retained",money_names[p]);
            finish("APPLIED",message);return;
        }
        int cash;
        if(!read_cash(p,&cash)){finish("FAILED","Selected character cash stat is unavailable");return;}
        bool refill=!strncmp(pending.action,"money.unlimited_",16);
        int desired=refill?(cash<MONEY_FLOOR?MONEY_FLOOR:cash):money_add_clamped(cash,atoi(pending.item));
        if(desired<0||!write_cash(p,desired,true)){finish("FAILED","Cash update was not confirmed by the game");return;}
        if(refill){money_locks[p]=true;atomic_store(&money_lock_failed,false);}
        char message[190];
        snprintf(message,sizeof(message),refill?"%s: $%d confirmed; refills to $1 billion while enabled":"%s balance confirmed: $%d",money_names[p],desired);
        finish("APPLIED",message);return;
    }
    bool vehicle=!strncmp(pending.action,"vehicles.spawn_",15);
    bool character=!strncmp(pending.action,"characters.",11);
    if(vehicle || character) {
        if(!pending.model_requested) {
            const char *model=pending.item;
            if(is_action("vehicles.spawn_car"))model="adder";
            else if(is_action("vehicles.spawn_motorcycle"))model="bati";
            else if(is_action("vehicles.spawn_helicopter"))model="buzzard";
            else if(is_action("characters.michael"))model="player_zero";
            else if(is_action("characters.franklin"))model="player_one";
            else if(is_action("characters.trevor"))model="player_two";
            else if(is_action("characters.npc_model"))model="a_m_y_business_01";
            pending.model=hash(model);
            if(!C1(IS_MODEL_IN_CDIMAGE,pending.model) || !C1(IS_MODEL_VALID,pending.model)
                    || !(vehicle?C1(IS_MODEL_A_VEHICLE,pending.model):C1(IS_MODEL_A_PED,pending.model))) {
                finish("FAILED","This model is unavailable in this game build"); return;
            }
            pending.owner=owner;
            C1(REQUEST_MODEL,pending.model);
            pending.model_requested=true;
            return;
        }
        if(pending.owner!=owner)return;
        if(!C1(HAS_MODEL_LOADED,pending.model))return;
        if(character) {
            C2(SET_PLAYER_MODEL,player,pending.model);
            int new_ped=(int)C0(PLAYER_PED_ID);
            if(new_ped && C1(DOES_ENTITY_EXIST,new_ped)) C1(SET_PED_DEFAULT_COMPONENT_VARIATION,new_ped);
            bool ok=new_ped && (uint32_t)C1(GET_ENTITY_MODEL,new_ped)==pending.model;
            C1(SET_MODEL_AS_NO_LONGER_NEEDED,pending.model);
            checked(ok,"Character model changed");
        } else {
            uint64_t pos[4]={0}; invoke(N_GET_ENTITY_COORDS,pos,(uint64_t[]){ped,1},2);
            float heading=as_float(C1(GET_ENTITY_HEADING,ped));
            float radians=heading*0.01745329252f;
            float distance=5.0f;bool water=false;
            for(size_t i=0;i<COUNT(spawn_info);i++)if(hash(spawn_info[i].id)==pending.model) {
                distance=spawn_info[i].distance;water=spawn_info[i].water;break;
            }
            float x=as_float(pos[0])-sinf(radians)*distance;
            float y=as_float(pos[1])+cosf(radians)*distance;
            float z=as_float(pos[2])+1.0f;
            if(!isfinite(x)||!isfinite(y)||!isfinite(z)||!isfinite(heading)) {finish("FAILED","Player coordinates unavailable");return;}
            if(water) {
                float surface=0;
                if(!C4(GET_WATER_HEIGHT,farg(x),farg(y),farg(z+50.0f),(uintptr_t)&surface)||!isfinite(surface)) {
                    C1(SET_MODEL_AS_NO_LONGER_NEEDED,pending.model);
                    finish("FAILED","No water ahead. Move near open water and face it before spawning this vessel");return;
                }
                z=surface+0.5f;
            }
            uint64_t args[]={pending.model,farg(x),farg(y),farg(z),farg(heading),0,0,0};
            int entity=(int)call(N_CREATE_VEHICLE,args,8);
            bool ok=entity && C1(DOES_ENTITY_EXIST,entity);
            C1(SET_MODEL_AS_NO_LONGER_NEEDED,pending.model);
            checked(ok,water?"Vessel created on water ahead":"Vehicle created ahead with space for its size");
        }
        return;
    }
    if(is_action("weapons.give_selected")) {
        uint32_t weapon=hash(pending.item);
        if(!C1(IS_WEAPON_VALID,weapon)){finish("FAILED","Weapon is unavailable in this game build");return;}
        C5(GIVE_WEAPON_TO_PED,ped,weapon,999,0,1);
        checked(C3(HAS_PED_GOT_WEAPON,ped,weapon,0)!=0,"Selected weapon added and equipped");
    } else if(is_action("weapons.give_all")) {
        unsigned applied=0;
        for(size_t i=0;i<COUNT(weapon_ids);i++) {
            uint32_t weapon=hash(weapon_ids[i]); if(!C1(IS_WEAPON_VALID,weapon))continue;
            C5(GIVE_WEAPON_TO_PED,ped,weapon,999,0,0);
            if(C3(HAS_PED_GOT_WEAPON,ped,weapon,0))applied++;
        }
        char message[100];snprintf(message,sizeof(message),"Game confirms %u weapons in inventory",applied);
        checked(applied>0,message);
    } else if(is_action("weapons.no_reload")) {
        no_reload=!no_reload; C2(SET_PED_INFINITE_AMMO_CLIP,ped,no_reload);
        finish("CALLED",no_reload?"No reload enabled; test by firing":"No reload disabled");
    } else if(is_action("weapons.unlimited_ammo")) {
        infinite_ammo=!infinite_ammo; apply_ammo(ped);
        finish("CALLED",infinite_ammo?"Unlimited ammo enabled; test by firing":"Unlimited ammo disabled");
    } else if(is_action("player.reset_effects")) {
        god_mode=false; no_reload=false; infinite_ammo=false; no_traffic=false;
        invisible=false;super_jump=false;no_ragdoll=false;never_wanted=false;fast_run=false;fast_swim=false;paused_clock=false;
        memset(money_locks,0,sizeof(money_locks));atomic_store(&money_lock_failed,false);
        C2(SET_PLAYER_INVINCIBLE,player,0);
        C2(SET_PED_INFINITE_AMMO_CLIP,ped,0);
        C3(SET_ENTITY_VISIBLE,ped,1,0);
        C2(SET_PED_CAN_RAGDOLL,ped,1);
        C2(SET_RUN_SPRINT_MULTIPLIER_FOR_PLAYER,player,farg(1.0f));
        C2(SET_SWIM_MULTIPLIER_FOR_PLAYER,player,farg(1.0f));
        C1(PAUSE_CLOCK,0);
        apply_ammo(ped);
        finish("CALLED","Trainer toggles and cash refill stopped; balances and previous game changes retained");
    } else if(is_action("player.invisible")) {
        invisible=!invisible;C3(SET_ENTITY_VISIBLE,ped,!invisible,0);
        checked((C1(IS_ENTITY_VISIBLE,ped)!=0)==!invisible,invisible?"Player invisible":"Player visible");
    } else if(is_action("player.super_jump")) {
        super_jump=!super_jump;finish("CALLED",super_jump?"Super jump enabled":"Super jump disabled");
    } else if(is_action("player.no_ragdoll")) {
        no_ragdoll=!no_ragdoll;C2(SET_PED_CAN_RAGDOLL,ped,!no_ragdoll);
        checked((C1(CAN_PED_RAGDOLL,ped)!=0)==!no_ragdoll,no_ragdoll?"Ragdoll disabled":"Ragdoll enabled");
    } else if(is_action("player.never_wanted")) {
        never_wanted=!never_wanted;
        if(never_wanted)C1(CLEAR_PLAYER_WANTED_LEVEL,player);
        finish("CALLED",never_wanted?"Wanted level will be cleared while enabled":"Normal wanted behavior restored");
    } else if(is_action("player.fast_run")) {
        fast_run=!fast_run;C2(SET_RUN_SPRINT_MULTIPLIER_FOR_PLAYER,player,farg(fast_run?1.49f:1.0f));
        finish("CALLED",fast_run?"Run multiplier set to 1.49":"Normal run speed restored");
    } else if(is_action("player.fast_swim")) {
        fast_swim=!fast_swim;C2(SET_SWIM_MULTIPLIER_FOR_PLAYER,player,farg(fast_swim?1.49f:1.0f));
        finish("CALLED",fast_swim?"Swim multiplier set to 1.49":"Normal swim speed restored");
    } else if(is_action("weapons.remove_all")) {
        C2(REMOVE_ALL_PED_WEAPONS,ped,1);finish("CALLED","Weapon removal requested");
    } else if(is_action("player.god_mode")) {
        god_mode=!god_mode; C2(SET_PLAYER_INVINCIBLE,player,god_mode);
        checked((C1(GET_PLAYER_INVINCIBLE,player)!=0)==god_mode,god_mode?"God mode enabled":"God mode disabled");
    } else if(is_action("player.max_health")) {
        int health=(int)C1(GET_ENTITY_MAX_HEALTH,ped);
        C4(SET_ENTITY_HEALTH,ped,health,0,0);
        checked((int)C1(GET_ENTITY_HEALTH,ped)>=health,"Health restored");
    } else if(is_action("player.max_armor")) {
        C2(SET_PED_ARMOUR,ped,100); checked((int)C1(GET_PED_ARMOUR,ped)>=100,"Armor set to 100");
    } else if(is_action("player.clear_wanted")) {
        C1(CLEAR_PLAYER_WANTED_LEVEL,player); checked(C1(GET_PLAYER_WANTED_LEVEL,player)==0,"Wanted level cleared");
    } else if(is_action("vehicles.repair")) {
        int entity=(int)C2(GET_VEHICLE_PED_IS_IN,ped,0);
        if(!entity || !C1(DOES_ENTITY_EXIST,entity)){finish("FAILED","Enter a vehicle first");return;}
        C1(SET_VEHICLE_FIXED,entity); C2(SET_VEHICLE_ENGINE_HEALTH,entity,farg(1000));
        C2(SET_VEHICLE_PETROL_TANK_HEALTH,entity,farg(1000));
        finish("CALLED","Vehicle repair requested; check the vehicle");
    } else if(is_action("vehicles.clean")||is_action("vehicles.flip")||is_action("vehicles.engine")) {
        int vehicle=(int)C2(GET_VEHICLE_PED_IS_IN,ped,0);
        if(!vehicle||!C1(DOES_ENTITY_EXIST,vehicle)){finish("FAILED","Enter a vehicle first");return;}
        if(is_action("vehicles.clean")) {
            C2(SET_VEHICLE_DIRT_LEVEL,vehicle,farg(0));
            checked(as_float(C1(GET_VEHICLE_DIRT_LEVEL,vehicle))<0.1f,"Vehicle washed");
        } else if(is_action("vehicles.flip")) {
            checked(C2(SET_VEHICLE_ON_GROUND_PROPERLY,vehicle,farg(5.0f))!=0,"Vehicle placed on ground");
        } else {
            bool on=C1(GET_IS_VEHICLE_ENGINE_RUNNING,vehicle)==0;
            C4(SET_VEHICLE_ENGINE_ON,vehicle,on,1,!on);
            finish("CALLED",on?"Engine start requested":"Engine stop requested");
        }
    } else if(is_action("teleport.save")||is_action("teleport.return")) {
        int vehicle=(int)C2(GET_VEHICLE_PED_IS_IN,ped,0);
        int entity=vehicle&&C1(DOES_ENTITY_EXIST,vehicle)?vehicle:ped;
        if(is_action("teleport.save")) {
            invoke(N_GET_ENTITY_COORDS,saved_coords,(uint64_t[]){entity,1},2);
            saved_heading=C1(GET_ENTITY_HEADING,entity);
            saved_position=isfinite(as_float(saved_coords[0]))&&isfinite(as_float(saved_coords[1]))&&isfinite(as_float(saved_coords[2]));
            checked(saved_position,"Current position remembered until game restart");
        } else {
            if(!saved_position){finish("FAILED","Remember a position first in this game session");return;}
            C3(REQUEST_COLLISION_AT_COORD,saved_coords[0],saved_coords[1],saved_coords[2]);
            uint64_t args[]={entity,saved_coords[0],saved_coords[1],saved_coords[2],0,0,1};
            call(N_SET_ENTITY_COORDS_NO_OFFSET,args,7);C2(SET_ENTITY_HEADING,entity,saved_heading);
            finish("CALLED","Return to saved position requested; wait for the area to load");
        }
    } else if(!strncmp(pending.action,"clothing.",9)) {
        if(is_action("clothing.outfit")) {
            C2(SET_PED_RANDOM_COMPONENT_VARIATION,ped,0);
            finish("CALLED","Random outfit requested for the current character");return;
        }
        int component=is_action("clothing.shirt")?11:is_action("clothing.pants")?4:is_action("clothing.shoes")?6:7;
        int count=(int)C2(GET_NUMBER_OF_PED_DRAWABLE_VARIATIONS,ped,component);
        if(count<2){finish("FAILED","No alternate clothing available for this character");return;}
        int next=((int)C2(GET_PED_DRAWABLE_VARIATION,ped,component)+1)%count;
        C5(SET_PED_COMPONENT_VARIATION,ped,component,next,0,0);
        checked((int)C2(GET_PED_DRAWABLE_VARIATION,ped,component)==next,"Next clothing variation selected");
    } else if(is_action("world.weather")||is_action("world.weather_selected")) {
        const char *weather=is_action("world.weather_selected")?pending.item:weather_names[weather_index++%COUNT(weather_names)];
        C1(SET_WEATHER_TYPE_NOW_PERSIST,(uintptr_t)weather);
        char message[100];snprintf(message,sizeof(message),"Weather requested: %s",weather); finish("CALLED",message);
    } else if(is_action("world.time")||is_action("world.time_selected")) {
        static const int hours[]={6,12,18,0};
        int hour=is_action("world.time_selected")?atoi(pending.item):hours[clock_index++%COUNT(hours)];
        C3(SET_CLOCK_TIME,hour,0,0);
        char message[100];snprintf(message,sizeof(message),"Time set to %02d:00",hour);
        checked((int)C0(GET_CLOCK_HOURS)==hour,message);
    } else if(is_action("world.pause_time")) {
        paused_clock=!paused_clock;C1(PAUSE_CLOCK,paused_clock);
        finish("CALLED",paused_clock?"Clock paused":"Clock resumed");
    } else if(is_action("world.traffic")) {
        no_traffic=!no_traffic;
        finish("CALLED",no_traffic?"Traffic spawning suppressed; existing vehicles remain":"Normal traffic spawning restored");
    } else finish("FAILED","Unsupported command");
}

static void wait_hook(NativeInfo *info) {
    if(!in_hook) {
        in_hook=true;
        atomic_store(&last_tick,now_ms());
        if(pthread_mutex_trylock(&mutex)==0) {
            bool needs_work=pending.active||god_mode||no_reload||infinite_ammo||no_traffic
                    ||invisible||super_jump||no_ragdoll||never_wanted||fast_run||fast_swim
                    ||money_locks[0]||money_locks[1]||money_locks[2];
            if(needs_work) {
                uint64_t frame=C0(GET_FRAME_COUNT);
                bool update_effects=!effect_frame_valid||frame!=last_effect_frame;
                // Commands must still visit their owning script on repeated WAITs.
                if(update_effects||pending.active) {
                if(C0(NETWORK_IS_SESSION_ACTIVE)) {
                    if(pending.active)finish("FAILED","Experimental bridge is for offline story mode");
                } else {
                    int ped=(int)C0(PLAYER_PED_ID),player=(int)C0(PLAYER_ID);
                    if(ped && C1(DOES_ENTITY_EXIST,ped)) {
                        if(update_effects) {
                        last_effect_frame=frame;effect_frame_valid=true;
                        if(money_locks[0]||money_locks[1]||money_locks[2])refill_cash();
                        if(god_mode)C2(SET_PLAYER_INVINCIBLE,player,1);
                        if(no_reload)C2(SET_PED_INFINITE_AMMO_CLIP,ped,1);
                        if(invisible)C3(SET_ENTITY_VISIBLE,ped,0,0);
                        if(super_jump)C1(SET_SUPER_JUMP_THIS_FRAME,player);
                        if(no_ragdoll)C2(SET_PED_CAN_RAGDOLL,ped,0);
                        if(never_wanted)C1(CLEAR_PLAYER_WANTED_LEVEL,player);
                        if(fast_run)C2(SET_RUN_SPRINT_MULTIPLIER_FOR_PLAYER,player,farg(1.49f));
                        if(fast_swim)C2(SET_SWIM_MULTIPLIER_FOR_PLAYER,player,farg(1.49f));
                        if(infinite_ammo&&cached_ped!=ped)apply_ammo(ped);
                        cached_ped=ped;
                        if(no_traffic) {
                            C1(SET_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME,farg(0));
                            C1(SET_RANDOM_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME,farg(0));
                            C1(SET_PARKED_VEHICLE_DENSITY_MULTIPLIER_THIS_FRAME,farg(0));
                        }
                        }
                        if(pending.active) {
                            if(now_ms()>=pending.deadline)finish("FAILED","Command expired before the game completed it");
                            else process_selection(ped,player,*(uintptr_t *)(game+0x46d0a88));
                        }
                    }
                }
                }
            }
            pthread_mutex_unlock(&mutex);
        }
        in_hook=false;
    }
    original_wait(info);
}

static bool valid_command(const char *action,const char *item) {
    if(!strcmp(action,"clothing.inspect")||!strcmp(action,"clothing.apply")||!strcmp(action,"clothing.remove_prop")) {
        WardrobeChoice w;
        if(!wardrobe_parse(item,&w))return false;
        if(!strcmp(action,"clothing.inspect"))return w.texture==0;
        if(!w.model)return false;
        return strcmp(action,"clothing.remove_prop")||(w.prop&&w.drawable==0&&w.texture==0);
    }
    if(!strcmp(action,"teleport.destination")) {
        if(!strcmp(item,"waypoint"))return true;
        for(size_t i=0;i<COUNT(destinations);i++)if(!strcmp(item,destinations[i].id))return true;
        return false;
    }
    if(!strcmp(action,"characters.select_model"))return listed(item,ped_ids,COUNT(ped_ids));
    if(!strncmp(action,"money.add_",10))return money_profile(action)>=0
            &&(!strcmp(item,"100000")||!strcmp(item,"1000000")||!strcmp(item,"10000000"));
    if(!strncmp(action,"money.unlimited_",16))return money_profile(action)>=0&&(!strcmp(item,"on")||!strcmp(item,"off"));
    if(!strcmp(action,"weapons.give_selected"))return listed(item,weapon_ids,COUNT(weapon_ids));
    if(!strcmp(action,"vehicles.spawn_selected"))return listed(item,vehicle_ids,COUNT(vehicle_ids));
    if(!strcmp(action,"world.weather_selected"))return listed(item,weather_names,COUNT(weather_names));
    if(!strcmp(action,"world.time_selected")) {
        if(!*item||strlen(item)>2)return false;
        for(const char *p=item;*p;p++)if(*p<'0'||*p>'9')return false;
        return atoi(item)<24;
    }
    if(*item)return false;
    static const char *actions[]={"characters.michael","characters.franklin","characters.trevor","characters.npc_model",
        "clothing.outfit","clothing.shirt","clothing.pants","clothing.shoes","clothing.accessories",
        "weapons.give_all","weapons.unlimited_ammo","weapons.no_reload",
        "vehicles.spawn_car","vehicles.spawn_motorcycle","vehicles.spawn_helicopter","vehicles.repair",
        "player.god_mode","player.max_health","player.max_armor","player.clear_wanted","player.reset_effects",
        "player.invisible","player.super_jump","player.no_ragdoll","player.never_wanted","player.fast_run","player.fast_swim",
        "weapons.remove_all","vehicles.clean","vehicles.flip","vehicles.engine","teleport.save","teleport.return",
        "mission.skip","world.weather","world.time","world.traffic","world.pause_time","money.balances"};
    return listed(action,actions,COUNT(actions));
}
static void send_reply(int fd,const char *reply) {
    size_t len=strlen(reply),done=0;
    while(done<len){ssize_t n=send(fd,reply+done,len-done,MSG_NOSIGNAL);if(n<=0)break;done+=(size_t)n;}
}
static void handle_client(int fd) {
    struct timeval timeout={3,0};
    setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
    setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
    char line[384];size_t len=0;
    while(len<sizeof(line)-1) {char c;ssize_t n=recv(fd,&c,1,0);if(n!=1)return;if(c=='\n')break;if(c=='\r'||c=='\0')return;line[len++]=c;}
    if(len==sizeof(line)-1)return;line[len]=0;
    char *sep=strchr(line,'\t');if(!sep)return;*sep++=0;
    if(strcmp(line,BRIDGE_TOKEN)){send_reply(fd,"FAILED\tPairing mismatch: install the matching trainer and game builds\n");return;}
    char *item=strchr(sep,'\t');if(!item)return;*item++=0;
    if(strchr(item,'\t'))return;
    if(!strcmp(sep,"HELLO")&&!*item) {
        send_reply(fd,"BRIDGE\t2\tcom.thegwchr.fuzzy\t0.8.0\tba87ec8a59e9b10437208a9c271290730527e9f6\n");return;
    }
    if(!atomic_load(&hook_ready)){char reply[256];snprintf(reply,sizeof(reply),"FAILED\t%s\n",init_error);send_reply(fd,reply);return;}
    if(!strcmp(sep,"PING")&&!*item) {
        int64_t tick=atomic_load(&last_tick);
        if(tick&&now_ms()-tick<3000&&atomic_load(&money_lock_failed)) {
            send_reply(fd,"READY\tMoney: a cash refill stopped because the game rejected its stat update\n");return;
        }
        send_reply(fd,tick&&now_ms()-tick<3000?"READY\tBridge connected; game scripts are running\n":"WAITING\tBridge loaded; enter offline story mode and unpause\n");return;
    }
    if(!valid_command(sep,item)){send_reply(fd,"FAILED\tUnsupported command or item\n");return;}
    pthread_mutex_lock(&mutex);
    memset(&pending,0,sizeof(pending));
    snprintf(pending.action,sizeof(pending.action),"%s",sep);snprintf(pending.item,sizeof(pending.item),"%s",item);
    pending.active=true;pending.deadline=now_ms()+12000;
    struct timespec until;clock_gettime(CLOCK_REALTIME,&until);until.tv_sec+=12;
    int result=0;
    while(!pending.done&&result!=ETIMEDOUT)result=pthread_cond_timedwait(&ready,&mutex,&until);
    if(!pending.done){pending.active=false;snprintf(pending.reply,sizeof(pending.reply),"FAILED\tNo game response within 12 seconds; enter story mode and unpause\n");}
    char reply[256];snprintf(reply,sizeof(reply),"%s",pending.reply);
    pthread_mutex_unlock(&mutex);send_reply(fd,reply);
}
static void *server(void *unused) {
    (void)unused;int fd=socket(AF_INET,SOCK_STREAM|SOCK_CLOEXEC,0);
    if(fd<0){LOG("Socket creation failed: %s",strerror(errno));return NULL;}
    int yes=1;setsockopt(fd,SOL_SOCKET,SO_REUSEADDR,&yes,sizeof(yes));
    struct sockaddr_in address={.sin_family=AF_INET,.sin_port=htons(BRIDGE_PORT),.sin_addr.s_addr=htonl(INADDR_LOOPBACK)};
    if(bind(fd,(struct sockaddr *)&address,sizeof(address))||listen(fd,4)){LOG("Bridge port unavailable: %s",strerror(errno));close(fd);return NULL;}
    LOG("Bridge listening on loopback port %d",BRIDGE_PORT);
    for(;;){int client=accept4(fd,NULL,NULL,SOCK_CLOEXEC);if(client<0){if(errno==EINTR)continue;break;}handle_client(client);close(client);}
    close(fd);return NULL;
}

static bool correct_build(void) {
    static const unsigned char expected[]={0xba,0x87,0xec,0x8a,0x59,0xe9,0xb1,0x04,0x37,0x20,0x8a,0x9c,0x27,0x12,0x90,0x73,0x05,0x27,0xe9,0xf6};
    Elf64_Ehdr *elf=(Elf64_Ehdr *)game;
    if(memcmp(elf->e_ident,ELFMAG,4)||elf->e_machine!=EM_AARCH64)return false;
    Elf64_Phdr *headers=(Elf64_Phdr *)(game+elf->e_phoff);
    for(unsigned i=0;i<elf->e_phnum;i++) {
        if(headers[i].p_type!=PT_NOTE)continue;
        uintptr_t p=game+headers[i].p_vaddr,end=p+headers[i].p_memsz;
        while(p+sizeof(Elf64_Nhdr)<=end) {
            Elf64_Nhdr *n=(Elf64_Nhdr *)p;const char *name=(const char *)(p+12);
            uintptr_t desc=p+12+((n->n_namesz+3)&~3U),next=desc+((n->n_descsz+3)&~3U);
            if(next>end||next<=p)break;
            if(n->n_type==NT_GNU_BUILD_ID&&n->n_namesz==4&&!memcmp(name,"GNU",4))return n->n_descsz==20&&!memcmp((void *)desc,expected,20);
            p=next;
        }
    }
    return false;
}
static bool install_hook(void) {
    if(!correct_build()){snprintf(init_error,sizeof(init_error),"Unsupported game build; no native code patched");return false;}
    for(size_t i=0;i<COUNT(guards);i++)if(memcmp((void *)(game+guards[i].offset),guards[i].bytes,16)) {
        snprintf(init_error,sizeof(init_error),"Native function fingerprint mismatch; no native code patched");return false;
    }
    uint32_t expected[]=PRIVATE_WAIT_ENTRY_WORDS;
    void *target=(void *)(game+0x2e6fdcc);
    if(memcmp(target,expected,16)){snprintf(init_error,sizeof(init_error),"Script hook fingerprint mismatch; no native code patched");return false;}
    size_t page=(size_t)sysconf(_SC_PAGESIZE);
    unsigned char *trampoline=mmap(NULL,page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(trampoline==MAP_FAILED)return false;
    uint32_t code[]=PRIVATE_WAIT_TRAMPOLINE_WORDS;
    memcpy(trampoline,code,sizeof(code));
    *(uintptr_t *)(trampoline+24)=game+0x31df000;
    *(uintptr_t *)(trampoline+32)=(uintptr_t)target+16;
    __builtin___clear_cache((char *)trampoline,(char *)trampoline+40);
    if(mprotect(trampoline,page,PROT_READ|PROT_EXEC)){munmap(trampoline,page);return false;}
    uintptr_t first=(uintptr_t)target&~(page-1),last=((uintptr_t)target+16+page-1)&~(page-1);
    if(mprotect((void *)first,last-first,PROT_READ|PROT_WRITE|PROT_EXEC)){munmap(trampoline,page);return false;}
    original_wait=(void (*)(NativeInfo *))trampoline;
    uint32_t patch[4]={0x58000051,0xd61f0220,0,0};uintptr_t callback=(uintptr_t)wait_hook;memcpy(patch+2,&callback,8);
    memcpy(target,patch,16);__builtin___clear_cache(target,(char *)target+16);
    if(mprotect((void *)first,last-first,PROT_READ|PROT_EXEC))LOG("Could not restore original code-page protection: %s",strerror(errno));
    return true;
}

__attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity *activity,void *state,size_t size) {
    void *library=dlopen("libgtav.so",RTLD_NOW|RTLD_LOCAL);
    if(!library){LOG("Cannot load original game: %s",dlerror());ANativeActivity_finish(activity);return;}
    void (*start)(ANativeActivity *,void *,size_t)=dlsym(library,"ANativeActivity_onCreate");
    Dl_info info;
    if(!start||!dladdr((void *)start,&info)){LOG("Original game entry point unavailable");ANativeActivity_finish(activity);return;}
    game=(uintptr_t)info.dli_fbase;
    snprintf(init_error,sizeof(init_error),"Native hook unavailable: device did not permit executable mapping");
    atomic_store(&hook_ready,install_hook());
    LOG("Experimental bridge: %s",atomic_load(&hook_ready)?"script hook installed":init_error);
    pthread_t worker;if(!pthread_create(&worker,NULL,server,NULL))pthread_detach(worker);
    start(activity,state,size);
}
