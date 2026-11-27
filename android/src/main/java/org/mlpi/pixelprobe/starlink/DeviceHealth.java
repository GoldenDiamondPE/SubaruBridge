package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbManager;
import android.os.*;
import android.provider.Settings;
import java.net.NetworkInterface;
import java.util.*;

/** Passive phone/USB inspection: counts and states, never addresses or payloads. */
final class DeviceHealth {
    static final String USB_STATE="android.hardware.usb.action.USB_STATE";
    static Map<String,Object> snapshot(Context context) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("androidApi",Build.VERSION.SDK_INT);values.put("connection",StarlinkService.state());
        values.put("enabledOlderAppCopies",LegacyApps.enabledCount(context));
        values.put("transport",LinkDiagnostics.snapshot());
        values.put("notificationPermission",Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
        values.put("nearbyPermission",Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
        try {
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            values.put("bluetooth",adapter==null?"unavailable":adapter.isEnabled()?"on":"off");
            if(adapter!=null)values.put("pairedDeviceCount",adapter.getBondedDevices().size());
        }catch(SecurityException denied){values.put("bluetooth","permission_required");}
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null) {
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            if(level>=0&&scale>0)values.put("batteryPercent",Math.round(level*100f/scale));
            values.put("charging",battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0);
        }
        try {
            Intent usb=context.registerReceiver(null,new IntentFilter(USB_STATE));
            values.put("usbStateAvailable",usb!=null);
            if(usb!=null)for(String key:new String[]{"connected","configured","adb","rndis","ncm","mtp"})
                if(usb.hasExtra(key))values.put("usb_"+key,usb.getBooleanExtra(key,false));
            UsbManager manager=context.getSystemService(UsbManager.class);
            values.put("usbHostDeviceCount",manager==null?0:manager.getDeviceList().size());
            values.put("usbAccessoryCount",manager==null||manager.getAccessoryList()==null?0:manager.getAccessoryList().length);
        }catch(RuntimeException unavailable){values.put("usbStateAvailable",false);}
        try {
            int up=0,usb=0;Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()) {
                NetworkInterface item=interfaces.nextElement();if(!item.isUp()||item.isLoopback())continue;up++;
                if(item.getName().matches("(rndis|usb|ncm)[0-9]+"))usb++;
            }
            values.put("activeNetworkInterfaceCount",up);values.put("usbNetworkInterfaceCount",usb);
        }catch(Exception unavailable){values.put("networkInspection","unavailable");}
        values.put("usbDebuggingEnabled",Settings.Global.getInt(context.getContentResolver(),Settings.Global.ADB_ENABLED,0)==1);
        Runtime runtime=Runtime.getRuntime();values.put("heapBytes",runtime.totalMemory()-runtime.freeMemory());
        values.put("cpuTimeMs",android.os.Process.getElapsedCpuTime());return values;
    }
    private DeviceHealth(){}
}
