package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

/** User-visible confirmation; never automatically confirms a car or web request. */
public final class PhoneActionActivity extends Activity {
    private PhoneIntegrations integrations;
    private PhoneIntegrations.Pending action;
    private TextView status;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);integrations=PhoneIntegrations.get(this);action=integrations.pending();
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(24*getResources().getDisplayMetrics().density);page.setPadding(pad,pad*2,pad,pad);setContentView(page);
        TextView title=new TextView(this);title.setText("Review phone action");title.setTextSize(27);page.addView(title);
        status=new TextView(this);status.setTextSize(18);status.setPadding(0,pad,0,pad);page.addView(status);
        String requested=getIntent().getStringExtra("pending");
        if(action==null || (requested!=null && !requested.equals(action.id))){status.setText("No current action. Requests expire after two minutes.");return;}
        if(!integrations.unlocked()){status.setText("Unlock your phone, then reopen this confirmation.");return;}
        status.setText(description(action));
        Button confirm=new Button(this);confirm.setAllCaps(false);confirm.setText(action.kind.equals("reply")?"Send this reply":"Open phone app");page.addView(confirm);
        confirm.setOnClickListener(v->{
            confirm.setEnabled(false);
            try { Intent intent=integrations.confirm(action.id);if(intent!=null){startActivity(intent);status.setText("Opened phone app. Complete the action there.");}else status.setText("Reply handed to the message app. Delivery is not confirmed."); }
            catch(Exception failed){status.setText("Action unavailable or expired. Nothing will be retried automatically.");}
        });
        Button cancel=new Button(this);cancel.setText("Dismiss");cancel.setAllCaps(false);page.addView(cancel);cancel.setOnClickListener(v->{integrations.cancel(action.id);finish();});
    }
    private String description(PhoneIntegrations.Pending action){
        switch(action.kind){case "dial":return "Open the dialer with:\n"+action.value+"\nYou choose whether to place the call.";
            case "compose":return "Open your SMS app for:\n"+action.value+"\nReview and send the message there.";
            case "navigate":return "Open Google Maps directions to:\n"+action.value;
            case "reply":return "Send this reply to the selected message notification:\n\n"+action.value;
            default:return "Open Spotify on this phone.";}
    }
}
