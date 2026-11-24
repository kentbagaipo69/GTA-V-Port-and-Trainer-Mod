package dev.gtav.trainer;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Compact native controls shared by the launcher and draggable window. */
final class TrainerPanel extends ContextWrapper {
    private final TrainerSession session;
    private final boolean overlay;
    private final Runnable editFocus;
    private final LinearLayout root, tabs, content;
    private final ScrollView scroll;
    private final TextView connection, status, details;
    private final Button retry;
    private final Runnable changed=this::refresh;
    private final Map<String,String> queries=new HashMap<>();
    private String selected="vehicles", vehicleGroup="Planes", modelGroup="All", character="michael";
    private boolean vehicleControls, showDetails;

    TrainerPanel(Context context,boolean overlay,Runnable editFocus,Bundle state) throws Exception {
        super(context);this.overlay=overlay;this.editFocus=editFocus;session=TrainerSession.get(context);
        if(state!=null) {
            selected=state.getString("category","vehicles");vehicleGroup=state.getString("vehicle_group","Planes");
            modelGroup=state.getString("model_group","All");character=state.getString("money_character","michael");
            vehicleControls=state.getBoolean("vehicle_controls",false);
            Bundle saved=state.getBundle("queries");if(saved!=null)for(String key:saved.keySet())queries.put(key,saved.getString(key,""));
        }
        root=vertical();root.setBackgroundColor(TrainerStyle.BG);root.setPadding(dp(12),dp(8),dp(12),dp(10));
        if(!overlay) {
            TextView title=text("Trainer Lab",24,TrainerStyle.TEXT);title.setTypeface(Typeface.create("sans-serif-medium",0));
            root.addView(title);root.addView(text("By Kent Vincent Bagaipo",10,TrainerStyle.MUTED),margin(3));
        }
        tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(horizontal(tabs),margin(overlay?0:12));
        View divider=rule();LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));dividerParams.topMargin=dp(8);root.addView(divider,dividerParams);
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        content=vertical();content.setPadding(0,dp(10),0,dp(10));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(rule());
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);
        connection=text("Waiting for game",11,TrainerStyle.MUTED);connection.setPadding(0,dp(8),dp(4),dp(8));
        connection.setOnClickListener(v->toggleDetails());
        footer.addView(connection,new LinearLayout.LayoutParams(0,dp(44),1));
        retry=button("Retry",v->session.client.check());footer.addView(retry,new LinearLayout.LayoutParams(dp(64),dp(36)));
        Button open=button("Open game",v->openGame());LinearLayout.LayoutParams openLp=new LinearLayout.LayoutParams(dp(96),dp(36));openLp.leftMargin=dp(6);footer.addView(open,openLp);
        root.addView(footer);
        details=text("",11,TrainerStyle.MUTED);details.setVisibility(View.GONE);root.addView(details,margin(2));
        status=text("",11,TrainerStyle.MUTED);status.setMaxLines(3);root.addView(status,margin(2));
        buildTabs();renderCategory();
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){resume();}
            public void onViewDetachedFromWindow(View v){pause();}
        });
        refresh();
    }
    View view(){return root;}
    private void toggleDetails(){showDetails=!showDetails;details.setVisibility(showDetails?View.VISIBLE:View.GONE);}
    void resume(){session.client.subscribe(changed);refresh();}
    void pause(){session.client.unsubscribe(changed);}
    void onConfigurationChanged(Configuration c){refresh();}
    void saveState(Bundle b){
        b.putString("category",selected);b.putString("vehicle_group",vehicleGroup);b.putString("model_group",modelGroup);
        b.putString("money_character",character);b.putBoolean("vehicle_controls",vehicleControls);
        Bundle saved=new Bundle();for(Map.Entry<String,String> e:queries.entrySet())saved.putString(e.getKey(),e.getValue());b.putBundle("queries",saved);
    }
    private String label(String id){
        switch(id){case "characters":return "Models";case "clothing":return "Clothes";case "teleport":return "Teleport";default:return id.substring(0,1).toUpperCase(Locale.ROOT)+id.substring(1);}
    }
    private void buildTabs(){
        tabs.removeAllViews();
        for(String id:new String[]{"vehicles","player","money","characters","weapons","clothing","world","teleport","mission"}) {
            boolean exists=false;for(CommandCatalog.Category c:session.catalog.categories)if(c.id.equals(id))exists=true;
            if(!exists)continue;
            Button b=button(label(id),v->{selected=id;buildTabs();renderCategory();scroll.scrollTo(0,0);});
            TrainerStyle.button(b,selected.equals(id));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.rightMargin=dp(4);tabs.addView(b,lp);
        }
    }
    private void renderCategory(){
        content.removeAllViews();
        CommandCatalog.Category category=null;for(CommandCatalog.Category c:session.catalog.categories)if(c.id.equals(selected))category=c;
        if(category==null)return;
        if(selected.equals("mission"))content.addView(note("Available on the mission-failed screen only when the game offers a whole-mission skip. Tap, then confirm in the game. Experimental; not an instant mission pass."));
        if(selected.equals("money")){renderMoney();refresh();return;}
        if(selected.equals("clothing"))content.addView(new WardrobePanel(this,session.client));
        if(selected.equals("teleport"))content.addView(note("Choose a destination or set a waypoint on the game map. Your current vehicle travels with you. Distant waypoints need loaded ground; named locations use reference coordinates."));
        if(selected.equals("vehicles")) {
            LinearLayout mode=new LinearLayout(this);
            mode.addView(choice("Browse",!vehicleControls,()->{vehicleControls=false;renderCategory();}),new LinearLayout.LayoutParams(0,dp(40),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.leftMargin=dp(6);
            mode.addView(choice("Vehicle controls",vehicleControls,()->{vehicleControls=true;renderCategory();}),lp);
            if(!overlay||vehicleControls)content.addView(mode);
            if(!vehicleControls) {
                String[] filters=overlay?new String[]{"Controls","All","Planes","Helicopters","Boats & ships","Submarines","Blimps","Land"}:new String[]{"All","Planes","Helicopters","Boats & ships","Submarines","Blimps","Land"};
                addFilter(content,filters,vehicleGroup,value->{if(value.equals("Controls"))vehicleControls=true;else vehicleGroup=value;renderCategory();});
                addSearch(content,session.catalog.find("vehicles.spawn_selected"));
                content.addView(note("Boats need open water ahead. Aircraft need clear space. Models unavailable in your game will be rejected."),margin(10));
                refresh();return;
            }
        }
        if(selected.equals("characters")) {
            content.addView(note("Change your character. Choose a person or animal; return to Michael, Franklin or Trevor below."));
            LinearLayout quick=new LinearLayout(this);
            for(String id:new String[]{"michael","franklin","trevor"}) {
                CommandCatalog.Action a=session.catalog.find("characters."+id);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.rightMargin=dp(4);
                quick.addView(command(a,null,a.label),lp);
            }
            content.addView(quick,margin(8));
            addFilter(content,new String[]{"All","People","Animals","Story"},modelGroup,value->{modelGroup=value;renderCategory();});
        }
        List<CommandCatalog.Action> plain=new ArrayList<>();
        if(selected.equals("player")) {
            content.addView(section("Movement"));
            CommandCatalog.Action run=session.catalog.find("player.fast_run"),swim=session.catalog.find("player.fast_swim"),jump=session.catalog.find("player.super_jump");
            content.addView(pair(command(run,null,run.label),command(swim,null,swim.label)),margin(8));
            content.addView(command(jump,null,jump.label),margin(6));
            content.addView(note("Tap again to turn off. Stop all effects restores normal movement."),margin(6));
            content.addView(section("Player actions"),margin(16));
        }
        for(CommandCatalog.Action action:category.actions) {
            if(selected.equals("player")&&(action.id.equals("player.fast_run")||action.id.equals("player.fast_swim")||action.id.equals("player.super_jump")))continue;
            if(action.items!=null) {
                if(selected.equals("vehicles")&&vehicleControls)continue;
                addSearch(content,action);
            } else if(!selected.equals("characters")||action.id.equals("characters.npc_model")) plain.add(action);
        }
        if(!plain.isEmpty()) {
            if(selected.equals("characters"))content.addView(section("Quick models"),margin(12));
            for(int i=0;i<plain.size();i+=2){
                final CommandCatalog.Action a=plain.get(i);
                Button left=command(a,null,shortLabel(a.label));
                Button right=i+1<plain.size()?command(plain.get(i+1),null,shortLabel(plain.get(i+1).label)):null;
                content.addView(pair(left,right),margin(6));
            }
        }
        refresh();
    }
    private String shortLabel(String s){return s.replace("Toggle ","").replace("Turn off trainer effects","Stop all effects");}
    private void renderMoney(){
        content.addView(section("Story cash"));
        content.addView(note("Choose whose balance to change. Amounts may be saved with your game."),margin(5));
        addFilter(content,new String[]{"Michael","Franklin","Trevor"},character.substring(0,1).toUpperCase(Locale.ROOT)+character.substring(1),value->{character=value.toLowerCase(Locale.ROOT);renderCategory();});
        CommandCatalog.Action add=session.catalog.find("money.add_"+character);
        if(add!=null)for(CommandCatalog.Item item:add.items.items)content.addView(command(add,item,item.name),margin(6));
        content.addView(section("Unlimited cash"),margin(18));
        content.addView(note("Refills this character to $1 billion while enabled. Stopping refill keeps the current balance."),margin(4));
        CommandCatalog.Action refill=session.catalog.find("money.unlimited_"+character);
        if(refill!=null)content.addView(pair(command(refill,refill.items.find("on"),"Enable refill"),command(refill,refill.items.find("off"),"Stop refill")),margin(10));
        CommandCatalog.Action balances=session.catalog.find("money.balances");
        if(balances!=null)content.addView(command(balances,null,"Check all balances"),margin(16));
    }
    private interface Selection{void accept(String value);}
    private void addFilter(LinearLayout parent,String[] values,String current,Selection changed){
        LinearLayout row=new LinearLayout(this);
        for(String value:values){Button b=choice(value,current.equals(value),()->changed.accept(value));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(4);row.addView(b,lp);}
        parent.addView(horizontal(row),margin(8));
    }
    private void addSearch(LinearLayout parent,CommandCatalog.Action action){
        if(action==null)return;
        if(!selected.equals("vehicles")&&!selected.equals("characters"))parent.addView(section(action.label),margin(10));
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(TrainerStyle.TEXT);search.setHintTextColor(TrainerStyle.MUTED);
        search.setHint(selected.equals("characters")?"Search model or animal…":selected.equals("teleport")?"Search destinations…":"Search name or model…");
        search.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setPadding(dp(12),0,dp(12),0);search.setBackground(TrainerStyle.background(this,TrainerStyle.SURFACE,6));
        search.setText(queries.getOrDefault(action.id,""));
        if(overlay)search.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)editFocus.run();return false;});
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(46));slp.topMargin=dp(8);parent.addView(search,slp);
        TextView count=note("");parent.addView(count,margin(6));
        LinearLayout list=vertical();parent.addView(list,margin(4));
        final int[] limit={overlay?8:16};
        Runnable[] render=new Runnable[1];
        render[0]=()->{
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);queries.put(action.id,query);
            List<CommandCatalog.Item> matches=new ArrayList<>();
            for(CommandCatalog.Item item:action.items.items) {
                if(selected.equals("vehicles")&&!vehicleGroup.equals("All")){
                    boolean airWater=item.group.equals("Planes")||item.group.equals("Helicopters")||item.group.equals("Boats & ships")||item.group.equals("Submarines")||item.group.equals("Blimps");
                    if(vehicleGroup.equals("Land")?airWater:!item.group.equals(vehicleGroup))continue;
                }
                if(selected.equals("characters")&&!modelGroup.equals("All")&&!item.group.equals(modelGroup))continue;
                if(!(item.name+" "+item.id+" "+item.group).toLowerCase(Locale.ROOT).contains(query))continue;
                matches.add(item);
            }
            list.removeAllViews();int shown=Math.min(limit[0],matches.size());
            count.setText(matches.size()==0?"No matching entries":matches.size()+" results · "+shown+" shown");
            for(int i=0;i<shown;i++) {
                CommandCatalog.Item item=matches.get(i);
                LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(9),dp(10),dp(9));
                row.setBackground(TrainerStyle.background(this,TrainerStyle.SURFACE,5));
                LinearLayout labels=vertical();TextView name=text(item.name,14,TrainerStyle.TEXT);name.setTypeface(Typeface.create("sans-serif-medium",0));labels.addView(name);
                labels.addView(text(item.group,10,TrainerStyle.MUTED),margin(2));
                row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                TextView go=text(selected.equals("vehicles")?"SPAWN":selected.equals("teleport")?"GO":"SELECT",10,TrainerStyle.ACCENT);row.addView(go);
                row.setMinimumHeight(dp(52));row.setContentDescription(item.name+", "+item.id);
                row.setOnClickListener(v->session.client.execute(action,item));mark(row);
                list.addView(row,margin(3));
            }
            if(shown<matches.size())list.addView(button("Show more",v->{limit[0]+=overlay?8:16;render[0].run();}),margin(6));
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){}public void afterTextChanged(Editable e){limit[0]=overlay?8:16;render[0].run();}});
        render[0].run();
    }
    private Button command(CommandCatalog.Action action,CommandCatalog.Item item,String label){Button b=button(label,v->session.client.execute(action,item));mark(b);return b;}
    private void mark(View v){v.setTag("game-command");v.setEnabled(session.client.canExecute());v.setAlpha(v.isEnabled()?1f:0.64f);}
    private void refresh(){
        if(connection==null)return;
        connection.setText(session.client.isBusy()?"● Working…":session.client.isConnected()?"● Connected ▾":"○ Waiting for game ▾");
        connection.setTextColor(session.client.isConnected()?TrainerStyle.ACCENT:TrainerStyle.MUTED);
        details.setText(session.client.connection());status.setText(session.client.status());retry.setEnabled(!session.client.isBusy());updateCommands(root);
    }
    private void updateCommands(View view){if("game-command".equals(view.getTag()))mark(view);if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)updateCommands(g.getChildAt(i));}}
    private void openGame(){try{Intent intent=getPackageManager().getLaunchIntentForPackage(session.catalog.targetPackage);if(intent==null){status.setText("Install the matching patched game first.");return;}startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(RuntimeException e){status.setText("Could not open game: "+e.getMessage());}}
    private Button choice(String label,boolean active,Runnable select){Button b=button(label,v->select.run());TrainerStyle.button(b,active);return b;}
    private Button button(String label,View.OnClickListener click){Button b=new Button(this);b.setText(label);TrainerStyle.button(b,false);b.setMinHeight(dp(46));b.setMinimumHeight(dp(46));b.setOnClickListener(click);b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(46)));return b;}
    private TextView section(String s){TextView v=text(s,15,TrainerStyle.TEXT);v.setTypeface(Typeface.create("sans-serif-medium",0));return v;}
    private TextView note(String s){return text(s,11,TrainerStyle.MUTED);}
    private TextView text(String s,int size,int color){return TrainerStyle.text(this,s,size,color);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private View rule(){View v=new View(this);v.setBackgroundColor(TrainerStyle.LINE);v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));return v;}
    private HorizontalScrollView horizontal(LinearLayout row){HorizontalScrollView s=new HorizontalScrollView(this);s.setHorizontalScrollBarEnabled(false);s.addView(row);return s;}
    private LinearLayout pair(View a,View b){LinearLayout l=new LinearLayout(this);l.addView(a,new LinearLayout.LayoutParams(0,dp(46),1));if(b!=null){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.leftMargin=dp(6);l.addView(b,p);}return l;}
    private LinearLayout.LayoutParams margin(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);return p;}
    private int dp(int value){return TrainerStyle.dp(this,value);}
}
