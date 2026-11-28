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
    private static StarlinkService owner;
    private volatile boolean stopping;
    private volatile CarLinkLoop loop;
    // Process-local test backend; no intent or network route can select it.
    static volatile CarLinkLoop.Backend testBackend;
    private boolean receiverRegistered;
    private final BroadcastReceiver bluetoothState=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){if(intent!=null&&BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())){CarLinkLoop current=loop;if(current!=null)current.changed();}}};
    private Thread worker;
    private boolean foreground;

    public static String state(){return state;}
    public static boolean running(){return running;}
    private void state(String value){
        synchronized(StarlinkService.class) {
            if(owner!=this||value.equals(state))return;
            state=value;
        }
        Log.i("SubaruBridgeLink",value);
        LinkDiagnostics.state(this,value);
        if (foreground) {
            String summary=value.startsWith("Active:")?"STARLINK requests received":value.startsWith("Connected:")?"Bluetooth linked · waiting for STARLINK":value.startsWith("Ready:")?"Ready for your paired car":value.startsWith("Waiting:")?"Waiting for Bluetooth to turn on":value.startsWith("Retrying:")||value.startsWith("Disconnected:")?"Recovering your car connection":value.startsWith("Starting:")?"Preparing your connection":"Connection paused";
            try{getSystemService(NotificationManager.class).notify(NOTICE,notification(summary));}
            catch(RuntimeException unavailable){Log.w("SubaruBridgeLink","Connection notification unavailable");}
        }
        android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"bridge-link-state.txt"));
        FileOutputStream out=null;
        try{out=file.startWrite();out.write((value+"\n"+System.currentTimeMillis()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));file.finishWrite(out);}
        catch(IOException e){file.failWrite(out);Log.w("SubaruBridgeLink","Status file unavailable");}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    public static void pausedByUser(Context context,boolean paused){context.getSharedPreferences("subarubridge-connection",MODE_PRIVATE).edit().putBoolean("pausedByUser",paused).apply();}
    public static boolean pausedByUser(Context context){return context.getSharedPreferences("subarubridge-connection",MODE_PRIVATE).getBoolean("pausedByUser",false);}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "STOP".equals(intent.getAction())){pausedByUser(this,true);stopping=true;if(loop!=null)loop.stop();stopSelf(startId);return START_NOT_STICKY;}
        if(pausedByUser(this)){stopSelf(startId);return START_NOT_STICKY;}
        synchronized(StarlinkService.class){owner=this;}
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){state("Stopped: Nearby devices permission required");stopSelf(startId);return START_NOT_STICKY;}
        try{
            NotificationManager manager=getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Car connection",NotificationManager.IMPORTANCE_LOW));
            startForeground(NOTICE,notification("Preparing your connection"));foreground=true;
        }catch(RuntimeException denied){state("Stopped: Android could not start the connection service; open the app and allow Nearby devices");stopSelf(startId);return START_NOT_STICKY;}
        if(worker==null){
            synchronized(StarlinkService.class){running=true;}
            state("Starting: preparing Bluetooth");
            IntentFilter filter=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            try{if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetoothState,filter,Context.RECEIVER_EXPORTED);else registerReceiver(bluetoothState,filter);receiverRegistered=true;}
            catch(RuntimeException unavailable){state("Stopped: Bluetooth recovery could not start; reopen the app");stopSelf(startId);return START_NOT_STICKY;}
            worker=new Thread(this::listen,"bridge-rfcomm");worker.start();
        }
        return START_STICKY;
    }
    private Notification notification(String summary) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,StarlinkActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,StarlinkService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(org.mlpi.pixelprobe.R.drawable.bridge_notice)
                .setContentTitle("SubaruBridge").setContentText(summary)
                .setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"Pause",stop).build()).build();
    }
    private CarLinkLoop.Backend backend(){
        CarLinkLoop.Backend fixture=testBackend;if(fixture!=null)return fixture;
        return new CarLinkLoop.Backend(){
            private BluetoothAdapter adapter(){BluetoothManager manager=getSystemService(BluetoothManager.class);return manager==null?null:manager.getAdapter();}
            @Override public CarLinkLoop.Availability availability(){
                if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)return CarLinkLoop.Availability.PERMISSION_REQUIRED;
                BluetoothAdapter adapter=adapter();return adapter==null?CarLinkLoop.Availability.UNAVAILABLE:adapter.isEnabled()?CarLinkLoop.Availability.READY:CarLinkLoop.Availability.BLUETOOTH_OFF;
            }
            @Override public CarLinkLoop.Listener listen()throws IOException{
                if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices permission required");
                BluetoothAdapter adapter=adapter();if(adapter==null)throw new IOException("Bluetooth unavailable");
                BluetoothServerSocket server=adapter.listenUsingRfcommWithServiceRecord("SubaruBridge",SERVICE_UUID);
                return new CarLinkLoop.Listener(){
                    @Override public CarLinkLoop.Peer accept()throws IOException{
                        BluetoothSocket socket=server.accept();return new CarLinkLoop.Peer(){
                            @Override public boolean bonded(){if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices permission required");return socket.getRemoteDevice().getBondState()==BluetoothDevice.BOND_BONDED;}
                            @Override public InputStream input()throws IOException{return socket.getInputStream();}
                            @Override public OutputStream output()throws IOException{return socket.getOutputStream();}
                            @Override public void close()throws IOException{socket.close();}
                        };
                    }
                    @Override public void close()throws IOException{server.close();}
                };
            }
        };
    }
    private void listen() {
        try{
            StarlinkRuntime runtime=StarlinkRuntime.get(this);
            // The phone preview is optional; its port must never gate Bluetooth.
            try{runtime.startPreview();}catch(IOException unavailable){Log.w("SubaruBridgePreview","Phone preview unavailable; car listener is independent");}
            CarLinkLoop current=new CarLinkLoop(backend(),new CarLinkLoop.Events(){
                @Override public void state(String value){StarlinkService.this.state(value);}
                @Override public void session(CarLinkLoop.Peer peer)throws IOException{StarlinkService.this.session(peer,runtime.router);}
            });loop=current;if(stopping)current.stop();else current.run();
        }catch(RuntimeException|LinkageError unavailable){if(!stopping)state("Stopped: connection components unavailable; reopen the app");}
        finally{synchronized(StarlinkService.class){if(owner==this)running=false;}stopSelf();}
    }
    private void session(CarLinkLoop.Peer socket,StarlinkRouter router) throws IOException {
        PowerManager power=getSystemService(PowerManager.class);
        PowerManager.WakeLock awake=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubaruBridge:car-session");
        awake.setReferenceCounted(false);
        SessionPower lease=new SessionPower(new SessionPower.Lock(){public void acquire(long timeout){awake.acquire(timeout);}public void release(){awake.release();}public boolean held(){return awake.isHeld();}},SystemClock.elapsedRealtime());
        ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor();
        RustCore core=null;
        try {
            core=new RustCore(new SecureRandom().nextLong());final RustCore engine=core;
            class Pump {
                private boolean active;
                synchronized void send(RustCore.Batch first) throws IOException {
                    ArrayDeque<RustCore.Batch> batches=new ArrayDeque<>();batches.add(first);
                    int actions=0;
                    while(!batches.isEmpty()) {
                        RustCore.Batch batch=batches.removeFirst();
                        if(batch.wire.length>0){socket.output().write(batch.wire);socket.output().flush();LinkDiagnostics.sent(batch.wire.length);}
                        for(RustCore.Request request:batch.requests) {
                            if(++actions>256)throw new IOException("Request batch limit");
                            if(!active){active=true;state("Active: STARLINK requests received");}
                            LocalHttp.Response response=router.handle(request.bytes);
                            batches.add(engine.respond(request.connection,response.wire,response.close,SystemClock.elapsedRealtime()));
                        }
                    }
                }
                synchronized void feed(byte[] bytes) throws IOException{send(engine.feed(bytes,SystemClock.elapsedRealtime()));}
                synchronized void tick() throws IOException{lease.refresh(SystemClock.elapsedRealtime());send(engine.tick(SystemClock.elapsedRealtime()));}
            }
            Pump pump=new Pump();
            clock.scheduleWithFixedDelay(()->{try{pump.tick();}catch(IOException|RuntimeException e){try{socket.close();}catch(IOException ignored){}}},100,100,TimeUnit.MILLISECONDS);
            byte[] bytes=new byte[8192];int count;
            while(!stopping && (count=socket.input().read(bytes))!=-1){if(count>0){LinkDiagnostics.received(count);pump.feed(Arrays.copyOf(bytes,count));}}
        }finally{clock.shutdownNow();lease.close();if(core!=null)core.close();}
    }
    @Override public void onDestroy() {
        AppleMediaClient.stopIfStarted();
        stopping=true;
        synchronized(StarlinkService.class){if(owner==this)running=false;}
        foreground=false;
        CarLinkLoop current=loop;if(current!=null)current.stop();
        if(receiverRegistered){unregisterReceiver(bluetoothState);receiverRegistered=false;}
        if(worker!=null)worker.interrupt();
        synchronized(StarlinkService.class){if(owner==this){if(!state.startsWith("Stopped:"))state("Stopped");owner=null;}}
        stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
}
