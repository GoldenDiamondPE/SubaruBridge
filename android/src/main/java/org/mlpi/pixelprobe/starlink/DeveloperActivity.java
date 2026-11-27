package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.mlpi.pixelprobe.R;
import java.io.IOException;
import java.text.DateFormat;
import java.util.*;

/** Unified original recorder, passive USB lab and phone-only checks. */
public final class DeveloperActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private PhonePalette palette;
    private LinearLayout page,sessions;
    private TextView recordingStatus,health,checkResult;
    private Button record,stop,export,check,markEvent,addNote;
    private boolean resumed,exporting,checking;
    private String sessionSignature="";
    private final Runnable refresh=new Runnable(){public void run(){if(!resumed)return;update();handler.postDelayed(this,1000);}};
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(6),0,dp(6));return v;
    }
    private void section(String title){TextView label=text(title,14,palette.muted);label.setPadding(0,dp(24),0,dp(8));page.addView(label);}
    private Button button(LinearLayout parent,String title,int id,Runnable action) {
        Button v=new Button(this);v.setId(id);v.setText(title);v.setAllCaps(false);v.setTextSize(16);v.setTextColor(palette.accent);
        GradientDrawable bg=new GradientDrawable();bg.setColor(palette.surface);bg.setCornerRadius(dp(14));v.setBackground(bg);
        v.setMinHeight(dp(52));v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setOnClickListener(view->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(5),0,dp(5));parent.addView(v,params);return v;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);palette=new PhonePalette(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((v,insets)->{var b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        ScrollView scroll=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(12),dp(22),dp(32));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        int appearance=palette.id.equals("daylight")?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0;
        getWindow().getInsetsController().setSystemBarsAppearance(appearance,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        button(page,"‹ SubaruBridge",R.id.developer_back,this::finish);
        TextView title=text("Record & diagnose",28,palette.text);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);page.addView(title);
        page.addView(text("Connection tests and developer tools, all on this phone.",15,palette.muted));
        section("ON-DEVICE RECORDER");
        recordingStatus=text("Checking recordings…",17,palette.text);recordingStatus.setId(R.id.recorder_status);recordingStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(recordingStatus);
        page.addView(text("Record app connection events, head-unit checks and passive phone/USB status. Add observations while parked. No laptop, root, account or internet required.",14,palette.muted));
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            button(page,"Allow recording notifications in Android settings",View.NO_ID,()->{
                try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
                catch(RuntimeException unavailable){say("Open Android Settings → Apps → SubaruBridge → Notifications");}
            });
        record=button(page,"Start recording",R.id.recorder_start,()->{
            try{startForegroundService(new Intent(this,RecorderService.class));record.setEnabled(false);handler.postDelayed(this::update,400);}
            catch(RuntimeException failure){say("Keep the app open and try again");}
        });
        markEvent=button(page,"Mark car event",R.id.recorder_mark,()->new AlertDialog.Builder(this).setTitle("What happened?")
                .setItems(new String[]{"Car Bluetooth connected","Opened STARLINK","Display visible","Touch confirmed","Error appeared","Disconnected","Reconnected"},(d,i)->mark(new String[]{"car_connected","starlink_opened","display_visible","touch_received","error_seen","disconnected","reconnected"}[i],"")).show());
        addNote=button(page,"Add private observation",R.id.recorder_note,()->{
            EditText note=new EditText(this);note.setHint("What you observed (240 characters)");note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
            new AlertDialog.Builder(this).setTitle("Private test note").setView(note).setNegativeButton("Cancel",null)
                    .setPositiveButton("Save",(d,i)->mark("note",note.getText().toString())).show();
        });
        stop=button(page,"Stop and save",R.id.recorder_stop,()->{stopService(new Intent(this,RecorderService.class));handler.postDelayed(this::update,300);});
        page.addView(text("Each recording stops after 10 minutes or 1 MiB. The newest 12 recordings are retained; older app recordings rotate automatically. Reports include your private notes.",13,palette.muted));
        section("SAVED RECORDINGS");sessions=new LinearLayout(this);sessions.setOrientation(LinearLayout.VERTICAL);sessions.setId(R.id.recorder_sessions);page.addView(sessions);
        export=button(page,"Export private diagnostics ZIP",R.id.recorder_export,()->{
            if(exporting)return;exporting=true;export.setEnabled(false);
            PrivateReport.save(this,ok->{exporting=false;if(!isFinishing())export.setEnabled(true);say(ok?"Private ZIP saved in Downloads":"Export failed; your originals are preserved");});
        });
        section("PHONE CHECKS");
        check=button(page,"Run phone self-check",R.id.developer_check,()->{
            if(checking)return;checking=true;check.setEnabled(false);checkResult.setText("Checking local components…");
            new Thread(()->{Map<String,Object> result=PhoneSelfCheck.run(this);handler.post(()->{
                checking=false;if(isDestroyed())return;check.setEnabled(true);checkResult.setText(new JSONObject(result).toString());
            });},"bridge-phone-check").start();
        });
        checkResult=text("Checks the Rust engine, maintenance engine and local dashboard. This does not verify the Subaru’s display or touch.",14,palette.muted);checkResult.setId(R.id.developer_check_result);checkResult.setTextIsSelectable(true);page.addView(checkResult);
        button(page,"Open display & touch checks",R.id.developer_headunit,()->{
            startActivity(new Intent(this,StarlinkActivity.class).putExtra("dashboardPath","/subarubridge-diag/basic?screen=1"));finish();
        });
        section("DEVELOPER MODE");
        Switch advanced=new Switch(this);advanced.setId(R.id.developer_enabled);advanced.setText("Show technical details");advanced.setTextColor(palette.text);advanced.setMinHeight(dp(52));
        boolean enabled=getPreferences(MODE_PRIVATE).getBoolean("advanced",false);advanced.setChecked(enabled);page.addView(advanced);
        LinearLayout technical=new LinearLayout(this);technical.setOrientation(LinearLayout.VERTICAL);technical.setVisibility(enabled?View.VISIBLE:View.GONE);page.addView(technical);
        advanced.setOnCheckedChangeListener((v,checked)->{getPreferences(MODE_PRIVATE).edit().putBoolean("advanced",checked).apply();technical.setVisibility(checked?View.VISIBLE:View.GONE);update();});
        health=text("",13,palette.muted);health.setTypeface(Typeface.MONOSPACE);health.setTextIsSelectable(true);health.setId(R.id.developer_health);technical.addView(health);
        button(technical,"Bluetooth settings",View.NO_ID,()->settings(Settings.ACTION_BLUETOOTH_SETTINGS));
        button(technical,"USB & network settings",View.NO_ID,()->settings(Settings.ACTION_WIRELESS_SETTINGS));
        button(technical,"Android developer options",View.NO_ID,()->settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        technical.addView(text("USB lab: observed states only. This app does not change USB functions, enable tethering or send experimental protocol commands. MirrorLink certification/registration and the older AirPlay/CarPlay receiver experiments remain unresolved and are not part of the independent STARLINK transport.",14,palette.muted));
        technical.addView(text("Raw Bluetooth HCI packets need Android system logging privileges. These app recordings contain metadata only; they do not replace an HCI trace. No proprietary STARLINK SDK or application is bundled.",14,palette.muted));
        technical.addView(text("OLDER APP COPIES",14,palette.muted));
        List<LegacyApps.Copy> copies=LegacyApps.installed(this);
        if(copies.isEmpty())technical.addView(text("No older listener apps detected.",14,palette.muted));
        for(LegacyApps.Copy copy:copies)button(technical,copy.title+(copy.enabled?" · installed":" · disabled"),View.NO_ID,()->{
            try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+copy.packageName)));}
            catch(RuntimeException unavailable){say("Open Android Settings → Apps to review the old copy");}
        });
        technical.addView(text("Only SubaruBridge is needed for the independent connection. Keep older listeners closed. Android requires you to manage other apps in system settings; their private files are not imported or erased by SubaruBridge.",14,palette.muted));
        page.addView(text("SubaruBridge "+DiagnosticEngine.BUILD+" · original local services",12,palette.muted));
    }
    private void settings(String action){try{startActivity(new Intent(action));}catch(RuntimeException unavailable){say("This settings page is unavailable on your phone");}}
    private void mark(String kind,String note){try{SessionRecorder.get(this).mark(kind,note);say("Observation saved");}catch(IOException failure){say(failure.getMessage());}}
    private void say(String value){Toast.makeText(getApplicationContext(),value,Toast.LENGTH_LONG).show();}
    private static String time(long value){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(value));}
    private void update() {
        if(isFinishing()||recordingStatus==null)return;
        SessionRecorder recorder=SessionRecorder.get(this);JSONObject active=recorder.current();
        recordingStatus.setText(active==null?(recorder.error().isEmpty()?"Ready to record":recorder.error()):"Recording · "+active.optInt("events")+" events · "+active.optLong("bytes")/1024+" KiB");
        record.setEnabled(active==null);stop.setEnabled(active!=null);
        record.setAlpha(active==null?1f:.4f);
        for(Button action:new Button[]{stop,markEvent,addNote}){action.setEnabled(active!=null);action.setAlpha(active!=null?1f:.4f);}
        if(health!=null&&health.isShown())try{health.setText(new JSONObject(DeviceHealth.snapshot(this)).toString(2));}catch(Exception unavailable){health.setText("Phone inspection unavailable");}
        List<JSONObject> saved=recorder.list();StringBuilder signature=new StringBuilder();for(JSONObject value:saved)signature.append(value.optString("id")).append(value.optString("status"));
        if(signature.toString().equals(sessionSignature)&&sessions.getChildCount()>0)return;sessionSignature=signature.toString();sessions.removeAllViews();
        if(saved.isEmpty())sessions.addView(text("Your recordings will appear here.",14,palette.muted));
        for(JSONObject value:saved) {
            String id=value.optString("id");String status=value.optString("status").replace('_',' ');
            button(sessions,time(value.optLong("startedAt"))+" · "+status,View.NO_ID,()->{
                try{TextView timeline=text(recorder.timeline(id),12,palette.text);timeline.setTextIsSelectable(true);timeline.setTypeface(Typeface.MONOSPACE);timeline.setPadding(dp(18),dp(12),dp(18),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(timeline);
                    new AlertDialog.Builder(this).setTitle("Private event timeline · newest 60").setView(scroll).setPositiveButton("Done",null)
                            .setNeutralButton("Delete recording",(d,i)->new AlertDialog.Builder(this).setTitle("Delete this recording?").setMessage("This removes its app event log and private notes from this phone.")
                                    .setNegativeButton("Keep",null).setPositiveButton("Delete",(dd,ii)->{try{recorder.delete(id);sessionSignature="";update();}catch(IOException failure){say(failure.getMessage());}}).show()).show();
                }catch(IOException failure){say(failure.getMessage());}
            });
        }
    }
    @Override public void onResume(){super.onResume();resumed=true;handler.post(refresh);}
    @Override public void onPause(){resumed=false;handler.removeCallbacks(refresh);super.onPause();}
}
