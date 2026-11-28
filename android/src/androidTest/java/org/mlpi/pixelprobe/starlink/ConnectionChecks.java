package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Real Android service lifecycle with isolated transports, never a synthetic car claim. */
final class ConnectionChecks {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void until(BooleanSupplier ready,String message)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+5000;while(!ready.getAsBoolean()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(10);check(ready.getAsBoolean(),message);
    }
    private static void join(Thread thread)throws Exception{thread.join(2000);check(!thread.isAlive(),"Listener could not stop");}
    private static final class Peer implements CarLinkLoop.Peer {
        final boolean bonded;volatile boolean closed;final PipedInputStream input=new PipedInputStream();final PipedOutputStream hold;
        Peer(boolean bonded)throws IOException{this.bonded=bonded;hold=new PipedOutputStream(input);}
        public boolean bonded(){return bonded;}public InputStream input(){return input;}public OutputStream output(){return new ByteArrayOutputStream();}
        public void close()throws IOException{closed=true;hold.close();input.close();}
    }
    private static final class Listener implements CarLinkLoop.Listener {
        final BlockingQueue<Peer> peers=new LinkedBlockingQueue<>();volatile boolean closed,fail,denied;
        public CarLinkLoop.Peer accept()throws IOException{
            if(denied)throw new SecurityException("fixture");if(fail)throw new IOException("fixture");
            try{while(!closed){Peer peer=peers.poll(10,TimeUnit.MILLISECONDS);if(peer!=null)return peer;}}
            catch(InterruptedException e){Thread.currentThread().interrupt();}throw new IOException("closed");
        }
        public void close(){closed=true;}
    }
    private static final class Backend implements CarLinkLoop.Backend {
        volatile CarLinkLoop.Availability available=CarLinkLoop.Availability.READY;
        volatile Listener last;final AtomicInteger opens=new AtomicInteger();int failures;boolean failFirstAccept;
        public CarLinkLoop.Availability availability(){return available;}
        public CarLinkLoop.Listener listen()throws IOException{
            int attempt=opens.incrementAndGet();if(attempt<=failures)throw new IOException("fixture");
            Listener listener=new Listener();listener.fail=failFirstAccept&&attempt==failures+1;last=listener;return listener;
        }
    }
    private static final class Events implements CarLinkLoop.Events {
        volatile String state="";final AtomicInteger sessions=new AtomicInteger();
        public void state(String value){state=value;}
        public void session(CarLinkLoop.Peer peer)throws IOException{sessions.incrementAndGet();throw new IOException("simulated disconnect");}
    }
    private static void loopCases()throws Exception{
        Backend backend=new Backend();backend.failures=2;backend.failFirstAccept=true;Events events=new Events();
        CarLinkLoop loop=new CarLinkLoop(backend,events,1);Thread worker=new Thread(loop,"fixture-recovery");worker.start();
        try{
            until(()->backend.opens.get()>=4&&events.state.startsWith("Ready:"),"Listener did not recover after open/accept failures");
            Peer unpaired=new Peer(false);backend.last.peers.add(unpaired);until(()->unpaired.closed,"Unpaired peer retained");check(events.sessions.get()==0,"Unpaired session accepted");
            Peer paired=new Peer(true);backend.last.peers.add(paired);until(()->paired.closed&&events.sessions.get()==1,"Disconnected peer not released");
            Peer second=new Peer(true);backend.last.peers.add(second);until(()->second.closed&&events.sessions.get()==2,"Reconnect required an Activity");
            backend.available=CarLinkLoop.Availability.BLUETOOTH_OFF;loop.changed();until(()->events.state.startsWith("Waiting:"),"Bluetooth off did not wait");int opens=backend.opens.get();
            backend.available=CarLinkLoop.Availability.READY;loop.changed();until(()->backend.opens.get()>opens&&events.state.startsWith("Ready:"),"Bluetooth on did not resume");
            backend.available=CarLinkLoop.Availability.PERMISSION_REQUIRED;loop.changed();join(worker);check(events.state.contains("permission required"),"Permission loss did not stop cleanly");
        }finally{loop.stop();join(worker);}
        check(backend.last.closed,"Stopped listener still open");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);Listener late=new Listener();
        CarLinkLoop slow=new CarLinkLoop(new CarLinkLoop.Backend(){public CarLinkLoop.Availability availability(){return CarLinkLoop.Availability.READY;}public CarLinkLoop.Listener listen()throws IOException{entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){throw new IOException(e);}return late;}},new Events(),1);
        Thread opening=new Thread(slow,"fixture-late-listener");opening.start();check(entered.await(2,TimeUnit.SECONDS),"Open fixture missing");slow.stop();release.countDown();join(opening);check(late.closed,"Late listener leaked after stop");
        Backend denied=new Backend();denied.available=CarLinkLoop.Availability.PERMISSION_REQUIRED;Events deniedEvents=new Events();new CarLinkLoop(denied,deniedEvents,1).run();check(denied.opens.get()==0&&deniedEvents.state.contains("permission required"),"Missing permission opened listener");
        Backend blocked=new Backend();Events blockedEvents=new Events();CarLinkLoop accepting=new CarLinkLoop(blocked,blockedEvents,1);Thread waiting=new Thread(accepting);waiting.start();until(()->blocked.last!=null,"Accept did not start");accepting.stop();join(waiting);check(blocked.last.closed,"Blocking accept was not canceled");
    }
    private static void powerCases(){
        final class Lock implements SessionPower.Lock{int acquisitions,releases;boolean held;public void acquire(long timeout){check(timeout==SessionPower.LEASE_MS,"Unbounded power lease");acquisitions++;held=true;}public void release(){releases++;held=false;}public boolean held(){return held;}}
        Lock lock=new Lock();SessionPower lease=new SessionPower(lock,0);lease.refresh(SessionPower.RENEW_MS-1);check(lock.acquisitions==1,"Lease renewed too early");
        lease.refresh(SessionPower.RENEW_MS);lease.refresh(2*SessionPower.RENEW_MS);check(lock.acquisitions==3,"Long drive lease expired");
        lease.close();lease.refresh(3*SessionPower.RENEW_MS);lease.close();check(lock.acquisitions==3&&lock.releases==1&&!lock.held,"Timer reacquired stopped lease");
    }
    private static void previewCases(Context context)throws Exception{
        StarlinkRuntime runtime=StarlinkRuntime.get(context);runtime.stopPreview();
        try(ServerSocket occupied=new ServerSocket()){
            occupied.bind(new InetSocketAddress("127.0.0.1",0));runtime.startPreview(occupied.getLocalPort());
            check(runtime.previewPort()!=occupied.getLocalPort(),"Occupied preview did not fall back");
            try(Socket socket=new Socket("127.0.0.1",runtime.previewPort())){
                socket.setSoTimeout(2000);socket.getOutputStream().write("GET /version HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                check(new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8).startsWith("HTTP/1.1 200"),"Fallback dashboard unavailable");
            }
            check("pass".equals(PhoneSelfCheck.run(context).get("localDashboard")),"Phone checks used stale preview port");
        }finally{runtime.stopPreview();runtime.startPreview();}
        check(runtime.previewPort()>0,"Preview did not restart");
    }
    static void run(Instrumentation instrumentation,Context context,Bundle result)throws Exception{
        loopCases();powerCases();previewCases(context);
        SharedPreferences prefs=context.getSharedPreferences("subarubridge-connection",Context.MODE_PRIVATE);Map<String,?> original=prefs.getAll();
        boolean wasRunning=StarlinkService.running();Backend backend=new Backend();backend.failures=1;Activity[] screen={null};
        try{
            context.stopService(new Intent(context,StarlinkService.class));until(()->!StarlinkService.running(),"Existing listener did not stop");
            prefs.edit().putBoolean("automatic",false).putBoolean("pausedByUser",false).commit();StarlinkService.testBackend=backend;
            screen[0]=instrumentation.startActivitySync(new Intent(context,StarlinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->context.startForegroundService(new Intent(context,StarlinkService.class)));
            until(()->StarlinkService.state().startsWith("Ready:"),"Foreground listener retry failed");instrumentation.runOnMainSync(screen[0]::finish);screen[0]=null;
            backend.available=CarLinkLoop.Availability.BLUETOOTH_OFF;backend.last.close();until(()->StarlinkService.state().startsWith("Waiting:"),"Background Bluetooth loss stopped service");
            check(StarlinkService.running(),"Waiting service not alive");
            backend.available=CarLinkLoop.Availability.READY;until(()->StarlinkService.state().startsWith("Ready:"),"Background Bluetooth recovery failed");
            Peer peer=new Peer(true);backend.last.peers.add(peer);until(()->StarlinkService.state().startsWith("Connected:"),"Service did not accept paired fixture");peer.close();
            until(()->StarlinkService.state().startsWith("Ready:"),"Service did not recover after peer ended");
            instrumentation.runOnMainSync(()->context.startService(new Intent(context,StarlinkService.class).setAction("STOP")));
            until(()->!StarlinkService.running()&&backend.last.closed,"Pause did not release service resources");check(StarlinkService.pausedByUser(context),"Notification pause not saved");
            prefs.edit().putBoolean("automatic",true).commit();screen[0]=instrumentation.startActivitySync(new Intent(context,StarlinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(400);check(!StarlinkService.running(),"Reopening ignored explicit pause");
            instrumentation.runOnMainSync(()->screen[0].findViewById(org.mlpi.pixelprobe.R.id.phone_primary_action).performClick());
            until(()->StarlinkService.running()&&StarlinkService.state().startsWith("Ready:"),"Explicit resume failed");check(!StarlinkService.pausedByUser(context),"Resume did not clear pause");
            result.putString("connection","PASS: failed open/accept, bond checks, reconnect, Bluetooth off/on in background, permission loss, cancel races, long-session power, preview collision, foreground service and persisted pause");
        }finally{
            if(screen[0]!=null){Activity activity=screen[0];instrumentation.runOnMainSync(activity::finish);}
            context.stopService(new Intent(context,StarlinkService.class));until(()->!StarlinkService.running(),"Fixture service did not stop");StarlinkService.testBackend=null;
            SharedPreferences.Editor restore=prefs.edit().clear();for(Map.Entry<String,?> entry:original.entrySet()){Object value=entry.getValue();if(value instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)value);else if(value instanceof String)restore.putString(entry.getKey(),(String)value);else if(value instanceof Long)restore.putLong(entry.getKey(),(Long)value);else if(value instanceof Integer)restore.putInt(entry.getKey(),(Integer)value);}restore.commit();
            if(wasRunning&&!StarlinkService.pausedByUser(context)){
                Activity activity=instrumentation.startActivitySync(new Intent(context,StarlinkActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.runOnMainSync(()->{context.startForegroundService(new Intent(context,StarlinkService.class));activity.finish();});
            }
        }
    }
}
