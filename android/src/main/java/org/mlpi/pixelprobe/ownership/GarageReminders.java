package org.mlpi.pixelprobe.ownership;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import org.mlpi.pixelprobe.R;
import org.mlpi.pixelprobe.starlink.GarageActivity;
import java.time.*;
import java.util.concurrent.*;

/** Opt-in local reminders. Inexact alarms are deferred by Android power management. */
public final class GarageReminders extends BroadcastReceiver {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final String CHANNEL = "garage-maintenance";
    private static final int NOTICE = 831, ALARM = 832;
    private static PendingIntent alarm(Context context) {
        return PendingIntent.getBroadcast(context, ALARM, new Intent(context, GarageReminders.class).setAction("org.subarubridge.GARAGE_REMINDER"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void refresh(Context context) {
        Context app = context.getApplicationContext(); WORK.execute(() -> check(app, "app"));
    }
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null||!(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                ||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                ||Intent.ACTION_TIME_CHANGED.equals(intent.getAction())||Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())
                ||"org.subarubridge.GARAGE_REMINDER".equals(intent.getAction())))return;
        PendingResult result = goAsync(); Context app = context.getApplicationContext();
        WORK.execute(() -> { try { check(app, intent.getAction()); } finally { result.finish(); } });
    }
    public static int[] counts(JSONObject snapshot) throws Exception {
        int attention = 0, overdue = 0; JSONArray cars = snapshot.getJSONObject("state").getJSONArray("vehicles"), summaries = snapshot.getJSONArray("summaries");
        for (int i = 0; i < summaries.length(); i++) {
            JSONObject summary = summaries.getJSONObject(i); boolean active = false;
            for (int j = 0; j < cars.length(); j++) if (cars.getJSONObject(j).getString("id").equals(summary.getString("vehicleId"))) active = !cars.getJSONObject(j).getBoolean("archived");
            if (!active) continue;
            JSONArray items = summary.getJSONArray("maintenance");
            for (int j = 0; j < items.length(); j++) {
                JSONObject item = items.getJSONObject(j); if (!item.isNull("reminderSnoozedUntil")) continue;
                String status = item.getString("status");
                if (status.equals("overdue") || status.equals("due") || status.equals("dueSoon") || status.equals("inspectionRecommended")) attention++;
                if (status.equals("overdue")) overdue++;
            }
        }
        return new int[]{attention, overdue};
    }
    public static final String PREFS = "subarubridge-garage-reminders";
    public static long nextCheck(long now,ZoneId zone) {
        ZonedDateTime current=Instant.ofEpochMilli(now).atZone(zone),next=current.toLocalDate().atTime(9,0).atZone(zone);
        if(!next.isAfter(current))next=current.toLocalDate().plusDays(1).atTime(9,0).atZone(zone);
        return next.toInstant().toEpochMilli();
    }
    public static String diagnostic(Context context) {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long checked=p.getLong("lastCheckedMs",0),next=p.getLong("nextCheckMs",0);
        java.time.format.DateTimeFormatter f=java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());
        return p.getString("outcome","Not checked yet")+"\nLast check: "+(checked==0?"Not yet":f.format(Instant.ofEpochMilli(checked)))
                +"\nNext scheduled: "+(next==0?"Off":f.format(Instant.ofEpochMilli(next)))
                +"\nAndroid may defer delivery while idle. A scheduled check is not a delivery guarantee.";
    }
    public static boolean canNotify(Context context) {
        NotificationManager n=context.getSystemService(NotificationManager.class);
        return n!=null && (Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
                && n.areNotificationsEnabled() && (n.getNotificationChannel(CHANNEL)==null||n.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE);
    }
    private static void channel(NotificationManager n){n.createNotificationChannel(new NotificationChannel(CHANNEL,"Maintenance reminders",NotificationManager.IMPORTANCE_DEFAULT));}
    private static Notification notice(Context context,String title,String body){
        PendingIntent open=PendingIntent.getActivity(context,NOTICE,new Intent(context,GarageActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.bridge_notice).setContentTitle(title).setContentText(body)
                .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_REMINDER).build();
    }
    /** Explicit test, with its own notification ID; does not invent a due service. */
    public static boolean testNotification(Context context) {
        if(!canNotify(context))return false;NotificationManager n=context.getSystemService(NotificationManager.class);channel(n);
        n.notify(NOTICE+10,notice(context,"Maintenance reminder test","Local delivery works. This is a test, not a due service."));return true;
    }
    private static void check(Context context,String reason) {
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);long now=System.currentTimeMillis();
        SharedPreferences.Editor log=prefs.edit().putLong("lastCheckedMs",now).putString("lastTrigger",reason);
        if(Intent.ACTION_BOOT_COMPLETED.equals(reason))log.putLong("lastBootMs",now);
        NotificationManager notifications=context.getSystemService(NotificationManager.class);AlarmManager alarms=context.getSystemService(AlarmManager.class);
        try {
            if(!prefs.getBoolean("enabled",false)){
                if(alarms!=null)alarms.cancel(alarm(context));if(notifications!=null)notifications.cancel(NOTICE);
                log.remove("shown").putLong("nextCheckMs",0).putString("outcome","Reminders are off").commit();return;
            }
            long next=nextCheck(now,ZoneId.systemDefault());
            // Reinstall the same calendar target on refresh/boot; never postpone by another 24 hours.
            if(alarms==null)throw new IllegalStateException("Alarm service unavailable");
            alarms.setWindow(AlarmManager.RTC_WAKEUP,next,60*60*1000L,alarm(context));log.putLong("nextCheckMs",next);
            int[] counts=counts(new OwnershipStore(context).view());
            if(!canNotify(context)){
                if(notifications!=null)notifications.cancel(NOTICE);log.remove("shown").putString("outcome","Notifications are blocked. Open Android notification settings.").commit();return;
            }
            channel(notifications);
            if(counts[0]==0){notifications.cancel(NOTICE);log.remove("shown").putString("outcome","Checked saved records. No unsnoozed maintenance needs attention.").commit();return;}
            String key=LocalDate.now()+":"+counts[0]+":"+counts[1];
            if(!key.equals(prefs.getString("shown",""))){
                notifications.notify(NOTICE,notice(context,"Maintenance reminder",counts[0]+(counts[0]==1?" item needs":" items need")+" attention. "+counts[1]+" overdue based on saved records."));log.putString("shown",key);
            }
            log.putString("outcome","Checked saved records; reminder submitted to Android.").commit();
        }catch(Exception|LinkageError e){
            if(notifications!=null)notifications.cancel(NOTICE);
            log.remove("shown").putString("outcome","Reminder check failed. Open Garage to review the saved file or storage. No due state was inferred.").putLong("failedMs",now).commit();
        }
    }
}
