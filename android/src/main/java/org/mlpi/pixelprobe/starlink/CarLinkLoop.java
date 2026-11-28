package org.mlpi.pixelprobe.starlink;

import java.io.*;

/** One cancellable listener/session owner. Recovery never depends on an Activity. */
final class CarLinkLoop implements Runnable {
    enum Availability { READY, BLUETOOTH_OFF, PERMISSION_REQUIRED, UNAVAILABLE }
    interface Backend { Availability availability(); Listener listen() throws IOException; }
    interface Listener extends Closeable { Peer accept() throws IOException; }
    interface Peer extends Closeable { boolean bonded(); InputStream input() throws IOException; OutputStream output() throws IOException; }
    interface Events { void state(String state); void session(Peer peer) throws IOException; }
    private final Backend backend; private final Events events; private final Object signal=new Object();
    private final long retryBaseMs;
    private volatile boolean stopped; private volatile Listener listener; private volatile Peer peer;
    CarLinkLoop(Backend backend,Events events){this(backend,events,1000);}
    CarLinkLoop(Backend backend,Events events,long retryBaseMs){if(retryBaseMs<1||retryBaseMs>1000)throw new IllegalArgumentException("Invalid retry delay");this.backend=backend;this.events=events;this.retryBaseMs=retryBaseMs;}
    void stop(){stopped=true;close(peer);close(listener);wake();}
    void changed(){if(availability()!=Availability.READY){close(peer);close(listener);}wake();}
    private void wake(){synchronized(signal){signal.notifyAll();}}
    private Availability availability(){try{return backend.availability();}catch(SecurityException denied){return Availability.PERMISSION_REQUIRED;}catch(RuntimeException unavailable){return Availability.UNAVAILABLE;}}
    private boolean waitFor(long millis){synchronized(signal){if(stopped)return false;try{signal.wait(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}return !stopped;}}
    private void state(String state){if(!stopped)events.state(state);}
    private static void close(Closeable resource){if(resource!=null)try{resource.close();}catch(IOException|RuntimeException ignored){}}
    @Override public void run(){int failures=0;
        try{
            while(!stopped){
                Availability available=availability();
                if(available==Availability.PERMISSION_REQUIRED){state("Stopped: Nearby devices permission required");return;}
                if(available==Availability.UNAVAILABLE){state("Stopped: Bluetooth is unavailable on this device");return;}
                if(available==Availability.BLUETOOTH_OFF){state("Waiting: turn on Bluetooth to resume");if(!waitFor(2000))return;continue;}
                try{
                    Listener opened=backend.listen();listener=opened;
                    if(stopped){close(opened);return;}
                    while(!stopped&&availability()==Availability.READY){
                        state("Ready: waiting for paired car Bluetooth");
                        Peer accepted=opened.accept();peer=accepted;
                        try{
                            if(stopped)return;
                            if(availability()!=Availability.READY)break;
                            if(!accepted.bonded()){state("Rejected: paired connection required");continue;}
                            failures=0;state("Connected: Bluetooth link open; waiting for car requests");events.session(accepted);
                        }catch(IOException|RuntimeException failure){if(!stopped)state("Disconnected: preparing to reconnect");}
                        finally{close(accepted);peer=null;}
                    }
                }catch(IOException failure){
                    close(listener);listener=null;
                    if(!stopped&&availability()==Availability.READY){
                        long delay=Math.min(30000,retryBaseMs*(1L<<Math.min(failures++,5)));
                        state("Retrying: Bluetooth listener unavailable; trying again");if(!waitFor(delay))return;
                    }
                }catch(SecurityException denied){state("Stopped: Nearby devices permission required");return;}
                finally{close(listener);listener=null;}
            }
        }catch(LinkageError unavailable){state("Stopped: connection components unavailable");}
        finally{close(peer);close(listener);peer=null;listener=null;}
    }
}
