package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Original, paired RFCOMM listener. No root, vendor APK or external backend. */
public final class StarlinkService extends Service {
    public static final UUID SERVICE_UUID=UUID.fromString("4213882b-44b9-45f4-ac4f-2c682bc800a7");
    private static final String CHANNEL="subarubridge-connection";
    private static final int NOTICE=5101;
    private static volatile String state="Stopped";
    private static volatile boolean running;
    private volatile boolean stopping;
    private volatile BluetoothServerSocket server;
    private volatile BluetoothSocket peer;
    private Thread worker;

    public static String state(){return state;}
    public static boolean running(){return running;}
    private void state(String value){
        state=value;Log.i("SubaruBridgeLink",value);
        android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"bridge-link-state.txt"));
        FileOutputStream out=null;
        try{out=file.startWrite();out.write((value+"\n"+System.currentTimeMillis()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException e){file.failWrite(out);Log.w("SubaruBridgeLink","Status file unavailable");}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Car connection",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,StarlinkActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,StarlinkService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("SubaruBridge connection").setContentText("Listening for a paired device. Tap to view or stop.")
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).build();
        startForeground(NOTICE,notice);
        if(worker==null){running=true;worker=new Thread(this::listen,"bridge-rfcomm");worker.start();}
        return START_NOT_STICKY;
    }
    private void listen() {
        try {
            if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new IOException("Nearby devices permission required");
            BluetoothManager manager=getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled())throw new IOException("Turn Bluetooth on, then start again");
            StarlinkRuntime runtime=StarlinkRuntime.get(this);runtime.startPreview();
            server=adapter.listenUsingRfcommWithServiceRecord("SubaruBridge",SERVICE_UUID);
            while(!stopping) {
                state("Ready: waiting for paired car Bluetooth");
                BluetoothSocket socket=server.accept();peer=socket;
                try {
                    if(socket.getRemoteDevice().getBondState()!=BluetoothDevice.BOND_BONDED){state("Unpaired connection declined");continue;}
                    state("Connected: serving local screens");
                    session(socket,runtime.router);
                }catch(IOException e){if(!stopping)state("Disconnected: ready to reconnect");}
                finally{try{socket.close();}catch(IOException ignored){}peer=null;}
            }
        }catch(IOException | SecurityException | LinkageError e){if(!stopping)state("Stopped: "+(e instanceof IOException?e.getMessage():e.getClass().getSimpleName()));}
        finally{running=false;stopSelf();}
    }
    private void session(BluetoothSocket socket,StarlinkRouter router) throws IOException {
        PowerManager power=getSystemService(PowerManager.class);
        PowerManager.WakeLock awake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubaruBridge:car-session");
        awake.acquire(30*60*1000L);
        ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor();
        try(RustCore core=new RustCore(new SecureRandom().nextLong())) {
            class Pump {
                synchronized void send(RustCore.Batch first) throws IOException {
                    ArrayDeque<RustCore.Batch> batches=new ArrayDeque<>();batches.add(first);
                    int actions=0;
                    while(!batches.isEmpty()) {
                        RustCore.Batch batch=batches.removeFirst();
                        if(batch.wire.length>0){socket.getOutputStream().write(batch.wire);socket.getOutputStream().flush();}
                        for(RustCore.Request request:batch.requests) {
                            if(++actions>256)throw new IOException("Request batch limit");
                            LocalHttp.Response response=router.handle(request.bytes);
                            batches.add(core.respond(request.connection,response.wire,response.close,SystemClock.elapsedRealtime()));
                        }
                    }
                }
                synchronized void feed(byte[] bytes) throws IOException{send(core.feed(bytes,SystemClock.elapsedRealtime()));}
                synchronized void tick() throws IOException{send(core.tick(SystemClock.elapsedRealtime()));}
            }
            Pump pump=new Pump();
            clock.scheduleAtFixedRate(()->{try{pump.tick();}catch(IOException e){try{socket.close();}catch(IOException ignored){}}},100,100,TimeUnit.MILLISECONDS);
            byte[] bytes=new byte[8192];int count;
            while(!stopping && (count=socket.getInputStream().read(bytes))!=-1){if(count>0)pump.feed(Arrays.copyOf(bytes,count));}
        }finally{clock.shutdownNow();if(awake.isHeld())awake.release();}
    }
    @Override public void onDestroy() {
        stopping=true;running=false;
        try{if(peer!=null)peer.close();}catch(IOException ignored){}
        try{if(server!=null)server.close();}catch(IOException ignored){}
        if(worker!=null)worker.interrupt();
        if(!state.startsWith("Stopped:"))state("Stopped");
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
