package org.mlpi.pixelprobe.starlink;

import java.util.*;
import org.json.JSONObject;

/** Session-local AMS read model. Disconnect clears content and invalidates every old form target. */
final class AppleMediaState {
    private String target="",player="",title="",artist="",album="",playback="Unknown";
    private long duration=-1,position=-1,positionAt;
    private double rate;
    private int truncated;
    private int playbackId=-1;
    private boolean active,ready;
    private final Set<Integer> commands=new HashSet<>();
    synchronized void begin(){clear();active=true;target=UUID.randomUUID().toString();}
    synchronized void clear(){active=false;ready=false;target="";player="";title="";artist="";album="";playback="Unknown";playbackId=-1;duration=-1;position=-1;rate=0;truncated=0;commands.clear();}
    synchronized void ready(){ready=active;}
    synchronized void supported(byte[] bytes) {
        if(!active || bytes==null || bytes.length>512)return;
        commands.clear();for(byte b:bytes)if((b&255)<=13)commands.add(b&255);
    }
    static int commandId(String verb) {
        switch(verb){case "play":return 0;case "pause":return 1;case "next":return 3;case "previous":return 4;default:return -1;}
    }
    synchronized boolean permits(String verb,String expected) {
        return active && ready && !target.isEmpty() && target.equals(expected) && offeredCommand(verb)>=0;
    }
    synchronized int offeredCommand(String verb) {
        int direct=commandId(verb);
        if(direct>=0 && commands.contains(direct))return direct;
        if(commands.contains(2) && ((verb.equals("play") && playbackId==0) || (verb.equals("pause") && playbackId==1)))return 2;
        return -1;
    }
    synchronized void update(byte[] bytes,long now) throws Exception {
        if(!active)return;
        JSONObject value=AppleMediaCodec.decode(bytes);
        int entity=value.getInt("entity"),attribute=value.getInt("attribute"),bit=1<<(entity*4+attribute);
        boolean cut=value.getBoolean("truncated");String text=value.getString("value");
        if(cut)truncated|=bit;else truncated&=~bit;
        if(entity==0 && attribute==0) {
            if(!player.isEmpty() && !player.equals(text)) {
                target=UUID.randomUUID().toString();title="";artist="";album="";duration=-1;position=-1;rate=0;playback="Unknown";playbackId=-1;
            }
            player=text;
        } else if(entity==0 && attribute==1) {
            if(cut){playback="Unknown";playbackId=-1;position=-1;rate=0;}
            else {
                int state=value.getInt("state");playbackId=state;playback=state==1?"Playing":state==0?"Paused":state==2?"Rewinding":"Fast forwarding";
                position=milliseconds(value.getDouble("elapsed"));rate=state==1?value.getDouble("rate"):0;positionAt=now;
            }
        } else if(entity==2) {
            switch(attribute){case 0:artist=text;break;case 1:album=text;break;case 2:title=text;break;case 3:duration=cut?-1:milliseconds(Double.parseDouble(text));break;}
        }
    }
    private static long milliseconds(double seconds){return (long)Math.min(Long.MAX_VALUE,Math.max(0,seconds*1000));}
    synchronized MediaSnapshot snapshot(long now) {
        if(!active || !ready)return null;
        Set<String> offered=new HashSet<>();for(String verb:new String[]{"play","pause","next","previous"})if(offeredCommand(verb)>=0)offered.add(verb);
        long elapsed=position;
        if(position>=0 && rate!=0)elapsed=(long)Math.min(Long.MAX_VALUE,Math.max(0,(double)position+Math.max(0,now-positionAt)*rate));
        if(duration>=0 && elapsed>=0)elapsed=Math.min(duration,elapsed);
        return new MediaSnapshot(target,player.isEmpty()?"iPhone media":player,title,artist,album,playback,duration,elapsed,truncated!=0,offered);
    }
}
