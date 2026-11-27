package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.*;

/** Device-only setup. Message sharing is off until explicitly enabled here. */
public final class IntegrationsActivity extends Activity {
    private PhoneIntegrations integrations;
    private LinearLayout page;
    private TextView access;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setPadding(0,dp(12),0,dp(8));page.addView(v);}
    private void button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->action.run());page.addView(b);}
    private void toggle(String key,String title,String detail){
        Switch value=new Switch(this);value.setText(title);value.setChecked(integrations.enabled(key));value.setPadding(0,dp(14),0,dp(10));
        value.setOnCheckedChangeListener((v,checked)->{integrations.prefs().edit().putBoolean(key,checked).apply();integrations.clearNotifications();});page.addView(value);label(detail,14);
    }
    private EditText field(String key,String hint,int max,int input){
        EditText value=new EditText(this);value.setHint(hint);value.setSingleLine(true);value.setInputType(input);
        value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});value.setText(integrations.prefs().getString(key,""));page.addView(value);return value;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);integrations=PhoneIntegrations.get(this);
        ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(36),dp(22),dp(30));scroll.addView(page);setContentView(scroll);
        label("Apps & integrations",28);label("These controls use Android's public APIs. Car audio and calls use your normal Bluetooth pairing.",15);
        access=new TextView(this);page.addView(access);
        button("Manage notification access",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        toggle("music","Music controls","Read active music metadata and send supported play, pause, previous and next commands. Start Spotify or another music app on your phone first.");
        toggle("navigation","Google Maps directions","Show Google Maps' active navigation notification. This is a direction card, not an embedded Google Maps display. No Maps API key is required.");
        toggle("messages","Share message notifications","Off by default. Shares recent message title and text with the paired head unit while this phone is unlocked. Only notifications Android exposes are available; no inbox history is read. Contents stay in memory and are excluded from reports.");
        toggle("notifications","Share general notifications","Off by default. Shows up to 20 recent app notifications on the head unit while your phone is unlocked. Android may withhold sensitive content. Secret notifications and SubaruBridge's own status notifications are excluded. No notification contents are saved in reports.");
        toggle("ongoing","Include ongoing notifications","Include persistent app notifications in the general feed. SubaruBridge status notices remain excluded.");
        toggle("navigationFirst","Navigation first on dashboard","Choose navigation as the first dashboard tile. All titles, icons, colors and tile order can also be edited in dashboard.json before building.");
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
    @Override public void onResume(){super.onResume();if(access!=null)access.setText(integrations.hasAccess()?"Notification access is enabled":"Notification access is off. Music, navigation and message reads are unavailable.");}
}
