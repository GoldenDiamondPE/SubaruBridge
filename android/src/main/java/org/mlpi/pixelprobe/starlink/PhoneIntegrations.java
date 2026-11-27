package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.media.MediaMetadata;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Android adapters. Content remains in memory; opening a dialer is not a completed call. */
public final class PhoneIntegrations {
    private static PhoneIntegrations instance;
    public static synchronized PhoneIntegrations get(Context context) {
        if(instance==null) instance=new PhoneIntegrations(context.getApplicationContext()); return instance;
    }
    final Context context;
    private final LinkedHashMap<String, Notice> messages=new LinkedHashMap<>();
    private final LinkedHashMap<String, Notice> notifications=new LinkedHashMap<>();
    private Notice navigation;
    private Pending pending;
    private PhoneIntegrations(Context context) { this.context=context; }
    public android.content.SharedPreferences prefs() { return context.getSharedPreferences("subarubridge-apps",Context.MODE_PRIVATE); }
    public boolean enabled(String feature) { return prefs().getBoolean(feature,feature.equals("music") || feature.equals("navigation")); }
    public boolean unlocked() { KeyguardManager keyguard=context.getSystemService(KeyguardManager.class); return keyguard==null || !keyguard.isDeviceLocked(); }
    public ComponentName listener() { return new ComponentName(context,BridgeNotificationListener.class); }
    public boolean hasAccess() { NotificationManager manager=context.getSystemService(NotificationManager.class); return manager!=null && manager.isNotificationListenerAccessGranted(listener()); }
    static String clean(CharSequence value, int max) {
        if(value==null)return ""; String text=value.toString().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        return text.length()>max ? text.substring(0,max) : text;
    }
    static boolean validNumber(String value) { return value!=null && value.matches("\\+?[0-9][0-9 ()-]{1,30}[0-9]"); }
    static boolean validDestination(String value) { return value!=null && !value.trim().isEmpty() && value.length()<=160 && !value.matches("(?s).*\\p{Cntrl}.*"); }
    public static final class Notice {
        public final String id, key, title, text, source; public final long received;
        final Notification.Action reply;
        Notice(String key,String title,String text,String source,Notification.Action reply) {
            this.id=UUID.randomUUID().toString();this.key=key;this.title=title;this.text=text;this.source=source;this.reply=reply;received=SystemClock.elapsedRealtime();
        }
        public boolean canReply() { return reply!=null; }
    }
    synchronized void posted(StatusBarNotification record) {
        if(!hasAccess()) { clearNotifications();return; }
        Notification n=record.getNotification(); if(n==null || n.extras==null)return;
        removed(record.getKey());
        if(n.visibility==Notification.VISIBILITY_SECRET)return;
        if(enabled("notifications") && !record.getPackageName().equals(context.getPackageName()) && n.visibility!=Notification.VISIBILITY_SECRET
                && (enabled("ongoing") || (n.flags & Notification.FLAG_ONGOING_EVENT)==0)) {
            notifications.remove(record.getKey());notifications.put(record.getKey(),notice(record,null));
            while(notifications.size()>20)notifications.remove(notifications.keySet().iterator().next());
        }
        if(Notification.CATEGORY_NAVIGATION.equals(n.category) && enabled("navigation") && record.getPackageName().equals("com.google.android.apps.maps")) {
            navigation=notice(record,null); return;
        }
        if(!enabled("messages") || !Notification.CATEGORY_MESSAGE.equals(n.category) || (n.flags & Notification.FLAG_GROUP_SUMMARY)!=0) return;
        Notification.Action reply=null;
        if(n.actions!=null) for(Notification.Action action:n.actions) {
            if(action.actionIntent==null || action.getSemanticAction()!=Notification.Action.SEMANTIC_ACTION_REPLY || action.getRemoteInputs()==null)continue;
            for(RemoteInput input:action.getRemoteInputs())if(input.getAllowFreeFormInput()){reply=action;break;}
            if(reply!=null)break;
        }
        messages.remove(record.getKey()); messages.put(record.getKey(),notice(record,reply));
        while(messages.size()>8) messages.remove(messages.keySet().iterator().next());
    }
    private Notice notice(StatusBarNotification record, Notification.Action reply) {
        Bundle extras=record.getNotification().extras;
        CharSequence body=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(body==null)body=extras.getCharSequence(Notification.EXTRA_TEXT);
        return new Notice(record.getKey(),clean(extras.getCharSequence(Notification.EXTRA_TITLE),100),clean(body,600),record.getPackageName(),reply);
    }
    synchronized void removed(String key) { messages.remove(key);notifications.remove(key); if(navigation!=null && navigation.key.equals(key)) navigation=null; }
    public synchronized void clearNotifications() { messages.clear();notifications.clear();navigation=null; }
    synchronized void replaceNotifications(StatusBarNotification[] active) {
        clearNotifications();if(active!=null)for(StatusBarNotification record:active)posted(record);
    }
    public synchronized List<Notice> notifications() {
        if(!enabled("notifications") || !hasAccess() || !unlocked()){notifications.clear();return Collections.emptyList();}
        notifications.entrySet().removeIf(e -> SystemClock.elapsedRealtime()-e.getValue().received>30*60*1000);
        List<Notice> result=new ArrayList<>(notifications.values());Collections.reverse(result);return result;
    }
    public synchronized Notice navigation() {
        if(!enabled("navigation") || !hasAccess() || !unlocked()) { navigation=null; return null; }
        if(navigation!=null && SystemClock.elapsedRealtime()-navigation.received>10*60*1000)navigation=null;
        return navigation;
    }
    public synchronized List<Notice> messages() {
        if(!enabled("messages") || !hasAccess() || !unlocked()){messages.clear();return Collections.emptyList();}
        messages.entrySet().removeIf(e -> SystemClock.elapsedRealtime()-e.getValue().received>30*60*1000);
        List<Notice> result=new ArrayList<>(messages.values()); Collections.reverse(result);return result;
    }
    public MediaController controller() {
        if(!enabled("music") || !hasAccess() || !unlocked())return null;
        try {
            List<MediaController> sessions=context.getSystemService(MediaSessionManager.class).getActiveSessions(listener());
            for(MediaController c:sessions){PlaybackState s=c.getPlaybackState();if(s!=null && s.getState()==PlaybackState.STATE_PLAYING)return c;}
            return sessions.isEmpty()?null:sessions.get(0);
        }catch(SecurityException revoked){return null;}
    }
    public String mediaCommand(String command) throws LocalHttp.Error {
        MediaController controller=controller();if(controller==null)throw new LocalHttp.Error(409,"No accessible active music session");
        PlaybackState state=controller.getPlaybackState();long actions=state==null?0:state.getActions();
        long required; switch(command){case "play":required=PlaybackState.ACTION_PLAY;break;case "pause":required=PlaybackState.ACTION_PAUSE;break;case "next":required=PlaybackState.ACTION_SKIP_TO_NEXT;break;case "previous":required=PlaybackState.ACTION_SKIP_TO_PREVIOUS;break;default:throw new LocalHttp.Error(400,"Unknown music command");}
        if((actions & required)==0)throw new LocalHttp.Error(409,"The music app does not offer this control");
        try {
            MediaController.TransportControls controls=controller.getTransportControls();
            switch(command){case "play":controls.play();break;case "pause":controls.pause();break;case "next":controls.skipToNext();break;case "previous":controls.skipToPrevious();break;}
        }catch(SecurityException revoked){throw new LocalHttp.Error(403,"Music access was revoked");}
        return "Command sent to your music app. Refresh to confirm its state.";
    }
    public static final class Pending {
        public final String id=UUID.randomUUID().toString(), kind, value;
        final Notification.Action reply; final String messageKey, messageId; final long created=SystemClock.elapsedRealtime();
        Pending(String kind,String value,Notification.Action reply,String messageKey,String messageId){this.kind=kind;this.value=value;this.reply=reply;this.messageKey=messageKey;this.messageId=messageId;}
    }
    public synchronized Pending pending() { if(pending!=null && SystemClock.elapsedRealtime()-pending.created>120000)pending=null;return pending; }
    public synchronized String queue(String kind,String subject) throws LocalHttp.Error {
        if(!unlocked())throw new LocalHttp.Error(403,"Unlock your phone before preparing an action");
        String value=""; Notification.Action reply=null;String messageKey=null,messageId=null;
        switch(kind) {
            case "navigate":value=prefs().getString("destination","");if(!validDestination(value))throw new LocalHttp.Error(409,"Save a destination in phone Integrations first");break;
            case "dial":case "compose":value=prefs().getString("number","");if(!validNumber(value))throw new LocalHttp.Error(409,"Save a phone number in phone Integrations first");break;
            case "reply":
                Notice found=null;for(Notice notice:messages())if(notice.id.equals(subject))found=notice;
                if(found==null || !found.canReply())throw new LocalHttp.Error(409,"Message reply is no longer available");
                reply=found.reply;messageKey=found.key;messageId=found.id;value=prefs().getString("reply","I'm on my way.");break;
            case "spotify":break;
            default:throw new LocalHttp.Error(400,"Unknown phone action");
        }
        if(pending()!=null)throw new LocalHttp.Error(409,"Confirm or dismiss the previous action on your phone first");
        pending=new Pending(kind,value,reply,messageKey,messageId);
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("subarubridge-actions","Phone confirmations",NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open=PendingIntent.getActivity(context,6301,new Intent(context,PhoneActionActivity.class).putExtra("pending",pending.id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try { manager.notify(6301,new Notification.Builder(context,"subarubridge-actions").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("SubaruBridge action ready").setContentText("Open your phone to review and confirm.").setContentIntent(open).setAutoCancel(true).setTimeoutAfter(120000).build()); }
        catch(SecurityException notificationDenied) { /* The Home screen also exposes the pending action. */ }
        return "Prepared. Open SubaruBridge on your phone to review and confirm. Nothing has been sent.";
    }
    public synchronized void cancel(String id) { if(pending!=null && pending.id.equals(id)){pending=null;context.getSystemService(NotificationManager.class).cancel(6301);} }
    public synchronized Intent confirm(String id) throws Exception {
        Pending action=pending();if(action==null || !action.id.equals(id) || !unlocked())throw new LocalHttp.Error(409,"Confirmation expired or phone locked");
        Intent result=null;
        switch(action.kind) {
            case "navigate":result=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=driving&dir_action=navigate&destination="+Uri.encode(action.value)));break;
            case "dial":result=new Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",action.value,null));break;
            case "compose":result=new Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",action.value,null)).putExtra("sms_body",prefs().getString("reply","I'm on my way."));break;
            case "spotify":result=new Intent(Intent.ACTION_VIEW,Uri.parse("https://open.spotify.com/"));break;
            case "reply":
                Notice current=messages.get(action.messageKey);
                if(!enabled("messages") || !hasAccess() || current==null || !current.id.equals(action.messageId) || action.reply==null)throw new LocalHttp.Error(409,"Message access or reply expired");
                Intent fill=new Intent();Bundle answers=new Bundle();
                for(RemoteInput input:action.reply.getRemoteInputs())if(input.getAllowFreeFormInput())answers.putCharSequence(input.getResultKey(),action.value);
                RemoteInput.addResultsToIntent(action.reply.getRemoteInputs(),fill,answers);
                // Remove before delivery: an uncertain result must not be retried as a duplicate send.
                cancel(id); action.reply.actionIntent.send(context,0,fill); return null;
        }
        cancel(id);return result;
    }
}
