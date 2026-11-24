package com.thegwchr.fuzzy;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import java.util.IdentityHashMap;

public final class TrainerApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final IdentityHashMap<Activity,ServiceConnection> connections=new IdentityHashMap<>();
 @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
 @Override public void onActivityResumed(Activity activity){
  String name=activity.getClass().getName();
  if(!(name.equals("com.thegwchr.fuzzy.LauncherActivity")||name.equals("com.thegwchr.fuzzy.GTAVNativeActivity"))
    ||connections.containsKey(activity)||!Settings.canDrawOverlays(activity)||!BuildIntegrity.valid(this))return;
  ServiceConnection connection=new ServiceConnection(){
   public void onServiceConnected(ComponentName name,IBinder binder){}
   public void onServiceDisconnected(ComponentName name){}
  };
  try {
   if(activity.bindService(new Intent(activity,dev.gtav.trainer.OverlayService.class),connection,BIND_AUTO_CREATE))
    connections.put(activity,connection);
  }catch(RuntimeException error){android.util.Log.w("TrainerOverlay","Could not bind foreground overlay",error);}
 }
 private void release(Activity activity){
  ServiceConnection connection=connections.remove(activity);
  if(connection!=null)try{activity.unbindService(connection);}catch(IllegalArgumentException ignored){}
 }
 @Override public void onActivityPaused(Activity activity){release(activity);}
 @Override public void onActivityStopped(Activity activity){release(activity);}
 @Override public void onActivityDestroyed(Activity activity){release(activity);}
 @Override public void onActivityCreated(Activity activity,Bundle state){}
 @Override public void onActivityStarted(Activity activity){}
 @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
}
