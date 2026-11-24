static void wardrobe_selection(int ped) {
    WardrobeChoice w;
    if(!wardrobe_parse(pending.item,&w)){finish("FAILED","Invalid wardrobe selection");return;}
    uint32_t model=(uint32_t)C1(GET_ENTITY_MODEL,ped);
    bool query=is_action("clothing.inspect");
    if(w.model && w.model!=model){finish("FAILED","Character changed. Reload wardrobe options");return;}
    if(!query && !w.model){finish("FAILED","Load wardrobe options before applying");return;}
    if(is_action("clothing.remove_prop")) {
        C3(CLEAR_PED_PROP,ped,w.slot,0);
        checked((int)C3(GET_PED_PROP_INDEX,ped,w.slot,0)==-1,"Accessory removed");return;
    }
    int count=w.prop?(int)C2(GET_NUMBER_OF_PED_PROP_DRAWABLE_VARIATIONS,ped,w.slot)
            :(int)C2(GET_NUMBER_OF_PED_DRAWABLE_VARIATIONS,ped,w.slot);
    if(count<0||count>4096){finish("FAILED","Invalid wardrobe count from game");return;}
    if(count && w.drawable>=(unsigned)count){finish("FAILED","Variation unavailable. Reload wardrobe options");return;}
    int textures=count?(w.prop?(int)C3(GET_NUMBER_OF_PED_PROP_TEXTURE_VARIATIONS,ped,w.slot,w.drawable)
            :(int)C3(GET_NUMBER_OF_PED_TEXTURE_VARIATIONS,ped,w.slot,w.drawable)):0;
    if(textures<0||textures>4096){finish("FAILED","Invalid texture count from game");return;}
    if(query) {
        char message[180];snprintf(message,sizeof(message),"WARDROBE|%u|%c|%u|%u|%d|%d",model,w.prop?'p':'c',w.slot,w.drawable,count,textures);
        finish("APPLIED",message);return;
    }
    if(!count||!textures||w.texture>=(unsigned)textures){finish("FAILED","Selected texture is unavailable");return;}
    if(w.prop) {
        uint64_t args[]={ped,w.slot,w.drawable,w.texture,1,0};call(N_SET_PED_PROP_INDEX,args,6);
        checked((int)C3(GET_PED_PROP_INDEX,ped,w.slot,0)==(int)w.drawable
                &&(int)C2(GET_PED_PROP_TEXTURE_INDEX,ped,w.slot)==(int)w.texture,"Accessory variation and texture confirmed");
    } else {
        if(!C4(IS_PED_COMPONENT_VARIATION_VALID,ped,w.slot,w.drawable,w.texture)) {
            finish("FAILED","This clothing combination is invalid for your model");return;
        }
        C5(SET_PED_COMPONENT_VARIATION,ped,w.slot,w.drawable,w.texture,0);
        checked((int)C2(GET_PED_DRAWABLE_VARIATION,ped,w.slot)==(int)w.drawable
                &&(int)C2(GET_PED_TEXTURE_VARIATION,ped,w.slot)==(int)w.texture,"Clothing variation and texture confirmed");
    }
}
