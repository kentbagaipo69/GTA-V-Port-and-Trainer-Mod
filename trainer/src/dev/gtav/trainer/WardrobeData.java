package dev.gtav.trainer;

final class WardrobeData {
    final long model;
    final String kind;
    final int slot, drawable, count, textures;
    private WardrobeData(long model, String kind, int slot, int drawable, int count, int textures) {
        this.model=model;this.kind=kind;this.slot=slot;this.drawable=drawable;this.count=count;this.textures=textures;
    }
    static WardrobeData parse(String message) {
        String[] p=message.split("\\|",-1);
        if(p.length!=7||!p[0].equals("WARDROBE")||!(p[2].equals("c")||p[2].equals("p")))throw new IllegalArgumentException("Invalid wardrobe reply");
        for(int i:new int[]{1,3,4,5,6})if(!p[i].matches("[0-9]{1,10}"))throw new IllegalArgumentException("Invalid wardrobe number");
        long model=Long.parseLong(p[1]);
        int slot=Integer.parseInt(p[3]),drawable=Integer.parseInt(p[4]),count=Integer.parseInt(p[5]),textures=Integer.parseInt(p[6]);
        if(model<1||model>0xffffffffL||slot>(p[2].equals("p")?12:11)||count>4096||textures>4096
                ||drawable>4095||(count>0&&drawable>=count)||(count==0&&(drawable!=0||textures!=0)))throw new IllegalArgumentException("Wardrobe reply out of range");
        return new WardrobeData(model,p[2],slot,drawable,count,textures);
    }
    String item(int texture) { return kind+"_"+slot+"_"+drawable+"_"+texture+"_"+model; }
}
