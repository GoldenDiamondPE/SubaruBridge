package org.mlpi.pixelprobe.ownership;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import org.mlpi.pixelprobe.R;
import org.mlpi.pixelprobe.starlink.GarageActivity;
import java.time.LocalDate;
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
        Context app = context.getApplicationContext(); WORK.execute(() -> check(app));
    }
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null||!(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                ||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())
                ||"org.subarubridge.GARAGE_REMINDER".equals(intent.getAction())))return;
        PendingResult result = goAsync(); Context app = context.getApplicationContext();
        WORK.execute(() -> { try { check(app); } finally { result.finish(); } });
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
    private static void check(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("subarubridge-garage-reminders", Context.MODE_PRIVATE);
        NotificationManager notifications = context.getSystemService(NotificationManager.class); AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (!prefs.getBoolean("enabled", false)) { if (alarms != null) alarms.cancel(alarm(context)); if (notifications != null) notifications.cancel(NOTICE); prefs.edit().remove("shown").apply(); return; }
        if (alarms != null) alarms.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_DAY, AlarmManager.INTERVAL_DAY, alarm(context));
        if (notifications == null || Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || !notifications.areNotificationsEnabled()) return;
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Maintenance reminders", NotificationManager.IMPORTANCE_DEFAULT));
        try {
            int[] counts = counts(new OwnershipStore(context).view());
            if (counts[0] == 0) { notifications.cancel(NOTICE); prefs.edit().remove("shown").apply(); return; }
            String key = LocalDate.now() + ":" + counts[0] + ":" + counts[1]; if (key.equals(prefs.getString("shown", ""))) return;
            PendingIntent open = PendingIntent.getActivity(context, NOTICE, new Intent(context, GarageActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notice = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.bridge_notice)
                    .setContentTitle("Maintenance reminder").setContentText(counts[0] + (counts[0] == 1 ? " item needs" : " items need") + " attention. " + counts[1] + " overdue based on saved records.")
                    .setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_REMINDER).build();
            notifications.notify(NOTICE, notice); prefs.edit().putString("shown", key).apply();
        } catch (Exception | LinkageError ignored) { /* A corrupt or future schema is preserved; never infer a due state. */ }
    }
}
