package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import java.io.IOException;
import java.util.*;

/** Script-free app cards backed by explicit Android adapters, never fictitious success. */
final class DashboardApps {
    private static final String BASE="/subarubridge-diag/apps/";
    private final Context context;
    private final PhoneIntegrations phone;
    private final ActionTickets tickets=new ActionTickets();
    DashboardApps(Context context){this.context=context;phone=PhoneIntegrations.get(context);}
    private String form(String action,String subject,String label){
        return "<form class=\"control-form\" method=\"post\" action=\""+BASE+"action\">"+HtmlView.hidden("action",action)+HtmlView.hidden("subject",subject)
            +HtmlView.hidden("ticket",tickets.issue(action,subject,SystemClock.elapsedRealtime()))+"<button class=\"app-control\" type=\"submit\">"+HtmlView.escape(label)+"</button></form>";
    }
    String perform(Map<String,String> fields) throws LocalHttp.Error {
        if(fields.size()!=3 || !fields.keySet().containsAll(Arrays.asList("action","subject","ticket")))throw new LocalHttp.Error(400,"Invalid app action fields");
        String action=fields.get("action"),subject=fields.get("subject");
        if(!tickets.consume(fields.get("ticket"),action,subject,SystemClock.elapsedRealtime()))throw new LocalHttp.Error(409,"Action expired or already used. Refresh before trying again.");
        if(!phone.unlocked())throw new LocalHttp.Error(403,"Unlock your phone before using personal app controls");
        if(action.startsWith("music-"))return phone.mediaCommand(action.substring(6),subject);
        return phone.queue(action,subject);
    }
    String actionPage(String notice) throws IOException { return new CarUi(context).app("Action prepared", "Review the result below.", "<p>"+HtmlView.escape(notice)+"</p>","music"); }
    String page(String app) throws Exception {
        StringBuilder body=new StringBuilder();String title,subtitle;
        switch(app){
            case "music":
                title="Now playing";subtitle="Your selected music source";
                MediaSnapshot music=phone.music();
                if(music==null){body.append("<h2>No accessible music session</h2><p>Choose Android or iPhone media in phone Integrations. Unlock the Pixel and start your selected player. Android reads require notification access; iPhone media requires its separate authorized Bluetooth connection.</p>");}
                else {
                    body.append("<h2>").append(HtmlView.escape(music.title.isEmpty()?"Music session":music.title)).append("</h2><p class=\"muted\">").append(HtmlView.escape(music.artist)).append("</p><p class=\"small\">")
                        .append(HtmlView.escape(music.state)).append(" · ").append(HtmlView.escape(music.source)).append("</p>");
                    if(!music.album.isEmpty())body.append("<p>").append(HtmlView.escape(music.album)).append("</p>");
                    if(music.positionMs>=0)body.append("<p class=\"small\">").append(time(music.positionMs)).append(music.durationMs>=0?" / "+time(music.durationMs):"").append(" · Refresh for current position</p>");
                    if(music.truncated)body.append("<p class=\"small\">Some iPhone text is shortened by Bluetooth.</p>");
                    String[] verbs={"previous","play","pause","next"},labels={"Previous","Play","Pause","Next"};
                    for(int i=0;i<verbs.length;i++)if(music.commands.contains(verbs[i]))body.append(form("music-"+verbs[i],music.target,labels[i]));
                }
                if(!phone.prefs().getString("musicSource","android").equals("iphone"))body.append(form("spotify","","Prepare Spotify on Android"));
                body.append("<p class=\"small\">For Taurus, start playback on the iPhone. Audio uses normal Bluetooth media pairing. Commands are requests; refresh to confirm the music app's state.</p>");break;
            case "navigation":
                title="Directions";subtitle="Google Maps direction card";
                PhoneIntegrations.Notice navigation=phone.navigation();
                if(navigation==null)body.append("<h2>No current directions</h2><p>Start Google Maps navigation on your unlocked phone. Enable navigation and notification access in Integrations.</p>");
                else body.append("<h2>").append(HtmlView.escape(navigation.title)).append("</h2><p>").append(HtmlView.escape(navigation.text)).append("</p><p class=\"small\">Google Maps notification Â· ").append((SystemClock.elapsedRealtime()-navigation.received)/1000).append(" seconds since phone read</p>");
                body.append(form("navigate","","Prepare favorite destination")).append("<p class=\"small\">Directions are read from Android notifications. A live map is not embedded. Refresh for the latest instruction.</p>");break;
            case "calls":
                title="Phone";subtitle="Your car's normal hands-free calling";
                body.append("<h2>Call your favorite</h2><p>Set a favorite number in phone Integrations. Prepare it here, then confirm on your phone.</p>").append(form("dial","","Prepare dialer"))
                    .append("<p class=\"small\">Place, answer and end calls using your phone or the Subaru's factory hands-free controls. SubaruBridge does not replace the default dialer or report a call as connected.</p>");break;
            case "messages":
                title="Messages";subtitle="Recent Android notifications Â· optional sharing";
                List<PhoneIntegrations.Notice> notices=phone.messages();
                if(notices.isEmpty())body.append("<h2>No shared messages</h2><p>Message sharing is off by default. Enable it in phone Integrations and keep the phone unlocked. Android may withhold sensitive notifications.</p>");
                for(PhoneIntegrations.Notice notice:notices){
                    body.append("<article class=\"message-card\"><h2>").append(HtmlView.escape(notice.title)).append("</h2><p>").append(HtmlView.escape(notice.text)).append("</p>");
                    if(notice.canReply())body.append(form("reply",notice.id,"Prepare quick reply"));body.append("</article>");
                }
                body.append(form("compose","","Prepare new SMS")).append("<p class=\"small\">Every reply requires confirmation on your phone. Message contents are not written to diagnostic reports.</p>");break;
            case "notifications":
                title="Notifications";subtitle="Recent Android app notifications Â· optional sharing";
                List<PhoneIntegrations.Notice> notifications=phone.notifications();
                if(notifications.isEmpty())body.append("<h2>No shared notifications</h2><p>Enable general notification sharing in phone Integrations and keep the phone unlocked. Android may withhold sensitive content.</p>");
                for(PhoneIntegrations.Notice notice:notifications)body.append("<article class=\"message-card\"><div class=\"small\">").append(HtmlView.escape(notice.source)).append("</div><h2>").append(HtmlView.escape(notice.title)).append("</h2><p>").append(HtmlView.escape(notice.text)).append("</p></article>");
                body.append("<p class=\"small\">Up to 20 notifications, held in memory for at most 30 minutes. Refresh for updates. Contents are excluded from diagnostic exports.</p>");break;
            default:throw new LocalHttp.Error(404,"Unknown dashboard app");
        }
        return new CarUi(context).app(title,subtitle,body.toString(),app);
    }
    private static String time(long ms){long seconds=Math.max(0,ms)/1000;return (seconds/60)+":"+String.format(java.util.Locale.ROOT,"%02d",seconds%60);}

}
