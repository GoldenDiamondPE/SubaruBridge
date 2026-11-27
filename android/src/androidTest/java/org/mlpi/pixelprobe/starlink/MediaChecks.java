package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.media.session.*;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic providers only. Never contacts an iPhone, Spotify account, or real recipient. */
final class MediaChecks {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static byte[] update(int entity,int attribute,int flags,String text){byte[] data=text.getBytes(StandardCharsets.UTF_8),out=new byte[data.length+3];out[0]=(byte)entity;out[1]=(byte)attribute;out[2]=(byte)flags;System.arraycopy(data,0,out,3,data.length);return out;}
    static void run(Context context,Bundle result) throws Exception {
        for(int i=0;i<64;i++) {
            check(AppleMediaCodec.decode(update(2,2,0,"Fixture \"title\" 🚗")).getString("value").equals("Fixture \"title\" 🚗"),"AMS JNI text changed");
        }
        for(byte[] malformed:new byte[][]{new byte[0],new byte[]{2,4,0},new byte[]{2,2,4},new byte[]{2,2,0,(byte)255},new byte[513],update(0,1,0,"1,NaN,0")}){
            try{AppleMediaCodec.decode(malformed);throw new AssertionError("Malformed AMS update accepted");}catch(java.io.IOException expected){}
        }
        AppleMediaState state=new AppleMediaState();check(state.snapshot(0)==null,"Disconnected iPhone showed media");
        state.begin();state.supported(new byte[]{0,1,3,4,(byte)255});state.ready();
        state.update(update(0,0,0,"Taurus fixture"),0);state.update(update(2,2,0,"Original <song>"),0);
        state.update(update(2,0,0,"Synthetic artist"),0);state.update(update(2,1,0,"Synthetic album"),0);
        state.update(update(2,3,0,"180"),0);state.update(update(0,1,0,"1,1,10.5"),1000);
        MediaSnapshot snapshot=state.snapshot(3500);
        check(snapshot.title.equals("Original <song>") && snapshot.source.equals("Taurus fixture"),"AMS provider metadata missing");
        check(snapshot.positionMs==13000 && snapshot.durationMs==180000,"AMS elapsed-time calculation wrong");
        check(snapshot.commands.equals(new HashSet<>(Arrays.asList("play","pause","next","previous"))),"AMS offered commands not filtered");
        check(state.permits("pause",snapshot.target) && !state.permits("volume-up",snapshot.target),"Unsupported iPhone command enabled");
        state.supported(new byte[]{1});check(!state.permits("play",snapshot.target),"Revoked iPhone capability remained enabled");
        state.update(update(2,2,1,"Short"),4000);check(state.snapshot(4000).truncated,"Truncated iPhone metadata hidden");
        state.update(update(2,2,0,"Full title"),4000);check(!state.snapshot(4000).truncated,"Full update retained truncation flag");
        state.update(update(0,1,0,"0,0,20"),5000);check(state.snapshot(9000).positionMs==20000,"Paused position kept advancing");
        state.update(update(0,0,0,"Another player"),10000);check(!state.permits("pause",snapshot.target),"Old form targeted a new iPhone player");
        check(state.snapshot(10000).title.isEmpty(),"Old player's track survived a player change");
        state.clear();check(state.snapshot(10000)==null && !state.permits("pause",snapshot.target),"Disconnect retained private iPhone content");
        state.begin();state.ready();check(!state.permits("pause",snapshot.target),"Reconnect accepted a previous session's target");
        state.supported(new byte[]{2});check(state.snapshot(0).commands.isEmpty(),"Unknown iPhone state offered a toggle");
        state.update(update(0,1,0,"0,0,0"),0);snapshot=state.snapshot(0);
        check(state.offeredCommand("play")==2 && state.permits("play",snapshot.target) && !state.permits("pause",snapshot.target),"Paused iPhone toggle-only player missing Play");
        state.update(update(0,1,0,"1,1,0"),0);
        check(state.offeredCommand("pause")==2 && !state.permits("play",snapshot.target),"Playing iPhone toggle-only player missing Pause");
        check(PhoneIntegrations.clean("a🚗",2).equals("a"),"Metadata clipping split a UTF-16 surrogate pair");
        PlaybackState toggle=new PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0).setActions(PlaybackState.ACTION_PLAY_PAUSE).build();
        check(PhoneIntegrations.offers(toggle,"play") && !PhoneIntegrations.offers(toggle,"pause"),"Paused toggle control misclassified");
        toggle=new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1).setActions(PlaybackState.ACTION_PLAY_PAUSE).build();
        check(PhoneIntegrations.offers(toggle,"pause") && !PhoneIntegrations.offers(toggle,"play"),"Playing toggle control misclassified");
        toggle=new PlaybackState.Builder().setState(PlaybackState.STATE_BUFFERING,0,0).setActions(PlaybackState.ACTION_PLAY_PAUSE).build();
        check(!PhoneIntegrations.offers(toggle,"play") && !PhoneIntegrations.offers(toggle,"pause"),"Unknown state offered an unsafe toggle");
        result.putString("appleMedia","PASS: Rust/JNI decoder, 64 lifetimes, metadata, elapsed time, capability revocation, player changes, disconnect, reconnect and toggle policy. Bluetooth/iPhone playback remains unverified.");
    }
}
