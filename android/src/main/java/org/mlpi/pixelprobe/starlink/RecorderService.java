package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.Intent;
import android.os.*;
import org.mlpi.pixelprobe.R;
import java.io.IOException;
import java.util.concurrent.*;

/** User-started ten-minute local log processing. No boot start or laptop arming. */
public final class RecorderService extends Service {
    private static final String CHANNEL="subarubridge-recording";
    private static final int NOTICE=5102;
    private ScheduledExecutorService worker;
    private long started;
    private SessionRecorder recorder;
    private PowerManager.WakeLock awake;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_NOT_STICKY;
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL,"Diagnostic recordings",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,DeveloperActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,RecorderService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        startForeground(NOTICE,new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.bridge_notice)
                .setContentTitle("Recording SubaruBridge diagnostics").setContentText("Local app events · stops after 10 minutes")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop and save",stop).build()).build());
        recorder=SessionRecorder.get(this);
        try{recorder.start();}catch(IOException failure){stopSelf();return START_NOT_STICKY;}
        awake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubaruBridge:diagnostic-recording");
        awake.acquire(605000L);
        started=SystemClock.elapsedRealtime();worker=Executors.newSingleThreadScheduledExecutor();
        worker.scheduleWithFixedDelay(()->{
            if(!recorder.recording()){stopSelf();return;}
            if(SystemClock.elapsedRealtime()-started>=600000){finish("time_limit");return;}
            try{recorder.record("phone_sample",DeviceHealth.snapshot(this));}
            catch(RuntimeException unavailable){recorder.record("phone_sample",java.util.Map.of("status","inspection_unavailable"));}
        },0,5,TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }
    private void finish(String reason) {
        if(recorder!=null)try{recorder.stop(reason);}catch(IOException failure){android.util.Log.w("SubaruBridgeRecorder","Recording save failed");}
        stopSelf();
    }
    @Override public void onTimeout(int startId,int type){finish("system_time_limit");}
    @Override public void onDestroy() {
        if(worker!=null)worker.shutdownNow();
        if(awake!=null&&awake.isHeld())awake.release();
        if(recorder!=null)try{recorder.stop("saved");}catch(IOException failure){android.util.Log.w("SubaruBridgeRecorder","Partial recording preserved");}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
