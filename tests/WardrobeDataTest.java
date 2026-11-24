package dev.gtav.trainer;
public final class WardrobeDataTest {
    static void reject(String value){try{WardrobeData.parse(value);throw new AssertionError(value);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        WardrobeData data=WardrobeData.parse("WARDROBE|4294967295|c|11|2|3|2");
        if(!data.item(1).equals("c_11_2_1_4294967295"))throw new AssertionError();
        WardrobeData.parse("WARDROBE|123|p|12|0|0|0");
        reject("WARDROBE|123|c|12|0|1|1");
        reject("WARDROBE|123|p|13|0|1|1");
        reject("WARDROBE|123|c|11|3|3|2");
        reject("WARDROBE|123|c|11|0|4097|2");
        reject("WARDROBE|123|c|11|0|0|1");
        reject("WARDROBE|0|c|11|0|1|1");
        reject("WARDROBE|4294967296|c|11|0|1|1");
        reject("WARDROBE|123|c|11|-1|1|1");
        reject("WARDROBE|123|c|11|0|1|1|extra");
        System.out.println("PASS: wardrobe reply bounds, malformed responses and unsigned model IDs");
    }
}
