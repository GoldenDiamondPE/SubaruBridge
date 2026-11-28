package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import org.json.*;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Temporary reminder preferences only; saves/restores owner settings and never seeds owner records. */
final class ReminderLifecycleChecks {
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static File saved(Context c){return new File(c.getCacheDir(),"reminder-lifecycle-original.json");}
    private static JSONObject read(Context c)throws Exception{return OwnershipStore.parse(Files.readAllBytes(saved(c).toPath()));}
    static void run(Context c,String step,Bundle result)throws Exception{
        SharedPreferences p=c.getSharedPreferences(GarageReminders.PREFS,Context.MODE_PRIVATE);
        if(step.equals("prepare")){
            check(!saved(c).exists(),"Finish the previous reminder lifecycle check before preparing another");
            JSONObject values=new JSONObject();for(Map.Entry<String,?> e:p.getAll().entrySet()){Object v=e.getValue();String type=v instanceof Boolean?"boolean":v instanceof Long?"long":v instanceof Integer?"int":v instanceof Float?"float":v instanceof Set?"set":"string";values.put(e.getKey(),new JSONObject().put("type",type).put("value",v instanceof Set?new JSONArray((Set<?>)v):v));}
            JSONObject backup=new JSONObject().put("startedMs",System.currentTimeMillis()).put("values",values);Files.write(saved(c).toPath(),backup.toString().getBytes(StandardCharsets.UTF_8));
            p.edit().putBoolean("enabled",true).commit();long start=System.currentTimeMillis();GarageReminders.refresh(c);await(p,start);
            check(p.getLong("nextCheckMs",0)>start,"Reminder was not scheduled");check(GarageReminders.testNotification(c),"Android notifications are blocked");
            boolean visible=false;for(android.service.notification.StatusBarNotification n:c.getSystemService(NotificationManager.class).getActiveNotifications())if(n.getId()==841)visible=true;check(visible,"Android did not retain the test notification");
            result.putString("reminderLifecycle","PASS: actual notification posted and normal daily alarm scheduled; original settings saved privately");
        }else if(step.equals("idle")){
            read(c);long start=System.currentTimeMillis();
            c.sendBroadcast(new Intent(c,GarageReminders.class).setAction("org.subarubridge.GARAGE_REMINDER"));await(p,start);
            check(p.getLong("nextCheckMs",0)>start,"Idle receiver did not reinstall the alarm");check(GarageReminders.testNotification(c),"Idle notification test blocked");
            result.putString("reminderLifecycle","PASS: real private receiver checked saved records and posted test notification during accelerated idle; this is not an overnight alarm-delivery test");
        }else if(step.equals("reboot")){
            JSONObject backup=read(c);check(p.getLong("lastBootMs",0)>backup.getLong("startedMs"),"Boot receiver did not run after unlock");
            check(p.getLong("nextCheckMs",0)>System.currentTimeMillis(),"Daily alarm was not restored after reboot");check(GarageReminders.testNotification(c),"Notification delivery unavailable after reboot");
            result.putString("reminderLifecycle","PASS: physical reboot/unlock receiver restored daily schedule and Android accepted a local test notification");
        }else if(step.equals("finish")){
            JSONObject values=read(c).getJSONObject("values");SharedPreferences.Editor edit=p.edit().clear();Iterator<String> keys=values.keys();while(keys.hasNext()){
                String key=keys.next();JSONObject v=values.getJSONObject(key);switch(v.getString("type")){
                    case "boolean":edit.putBoolean(key,v.getBoolean("value"));break;case "long":edit.putLong(key,v.getLong("value"));break;case "int":edit.putInt(key,v.getInt("value"));break;case "float":edit.putFloat(key,(float)v.getDouble("value"));break;case "set":HashSet<String> set=new HashSet<>();JSONArray list=v.getJSONArray("value");for(int i=0;i<list.length();i++)set.add(list.getString(i));edit.putStringSet(key,set);break;default:edit.putString(key,v.getString("value"));
                }
            }edit.commit();c.getSystemService(NotificationManager.class).cancel(841);saved(c).delete();GarageReminders.refresh(c);result.putString("reminderLifecycle","PASS: original reminder settings restored; test notification removed");
        }else throw new IOException("Unknown reminder test step");
    }
    private static void await(SharedPreferences p,long after)throws Exception{long until=System.currentTimeMillis()+10000;while(p.getLong("lastCheckedMs",0)<after&&System.currentTimeMillis()<until)Thread.sleep(60);check(p.getLong("lastCheckedMs",0)>=after,"Reminder receiver did not finish");}
}
