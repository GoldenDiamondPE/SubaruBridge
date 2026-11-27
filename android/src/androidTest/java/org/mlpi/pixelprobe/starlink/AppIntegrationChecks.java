package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.view.KeyEvent;
import android.service.notification.StatusBarNotification;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

/** Synthetic Android fixtures: never place a call, send an SMS or contact a real account. */
final class AppIntegrationChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String page(StarlinkRouter router,String path) {
        LocalHttp.Response response=router.handle(("GET "+path+" HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        check(response.status==200,"App route failed "+path);return new String(response.wire,StandardCharsets.UTF_8);
    }
    private static String form(String page,String action) {
        Matcher forms=Pattern.compile("<form\\b[^>]*>(.*?)</form>",Pattern.DOTALL).matcher(page);
        while(forms.find())if(forms.group(1).contains("value=\""+action+"\"")){
            Matcher fields=Pattern.compile("name=\"([^\"]+)\" value=\"([^\"]*)\"").matcher(forms.group(1));StringBuilder body=new StringBuilder();
            while(fields.find()){if(body.length()>0)body.append('&');body.append(fields.group(1)).append('=').append(fields.group(2));}return body.toString();
        }throw new AssertionError("Missing form "+action);
    }
    private static int post(StarlinkRouter router,String body){return router.handle(("POST /subarubridge-diag/apps/action HTTP/1.0\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\n\r\n"+body).getBytes(StandardCharsets.UTF_8)).status;}
    static void run(Context context,Bundle result) throws Exception {
        ActionTickets tickets=new ActionTickets();String ticket=tickets.issue("pause","track",100);
        check(!tickets.consume(ticket,"next","track",101),"Ticket bound to wrong action");
        check(!tickets.consume(ticket,"pause","other",101),"Ticket bound to wrong target");
        check(tickets.consume(ticket,"pause","track",101),"Valid ticket refused");check(!tickets.consume(ticket,"pause","track",102),"Ticket replay accepted");
        ticket=tickets.issue("pause","track",0);check(!tickets.consume(ticket,"pause","track",300000),"Expired ticket accepted");
        ticket=tickets.issue("first","",1);for(int i=0;i<128;i++)tickets.issue("new","",2);check(!tickets.consume(ticket,"first","",3),"Ticket store unbounded");
        for(String invalid:new String[]{"tel:123","123;456","123#456","123\n456",""})check(!PhoneIntegrations.validNumber(invalid),"Dial string injection accepted");
        check(PhoneIntegrations.validNumber("+12025550100"),"Valid number refused");
        PhoneIntegrations phone=PhoneIntegrations.get(context);Map<String,?> original=phone.prefs().getAll();
        MediaSession session=new MediaSession(context,"SubaruBridge synthetic regression");AtomicInteger paused=new AtomicInteger(),toggles=new AtomicInteger();
        try {
            phone.prefs().edit().putBoolean("music",true).putBoolean("navigation",true).putBoolean("messages",true).putString("musicSource","android").putString("musicPackage","").putString("number","+12025550100").putString("destination","Main & Second?api=evil").apply();
            check(phone.hasAccess(),"Selected device fixture requires notification-listener access");check(phone.unlocked(),"Unlock the selected device for fixture tests");
            session.setCallback(new MediaSession.Callback(){
                @Override public void onPause(){paused.incrementAndGet();}
                @Override public boolean onMediaButtonEvent(Intent intent){KeyEvent key=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);if(key!=null && key.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(key.getAction()==KeyEvent.ACTION_DOWN)toggles.incrementAndGet();return true;}return super.onMediaButtonEvent(intent);}
            },new Handler(Looper.getMainLooper()));
            session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Fixture <track>").putString(MediaMetadata.METADATA_KEY_ARTIST,"Synthetic artist").build());
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1).setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE).build());session.setActive(true);
            StarlinkRouter router=StarlinkRuntime.get(context).router;String music=page(router,"/subarubridge-diag/apps/music");
            check(music.contains("Fixture &lt;track&gt;"),"Media metadata not escaped");String pause=form(music,"music-pause");
            check(post(router,pause)==200,"Media control failed");
            long deadline=SystemClock.elapsedRealtime()+3000;while(paused.get()==0 && SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
            check(paused.get()==1,"Media callback did not reach session");check(post(router,pause)==409,"Media replay not rejected");check(paused.get()==1,"Media replay duplicated command");
            check(post(router,"action=music-pause&subject=&ticket=missing")==409,"Forged ticket accepted");
            check(post(router,"action=music-pause&subject=&ticket=missing&extra=1")==400,"Unknown fields accepted");
            String stale=form(page(router,"/subarubridge-diag/apps/music"),"music-pause");
            phone.prefs().edit().putString("musicPackage","org.example.absent").apply();
            check(phone.controller()==null && post(router,stale)==409,"Selected unavailable player silently fell back");
            phone.prefs().edit().putString("musicPackage","").apply();
            String replacementStale=form(page(router,"/subarubridge-diag/apps/music"),"music-pause");
            MediaSession replacement=new MediaSession(context,"Synthetic replacement player");
            try{
                session.setActive(false);replacement.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1).setActions(PlaybackState.ACTION_PAUSE).build());replacement.setActive(true);
                check(post(router,replacementStale)==409,"Old page controlled a different media session");
            }finally{replacement.release();session.setActive(true);}
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0).setActions(PlaybackState.ACTION_PLAY_PAUSE).build());
            String togglePlay=form(page(router,"/subarubridge-diag/apps/music"),"music-play");
            check(post(router,togglePlay)==200,"Toggle-only player's Play control failed");
            deadline=SystemClock.elapsedRealtime()+3000;while(toggles.get()==0 && SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
            check(toggles.get()==1,"Toggle control did not reach the selected session exactly once");
            check(post(router,togglePlay)==409 && toggles.get()==1,"Toggle command replay was accepted");
            String dial=form(page(router,"/subarubridge-diag/apps/calls"),"dial");check(post(router,dial)==200,"Dial preparation failed");
            PhoneIntegrations.Pending pending=phone.pending();check(pending!=null && pending.kind.equals("dial"),"Dial action not queued");
            Intent dialIntent=phone.confirm(pending.id);check(Intent.ACTION_DIAL.equals(dialIntent.getAction()),"Action would place an unconfirmed call");check(phone.pending()==null,"Confirmation did not consume request");
            check(post(router,dial)==409,"Dial preparation replay accepted");
            phone.queue("navigate","");Intent maps=phone.confirm(phone.pending().id);check("Main & Second?api=evil".equals(maps.getData().getQueryParameter("destination")),"Maps query injection");
            phone.queue("compose","");Intent sms=phone.confirm(phone.pending().id);check(Intent.ACTION_SENDTO.equals(sms.getAction()) && "smsto".equals(sms.getData().getScheme()),"SMS action bypasses system composer");
            Notification navigation=new Notification.Builder(context,"fixture").setContentTitle("Turn right <test>").setContentText("In 300 metres").setCategory(Notification.CATEGORY_NAVIGATION).build();
            StatusBarNotification nav=new StatusBarNotification("com.google.android.apps.maps","com.google.android.apps.maps",10,null,android.os.Process.myUid(),0,0,navigation,android.os.Process.myUserHandle(),System.currentTimeMillis());
            phone.posted(nav);check(page(router,"/subarubridge-diag/apps/navigation").contains("Turn right &lt;test&gt;"),"Navigation fixture absent");phone.removed(nav.getKey());check(phone.navigation()==null,"Removed navigation stayed visible");
            Notification message=new Notification.Builder(context,"fixture").setContentTitle("Fixture sender").setContentText("Private fixture body").setCategory(Notification.CATEGORY_MESSAGE).build();
            StatusBarNotification msg=new StatusBarNotification(context.getPackageName(),context.getPackageName(),11,null,android.os.Process.myUid(),0,0,message,android.os.Process.myUserHandle(),System.currentTimeMillis());
            phone.posted(msg);check(page(router,"/subarubridge-diag/apps/messages").contains("Private fixture body"),"Message opt-in ignored");
            phone.prefs().edit().putBoolean("messages",false).apply();check(phone.messages().isEmpty(),"Disabled message contents retained");
            phone.prefs().edit().putBoolean("notifications",true).putBoolean("ongoing",false).apply();
            Notification general=new Notification.Builder(context,"fixture").setContentTitle("Fixture update").setContentText("Private general body").setVisibility(Notification.VISIBILITY_PRIVATE).build();
            StatusBarNotification update=new StatusBarNotification("org.example.fixture","org.example.fixture",12,null,android.os.Process.myUid(),0,0,general,android.os.Process.myUserHandle(),System.currentTimeMillis());
            phone.posted(update);check(phone.notifications().size()==1,"General notification opt-in failed");
            general.flags|=Notification.FLAG_ONGOING_EVENT;phone.posted(update);check(phone.notifications().isEmpty(),"Ongoing notification not filtered on update");
            phone.prefs().edit().putBoolean("ongoing",true).apply();phone.posted(update);check(phone.notifications().size()==1,"Ongoing notification opt-in ignored");
            general.visibility=Notification.VISIBILITY_SECRET;phone.posted(update);check(phone.notifications().isEmpty(),"Secret update retained old body");
            general.visibility=Notification.VISIBILITY_PRIVATE;
            for(int i=0;i<25;i++)phone.posted(new StatusBarNotification("org.example.fixture","org.example.fixture",100+i,null,android.os.Process.myUid(),0,0,general,android.os.Process.myUserHandle(),System.currentTimeMillis()));
            check(phone.notifications().size()==20,"General notification store unbounded");
            phone.prefs().edit().putBoolean("notifications",false).apply();check(phone.notifications().isEmpty(),"Disabled general notification contents retained");
            phone.prefs().edit().putBoolean("notifications",true).apply();phone.replaceNotifications(new StatusBarNotification[]{update});
            check(phone.notifications().size()==1,"Enabling sharing did not restore an existing active notification");
            AtomicInteger replies=new AtomicInteger();java.util.concurrent.atomic.AtomicReference<String> replyText=new java.util.concurrent.atomic.AtomicReference<>();
            String replyAction=context.getPackageName()+".SYNTHETIC_REPLY";
            BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){Bundle answers=RemoteInput.getResultsFromIntent(intent);if(answers!=null){replyText.set(answers.getString("text"));replies.incrementAndGet();}}};
            if(android.os.Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,new IntentFilter(replyAction),Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,new IntentFilter(replyAction));
            PendingIntent delivery=PendingIntent.getBroadcast(context,953,new Intent(replyAction).setPackage(context.getPackageName()),PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_CANCEL_CURRENT);
            try {
                phone.prefs().edit().putBoolean("messages",true).putString("reply","Synthetic reply only").apply();
                Notification.Action reply=new Notification.Action.Builder(null,"Reply",delivery).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY).addRemoteInput(new RemoteInput.Builder("text").setAllowFreeFormInput(true).build()).build();
                Notification replyNotification=new Notification.Builder(context,"fixture").setContentTitle("Fixture conversation").setContentText("Synthetic reply fixture").setCategory(Notification.CATEGORY_MESSAGE).addAction(reply).build();
                StatusBarNotification replyRecord=new StatusBarNotification(context.getPackageName(),context.getPackageName(),15,null,android.os.Process.myUid(),0,0,replyNotification,android.os.Process.myUserHandle(),System.currentTimeMillis());
                phone.posted(replyRecord);PhoneIntegrations.Notice notice=phone.messages().get(0);phone.queue("reply",notice.id);
                check(replies.get()==0,"Reply sent before confirmation");String id=phone.pending().id;phone.confirm(id);
                deadline=SystemClock.elapsedRealtime()+3000;while(replies.get()==0 && SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
                check(replies.get()==1 && "Synthetic reply only".equals(replyText.get()),"Confirmed RemoteInput reply did not reach Android receiver");
                try{phone.confirm(id);throw new AssertionError("Reply confirmation replay accepted");}catch(LocalHttp.Error expected){check(expected.status==409,"Wrong replay error");}
                check(replies.get()==1,"Reply duplicated after confirmation replay");
                phone.posted(replyRecord);phone.queue("reply",phone.messages().get(0).id);id=phone.pending().id;phone.posted(replyRecord);
                try{phone.confirm(id);throw new AssertionError("Stale reply action accepted");}catch(LocalHttp.Error expected){check(expected.status==409,"Wrong stale reply error");}phone.cancel(id);
            } finally {delivery.cancel();context.unregisterReceiver(receiver);}
            java.io.ByteArrayOutputStream report=new java.io.ByteArrayOutputStream();router.exportReport(report);
            java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(report.toByteArray()));
            java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] block=new byte[4096];int count;while((count=zip.read(block))!=-1)bytes.write(block,0,count);String text=bytes.toString("UTF-8");check(!text.contains("Private fixture body") && !text.contains("Fixture sender") && !text.contains("+12025550100") && !text.contains("Private general body") && !text.contains("Synthetic reply only") && !text.contains("Main & Second") && !text.contains("Fixture <track>"),"Personal app content leaked into diagnostic report");}
            result.putString("apps","PASS: real Android media callback; one confirmed RemoteInput reply and stale/replay rejection; Maps encoding; dialer/SMS intents; bounded notification feed; report privacy");
        } finally {
            session.release();PhoneIntegrations.Pending pending=phone.pending();if(pending!=null)phone.cancel(pending.id);phone.clearNotifications();
            SharedPreferences.Editor restore=phone.prefs().edit().clear();for(Map.Entry<String,?> entry:original.entrySet())if(entry.getValue() instanceof Boolean)restore.putBoolean(entry.getKey(),(Boolean)entry.getValue());else if(entry.getValue() instanceof String)restore.putString(entry.getKey(),(String)entry.getValue());restore.commit();
        }
    }
}
