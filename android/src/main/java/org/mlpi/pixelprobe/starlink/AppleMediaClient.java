package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

/** Optional GATT client for Apple's public AMS. Pairing/authorization stay with the operating systems. */
final class AppleMediaClient {
    static final UUID SERVICE=UUID.fromString("89d3502b-0f36-433a-8ef4-c502ad55f8dc");
    static final UUID REMOTE=UUID.fromString("9b3c81d8-57b1-4a8a-b8df-0e56f7ca51c2");
    static final UUID UPDATE=UUID.fromString("2f7cabce-808d-411f-9a0c-bb92ba96c102");
    private static final UUID GATT_SERVICE=UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    private static final UUID CHANGED=UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static AppleMediaClient instance;
    static synchronized AppleMediaClient get(Context context){if(instance==null)instance=new AppleMediaClient(context.getApplicationContext());return instance;}
    static synchronized void stopIfStarted(){if(instance!=null)instance.disconnect();}
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Object timeouts=new Object();
    private final AppleMediaState media=new AppleMediaState();
    private final ArrayDeque<Operation> queue=new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic remote;
    private Operation inFlight;
    private boolean mediaAvailable;
    private volatile String status="iPhone media is disconnected";
    private volatile long updates,rejected;
    private volatile int lastGattStatus=-1,remoteProperties=-1,entityProperties=-1;
    private AppleMediaClient(Context context){this.context=context;}
    String status(){return status+" · updates "+updates+", rejected "+rejected;}
    MediaSnapshot snapshot(){return media.snapshot(SystemClock.elapsedRealtime());}
    Map<String,Object> diagnostics(){
        Map<String,Object> out=new LinkedHashMap<>();out.put("schema",1);out.put("experimental",true);
        out.put("status",status);out.put("ready",snapshot()!=null);out.put("acceptedUpdates",updates);out.put("rejectedUpdates",rejected);
        out.put("lastGattStatus",lastGattStatus);out.put("lastRemoteProperties",remoteProperties);out.put("lastEntityProperties",entityProperties);
        return out;
    }
    void disconnect(){handler.post(()->close("iPhone media is disconnected"));}
    private void close(String message) {
        handler.removeCallbacksAndMessages(timeouts);queue.clear();inFlight=null;media.clear();remote=null;mediaAvailable=false;
        BluetoothGatt old=gatt;gatt=null;status=message;
        if(old!=null){try{old.disconnect();}catch(SecurityException ignored){}try{old.close();}catch(SecurityException ignored){}}
    }
    void connect(BluetoothDevice device) {
        handler.post(()->{
            close("Preparing iPhone media");updates=0;rejected=0;lastGattStatus=-1;remoteProperties=-1;entityProperties=-1;
            try {
                if(Build.VERSION.SDK_INT>=31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){close("Allow Nearby devices on the SubaruBridge home screen first");return;}
                if(device==null || device.getBondState()!=BluetoothDevice.BOND_BONDED){close("Pair the iPhone and Pixel normally in Bluetooth settings first");return;}
                if(!StarlinkService.running()){close("Resume the car connection on Home before connecting iPhone media");return;}
                media.begin();status="Connecting to the paired iPhone over Bluetooth LE";
                gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE);
                if(gatt==null){close("iPhone Bluetooth LE connection could not start");return;}
                BluetoothGatt expected=gatt;
                handler.postDelayed(()->{if(gatt==expected && !mediaAvailable)close("iPhone media setup timed out. Keep the iPhone nearby and start music before reconnecting.");},timeouts,30000);
            }catch(SecurityException denied){close("iPhone media permission was revoked");}
            catch(RuntimeException unavailable){close("iPhone Bluetooth LE is unavailable. Pair normally and try again.");}
        });
    }
    String command(String verb,String target) throws LocalHttp.Error {
        if(!media.permits(verb,target))throw new LocalHttp.Error(409,"iPhone player or supported controls changed. Refresh the music screen.");
        handler.post(()->{
            PhoneIntegrations phone=PhoneIntegrations.get(context);
            if(!phone.unlocked() || !phone.enabled("music") || !phone.prefs().getString("musicSource","android").equals("iphone") || !media.permits(verb,target) || remote==null){status="iPhone control canceled because the source, access or session changed";return;}
            if(queue.size()>=16){status="iPhone control queue is full. Refresh before trying again.";return;}
            queue.add(new Operation(remote,verb,target));pump();
        });
        return "Queued for the paired iPhone. Refresh to confirm the player changed; a queued request does not prove playback.";
    }
    private final class Operation {
        final BluetoothGattCharacteristic characteristic;
        final BluetoothGattDescriptor descriptor;
        final byte[] value;
        final String verb,target;
        Operation(BluetoothGattCharacteristic c,byte[] v){characteristic=c;descriptor=null;value=v;verb=null;target=null;}
        Operation(BluetoothGattCharacteristic c,String v,String t){characteristic=c;descriptor=null;verb=v;target=t;value=new byte[]{(byte)AppleMediaState.commandId(v)};}
        Operation(BluetoothGattDescriptor d,byte[] v){descriptor=d;characteristic=null;value=v;verb=null;target=null;}
        boolean valid(){if(verb==null)return true;PhoneIntegrations phone=PhoneIntegrations.get(context);return phone.unlocked() && phone.enabled("music") && phone.prefs().getString("musicSource","android").equals("iphone") && media.permits(verb,target);}
        @SuppressWarnings("deprecation") boolean start() {
            if(verb!=null)value[0]=(byte)media.offeredCommand(verb);
            if(descriptor!=null) {
                if(Build.VERSION.SDK_INT>=33)return gatt.writeDescriptor(descriptor,value)==BluetoothStatusCodes.SUCCESS;
                descriptor.setValue(value);return gatt.writeDescriptor(descriptor);
            }
            int writeType=(characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE)!=0?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            if(Build.VERSION.SDK_INT>=33)return gatt.writeCharacteristic(characteristic,value,writeType)==BluetoothStatusCodes.SUCCESS;
            characteristic.setWriteType(writeType);characteristic.setValue(value);return gatt.writeCharacteristic(characteristic);
        }
    }
    private void pump() {
        if(gatt==null || inFlight!=null)return;
        if(queue.isEmpty()){if(mediaAvailable){media.ready();status="iPhone media is ready; start Taurus or another player on the iPhone";}return;}
        Operation op=queue.removeFirst();
        if(!op.valid()){status="iPhone control canceled because the source, access or session changed";pump();return;}
        inFlight=op;
        try{if(!op.start()){close("iPhone media operation could not start. Reconnect to try again.");return;}}
        catch(SecurityException denied){close("iPhone media permission was revoked");return;}
        catch(RuntimeException unavailable){close("iPhone media operation failed locally. No control was retried.");return;}
        handler.postDelayed(()->{if(inFlight==op)close("iPhone media operation timed out. No control was retried.");},timeouts,10000);
    }
    private boolean subscribe(BluetoothGattCharacteristic c) {
        if(c==null)return false;
        int properties=c.getProperties();boolean notify=(properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0;
        if(!notify && (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE)==0)return false;
        BluetoothGattDescriptor descriptor=c.getDescriptor(CCCD);
        if(descriptor==null || !gatt.setCharacteristicNotification(c,true))return false;
        queue.add(new Operation(descriptor,notify?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE));return true;
    }
    private void discovered(BluetoothGatt peer,int code) {
        if(peer!=gatt)return;
        lastGattStatus=code;
        if(code!=BluetoothGatt.GATT_SUCCESS){close("iPhone service discovery failed (GATT "+code+")");return;}
        try {
            BluetoothGattService generic=gatt.getService(GATT_SERVICE);
            if(generic!=null)subscribe(generic.getCharacteristic(CHANGED));
            BluetoothGattService service=gatt.getService(SERVICE);
            if(service==null){close("The paired device did not expose Apple Media Service. Start iPhone music and reconnect; this is not a Subaru result.");return;}
            remote=service.getCharacteristic(REMOTE);BluetoothGattCharacteristic update=service.getCharacteristic(UPDATE);
            remoteProperties=remote==null?0:remote.getProperties();entityProperties=update==null?0:update.getProperties();
            boolean remoteEvents=remote!=null && (remote.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE|BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE))!=0 && subscribe(remote);
            if(!remoteEvents)remote=null;
            boolean entityEvents=update!=null && (update.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE)!=0 && subscribe(update);
            if(entityEvents) {
                // CCCDs are queued first: Apple requires notification subscription before registration writes.
                queue.add(new Operation(update,new byte[]{0,0,1}));
                queue.add(new Operation(update,new byte[]{2,0,1,2,3}));
            }
            if(!remoteEvents && !entityEvents){close("This iPhone exposes no usable AMS metadata or control characteristics");return;}
            mediaAvailable=true;status="Subscribing to iPhone media";pump();
        }catch(SecurityException denied){close("iPhone media permission was revoked");}
    }
    private void complete(BluetoothGatt peer,Object item,int code) {
        if(peer!=gatt || inFlight==null || (inFlight.characteristic!=item && inFlight.descriptor!=item))return;
        lastGattStatus=code;
        if(code!=BluetoothGatt.GATT_SUCCESS){close("iPhone media GATT error "+code+". Confirm any normal iPhone authorization prompt, then reconnect. No control was retried.");return;}
        inFlight=null;pump();
    }
    private void changed(BluetoothGatt peer,UUID id,byte[] data) {
        if(peer!=gatt)return;
        if(CHANGED.equals(id)){close("iPhone Bluetooth services changed. Reconnect to discover current capabilities.");return;}
        try {
            if(REMOTE.equals(id)){if(data==null || data.length>512){rejected++;return;}media.supported(data);}
            else if(UPDATE.equals(id)){media.update(data,SystemClock.elapsedRealtime());updates++;}
        }catch(Exception malformed){rejected++;}
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt peer,int code,int state){handler.post(()->{
            if(peer!=gatt)return;
            lastGattStatus=code;
            if(code!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED){close("iPhone media disconnected (GATT "+code+")");return;}
            if(state==BluetoothProfile.STATE_CONNECTED)try{status="Discovering iPhone media";if(!peer.discoverServices())close("iPhone service discovery could not start");}catch(SecurityException denied){close("iPhone media permission was revoked");}
        });}
        @Override public void onServicesDiscovered(BluetoothGatt peer,int code){handler.post(()->discovered(peer,code));}
        @Override public void onServiceChanged(BluetoothGatt peer){handler.post(()->{if(peer==gatt)close("iPhone Bluetooth services changed. Reconnect to discover current capabilities.");});}
        @Override public void onDescriptorWrite(BluetoothGatt peer,BluetoothGattDescriptor descriptor,int code){handler.post(()->complete(peer,descriptor,code));}
        @Override public void onCharacteristicWrite(BluetoothGatt peer,BluetoothGattCharacteristic c,int code){handler.post(()->complete(peer,c,code));}
        @Override public void onCharacteristicChanged(BluetoothGatt peer,BluetoothGattCharacteristic c,byte[] value){
            if(value==null || value.length>512){handler.post(()->{if(peer==gatt)rejected++;});return;}
            byte[] copy=value.clone();handler.post(()->changed(peer,c.getUuid(),copy));
        }
        @SuppressWarnings("deprecation") @Override public void onCharacteristicChanged(BluetoothGatt peer,BluetoothGattCharacteristic c){byte[] value=c.getValue();onCharacteristicChanged(peer,c,value);}
    };
}
