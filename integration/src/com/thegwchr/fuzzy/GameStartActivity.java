package com.thegwchr.fuzzy;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;
import java.util.Collections;
public final class GameStartActivity extends Activity {
 private static final String TRAINER="com.thegwchr.fuzzy.OPEN_TRAINER";
 private boolean waitingForPermission, launchPending, launched;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  if(!BuildIntegrity.valid(this)){finish();return;}
  try {
   ShortcutManager manager=getSystemService(ShortcutManager.class);
   if(manager!=null) manager.addDynamicShortcuts(Collections.singletonList(
    new ShortcutInfo.Builder(this,"trainer").setShortLabel("Trainer")
     .setLongLabel("Open Trainer Lab")
     .setIcon(Icon.createWithResource(this,android.R.drawable.ic_menu_manage))
     .setIntent(new Intent(this,GameStartActivity.class).setAction(TRAINER)).build()));
  } catch(RuntimeException ignored) { }
  if(TRAINER.equals(getIntent().getAction())) {
   startActivity(new Intent(this,dev.gtav.trainer.MainActivity.class));finish();return;
  }
  waitingForPermission=state!=null&&state.getBoolean("waiting_overlay");
  if(waitingForPermission)return;
  if(Settings.canDrawOverlays(this))launchPending=true;
  else new AlertDialog.Builder(this).setTitle("Floating trainer")
   .setMessage("Allow display over other apps to show the trainer bubble automatically when you open the game. Tap the bubble to open controls; closing controls returns to the bubble.")
   .setPositiveButton("Enable overlay",(dialog,which)->{
    waitingForPermission=true;
    try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:"+getPackageName())),41); }
    catch(RuntimeException error){waitingForPermission=false;openGame();}
   })
   .setNegativeButton("Continue without",(dialog,which)->openGame())
   .setOnCancelListener(dialog->openGame()).show();
 }
 @Override protected void onPostResume() {
  super.onPostResume();
  if(launchPending){launchPending=false;openGame();}
 }
 @Override protected void onActivityResult(int request,int result,Intent data) {
  super.onActivityResult(request,result,data);
  if(request==41){waitingForPermission=false;launchPending=true;}
 }
 @Override protected void onSaveInstanceState(Bundle state) {
  super.onSaveInstanceState(state);state.putBoolean("waiting_overlay",waitingForPermission);
 }
 private void openGame() {
  if(launched||isFinishing())return;
  launched=true;
  startActivity(new Intent().setClassName(getPackageName(),"com.thegwchr.fuzzy.LauncherActivity"));finish();
 }
}
