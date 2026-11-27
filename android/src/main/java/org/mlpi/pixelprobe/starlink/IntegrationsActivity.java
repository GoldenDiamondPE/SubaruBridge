package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.bluetooth.*;
import android.os.Handler;
import android.os.Looper;
import android.media.session.MediaController;
import java.util.*;

/** Device-only setup. Message sharing is off until explicitly enabled here. */
public final class IntegrationsActivity extends Activity {
    private PhoneIntegrations integrations;
    private LinearLayout page;
    private TextView access;
    private TextView musicStatus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refreshMusic=new Runnable(){public void run(){if(musicStatus!=null){String source=integrations.prefs().getString("musicSource","android");musicStatus.setText(source.equals("iphone")?AppleMediaClient.get(IntegrationsActivity.this).status():"Android music · "+(integrations.prefs().getString("musicPackage","").isEmpty()?"follow the active player":integrations.prefs().getString("musicPackage","")));}handler.postDelayed(this,1000);}};
    private PhonePalette palette;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(size>=22?palette.text:palette.muted);if(size>=22)v.setTypeface(null,Typeface.BOLD);v.setPadding(0,dp(12),0,dp(8));page.addView(v);}
    private void button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(palette.onAccent);b.setBackground(rounded(palette.accent,14));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(8));page.addView(b,p);}
    private void toggle(String key,String title,String detail){
        LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);group.setPadding(dp(16),dp(13),dp(16),dp(14));group.setBackground(rounded(palette.surface,18));
        Switch value=new Switch(this);value.setText(title);value.setTextColor(palette.text);value.setTextSize(16);value.setChecked(integrations.enabled(key));value.setPadding(0,0,0,dp(8));
        value.setOnCheckedChangeListener((v,checked)->{integrations.prefs().edit().putBoolean(key,checked).apply();if(key.equals("music") && !checked)AppleMediaClient.stopIfStarted();integrations.clearNotifications();BridgeNotificationListener.refresh();});group.addView(value);
        TextView description=new TextView(this);description.setText(detail);description.setTextSize(14);description.setTextColor(palette.muted);group.addView(description);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);page.addView(group,p);
    }
    private EditText field(String key,String hint,int max,int input){
        EditText value=new EditText(this);value.setHint(hint);value.setSingleLine(true);value.setInputType(input);
        value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});value.setText(integrations.prefs().getString(key,""));value.setTextColor(palette.text);value.setHintTextColor(palette.muted);value.setBackground(rounded(palette.surface,12));value.setPadding(dp(14),dp(13),dp(14),dp(13));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);page.addView(value,p);return value;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);integrations=PhoneIntegrations.get(this);palette=new PhonePalette(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(palette.background);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(36),dp(22),dp(30));scroll.addView(page);setContentView(scroll);
        label("Apps & integrations",28);label("Choose what appears on your Subaru dashboard.",15);
        access=new TextView(this);access.setTextColor(palette.muted);page.addView(access);
        button("Manage notification access",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        toggle("music","Music controls","Use an Android music app, or the optional iPhone Bluetooth media connection for Taurus and other iPhone players.");
        musicStatus=new TextView(this);musicStatus.setTextColor(palette.muted);musicStatus.setPadding(0,dp(12),0,dp(4));page.addView(musicStatus);
        button("Choose music source",()->new AlertDialog.Builder(this).setTitle("Music source").setSingleChoiceItems(new String[]{"Android music apps","iPhone media · experimental"},integrations.prefs().getString("musicSource","android").equals("iphone")?1:0,(dialog,which)->{integrations.prefs().edit().putString("musicSource",which==1?"iphone":"android").apply();if(which==0)AppleMediaClient.stopIfStarted();dialog.dismiss();}).setNegativeButton("Cancel",null).show());
        button("Choose Android music app",this::chooseAndroidPlayer);
        button("Connect paired iPhone media",this::chooseIPhone);
        button("Disconnect iPhone media",()->AppleMediaClient.get(this).disconnect());
        label("iPhone media uses Apple's standard Bluetooth service, when the iPhone exposes it. Start Taurus on your iPhone. This carries track information and supported controls; audio continues through normal Bluetooth media pairing. The Subaru's simultaneous iPhone audio and Pixel data connection still needs testing.",14);
        toggle("navigation","Google Maps directions","Show the current direction from Google Maps. Start navigation on your phone.");
        toggle("messages","Share messages","Show recent message notifications while unlocked. Off by default; content stays out of reports.");
        toggle("notifications","Share notifications","Show up to 20 recent app notifications while unlocked. Android may withhold sensitive content; secret notifications are excluded.");
        toggle("ongoing","Include ongoing notifications","Also show persistent notifications, such as background app activity.");
        toggle("navigationFirst","Navigation first","Place Maps first on your dashboard.");
        button("How your data is used",()->new AlertDialog.Builder(this).setTitle("Your dashboard data").setMessage("Notification content stays in memory for up to 30 minutes and is excluded from reports. Sharing is off by default and only available while this phone is unlocked. Android may withhold sensitive content.\n\nMaps shows direction text rather than an embedded map. Calls and new messages open the phone's normal apps. Existing-message replies require confirmation on this phone. Audio uses normal Bluetooth pairing.").setPositiveButton("Done",null).show());
        label("Phone favorites",22);
        EditText number=field("number","Favorite phone number",32,InputType.TYPE_CLASS_PHONE);
        EditText destination=field("destination","Favorite Google Maps destination",160,InputType.TYPE_CLASS_TEXT);
        EditText reply=field("reply","Quick reply text",160,InputType.TYPE_CLASS_TEXT);
        if(reply.getText().length()==0)reply.setText("I'm on my way.");
        button("Save favorites",()->{
            String n=number.getText().toString().trim(),d=destination.getText().toString().trim(),r=reply.getText().toString().trim();
            if(!n.isEmpty() && !PhoneIntegrations.validNumber(n)){number.setError("Use a standard phone number");return;}
            if(!d.isEmpty() && !PhoneIntegrations.validDestination(d)){destination.setError("Use a destination up to 160 characters");return;}
            if(r.isEmpty()){reply.setError("Enter a quick reply");return;}
            boolean savedOk=integrations.prefs().edit().putString("number",n).putString("destination",d).putString("reply",r).commit();
            Toast.makeText(this,savedOk?"Favorites saved on this phone":"Could not save favorites",Toast.LENGTH_SHORT).show();
        });
        label("Calls open the dialer. New messages open your SMS app. Existing-notification replies require a separate confirmation on this phone before delivery. SubaruBridge does not take over your default phone or SMS app.",14);
        button("Review pending action",()->startActivity(new Intent(this,PhoneActionActivity.class)));
        button("Back to SubaruBridge",this::finish);
    }
    private void chooseAndroidPlayer(){
        List<String> packages=new ArrayList<>();List<String> labels=new ArrayList<>();packages.add("");labels.add("Follow the active Android player");
        for(MediaController player:integrations.musicPlayers())if(!packages.contains(player.getPackageName())){packages.add(player.getPackageName());labels.add(player.getPackageName());}
        int selected=Math.max(0,packages.indexOf(integrations.prefs().getString("musicPackage","")));
        new AlertDialog.Builder(this).setTitle("Android music player").setSingleChoiceItems(labels.toArray(new String[0]),selected,(dialog,which)->{integrations.prefs().edit().putString("musicPackage",packages.get(which)).putString("musicSource","android").apply();AppleMediaClient.stopIfStarted();dialog.dismiss();}).setMessage(packages.size()==1?"Start a music app and enable notification access to list its active session.":null).setNegativeButton("Cancel",null).show();
    }
    private void chooseIPhone(){
        try {
            if(!integrations.enabled("music")){Toast.makeText(this,"Enable Music controls first",Toast.LENGTH_LONG).show();return;}
            BluetoothManager manager=getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()){Toast.makeText(this,"Enable Bluetooth on the SubaruBridge home screen first",Toast.LENGTH_LONG).show();return;}
            List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            if(devices.isEmpty()){new AlertDialog.Builder(this).setTitle("Pair your iPhone first").setMessage("Pair the iPhone and Pixel normally in Bluetooth settings. Then return here. This app does not replace either phone's authorization.").setPositiveButton("Bluetooth settings",(d,w)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).setNegativeButton("Cancel",null).show();return;}
            String[] names=new String[devices.size()];for(int i=0;i<names.length;i++){String name=devices.get(i).getName();names[i]=name==null?"Paired device "+(i+1):name;}
            new AlertDialog.Builder(this).setTitle("Choose your paired iPhone").setItems(names,(dialog,which)->{integrations.prefs().edit().putString("musicSource","iphone").apply();AppleMediaClient.get(this).connect(devices.get(which));}).setNegativeButton("Cancel",null).show();
        }catch(SecurityException denied){Toast.makeText(this,"Allow Nearby devices on the SubaruBridge home screen first",Toast.LENGTH_LONG).show();}
    }
    @Override public void onResume(){super.onResume();BridgeNotificationListener.refresh();if(access!=null)access.setText(integrations.hasAccess()?"Android notification access is enabled":"Android notification access is off. iPhone media has a separate Bluetooth authorization.");handler.post(refreshMusic);}
    @Override public void onPause(){handler.removeCallbacks(refreshMusic);super.onPause();}
}
