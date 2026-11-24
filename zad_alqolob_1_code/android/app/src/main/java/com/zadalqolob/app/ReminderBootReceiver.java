package com.zadalqolob.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Map;

public class ReminderBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context, Intent intent) {
  String action=intent==null?null:intent.getAction(); if(action==null)return;
  if(!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_TIME_CHANGED.equals(action)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
  SharedPreferences prefs=context.getSharedPreferences("zad_reminders",Context.MODE_PRIVATE);
  for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
   if(!entry.getKey().startsWith("reminder_")||!(entry.getValue() instanceof String))continue;
   try{JSONObject data=new JSONObject((String)entry.getValue()); ReminderReceiver.schedule(context,entry.getKey().substring("reminder_".length()),data.optString("title"),data.optString("message"),data.optInt("hour",7),data.optInt("minute",0));}catch(Exception ignored){}
  }
 }
}
