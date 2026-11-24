package dev.gtav.trainer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Reads model-specific choices before permitting an explicit apply. */
final class WardrobePanel extends LinearLayout {
    private static final String[] SLOTS={"Tops / jackets · 11","Torso / arms · 3","Pants · 4","Shoes · 6","Undershirts · 8",
        "Neck accessories · 7","Bags / hands · 5","Masks / beards · 1","Armor · 9","Badges / decals · 10","Hair · 2","Face · 0",
        "Hats · 0","Glasses · 1","Ears · 2","Watches · 6","Bracelets · 7","Mouth props · 3","Left hand props · 4",
        "Right hand props · 5","Hip props · 8","Left foot props · 9","Right foot props · 10","Left hand attachments · 11","Right hand attachments · 12"};
    private static final int[] IDS={11,3,4,6,8,7,5,1,9,10,2,0,0,1,2,6,7,3,4,5,8,9,10,11,12};
    private final BridgeClient client;
    private final Spinner slot, drawable, texture;
    private final TextView info;
    private WardrobeData loaded;
    private int generation;

    WardrobePanel(Context context,BridgeClient client) {
        super(context);this.client=client;setOrientation(VERTICAL);
        addView(TrainerStyle.text(context,"Clothes & accessories",16,TrainerStyle.TEXT));
        addView(TrainerStyle.text(context,"Choose a slot, load its variations, then load textures for your chosen variation. Names and available slots vary by character model.",11,TrainerStyle.MUTED));
        slot=new Spinner(context);setChoices(slot,java.util.Arrays.asList(SLOTS));addView(slot,new LayoutParams(-1,dp(48)));
        addButton("Load available variations",()->load(false));
        addView(TrainerStyle.text(context,"Variation",12,TrainerStyle.MUTED));
        drawable=new Spinner(context);setChoices(drawable,java.util.Collections.singletonList("Load variations first"));addView(drawable,new LayoutParams(-1,dp(48)));
        addButton("Load textures for selected variation",()->load(true));
        addView(TrainerStyle.text(context,"Texture / colour",12,TrainerStyle.MUTED));
        texture=new Spinner(context);setChoices(texture,java.util.Collections.singletonList("Load textures first"));addView(texture,new LayoutParams(-1,dp(48)));
        info=TrainerStyle.text(context,"Connect in story mode to read your character's clothing pool. Choices use game variation IDs; thumbnail images are not included.",11,TrainerStyle.MUTED);addView(info);
        addButton("Apply selected clothing / accessory",()->{
            if(!matches()||loaded.count==0||loaded.textures==0){info.setText("Load textures for this variation before applying.");return;}
            client.executeRaw("clothing.apply",loaded.item(texture.getSelectedItemPosition()),"Selected wardrobe variation",null);
        });
        addButton("Remove selected accessory",()->{
            if(loaded==null||!loaded.kind.equals("p")||!sameSlot(loaded)){info.setText("Select an accessory slot and load its variations first.");return;}
            client.executeRaw("clothing.remove_prop","p_"+loaded.slot+"_0_0_"+loaded.model,"Remove accessory",null);
        });
        slot.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                generation++;loaded=null;setChoices(drawable,java.util.Collections.singletonList("Load variations first"));
                setChoices(texture,java.util.Collections.singletonList("Load textures first"));info.setText("Load the available variations for this slot.");
            }
        });
    }
    private boolean sameSlot(WardrobeData data){int i=slot.getSelectedItemPosition();return data.slot==IDS[i]&&data.kind.equals(i>=12?"p":"c");}
    private boolean matches(){return loaded!=null&&sameSlot(loaded)&&loaded.drawable==drawable.getSelectedItemPosition();}
    private void load(boolean textures) {
        if(!client.canExecute())return;
        if(textures&&(loaded==null||!sameSlot(loaded)||loaded.count==0)){info.setText("Load available variations first.");return;}
        int i=slot.getSelectedItemPosition(), d=textures?drawable.getSelectedItemPosition():0;
        long model=textures?loaded.model:0;
        int request=++generation;
        String kind=i>=12?"p":"c";
        client.executeRaw("clothing.inspect",kind+"_"+IDS[i]+"_"+d+"_0_"+model,"Read wardrobe choices",reply->{
            if(request!=generation||!isAttachedToWindow())return;
            if(reply==null||!reply.status.equals("APPLIED")){loaded=null;info.setText("Wardrobe read failed. Reload variations after the game is ready.");return;}
            try {
                WardrobeData data=WardrobeData.parse(reply.message);
                if(!sameSlot(data)||data.drawable!=d||(model!=0&&data.model!=model))throw new IllegalArgumentException();
                loaded=data;
                if(!textures){setChoices(drawable,numbered("Variation",data.count));drawable.setSelection(d);}
                setChoices(texture,numbered("Texture",data.textures));
                info.setText(data.count+" variations · "+data.textures+" textures for variation "+d+". Reload after changing character.");
            } catch(RuntimeException error){loaded=null;info.setText("Unexpected wardrobe data. Nothing applied.");}
        });
    }
    private List<String> numbered(String prefix,int count){List<String> values=new ArrayList<>();for(int i=0;i<count;i++)values.add(prefix+" "+i);if(values.isEmpty())values.add("None available");return values;}
    private void setChoices(Spinner spinner,List<String> values){
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(getContext(),android.R.layout.simple_spinner_item,values){
            @Override public View getView(int position,View convert,ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTextColor(TrainerStyle.TEXT);v.setTextSize(13);return v;}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);
    }
    private void addButton(String label,Runnable action){Button b=new Button(getContext());b.setText(label);TrainerStyle.button(b,false);b.setTag("game-command");b.setOnClickListener(v->action.run());LayoutParams lp=new LayoutParams(-1,dp(46));lp.topMargin=dp(6);addView(b,lp);}
    private int dp(int n){return TrainerStyle.dp(getContext(),n);}
}
