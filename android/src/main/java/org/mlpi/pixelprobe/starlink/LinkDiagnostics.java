package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, app-owned metadata for offline tests. No packets, addresses or bodies. */
public final class LinkDiagnostics {
    private static final String INSTANCE=UUID.randomUUID().toString();
    private static long received,sent,requests,connections;
    private static boolean journalFull,journalUnavailable;
    private LinkDiagnostics(){}
    public static synchronized void received(int count){received+=count;}
    public static synchronized void sent(int count){sent+=count;}
    public static synchronized Map<String,Object> snapshot(){
        Map<String,Object> value=new LinkedHashMap<>();value.put("instance",INSTANCE);
        value.put("receivedBytes",received);value.put("sentBytes",sent);value.put("httpRequests",requests);value.put("connections",connections);
        value.put("journalFull",journalFull);value.put("journalUnavailable",journalUnavailable);return value;
    }
    public static synchronized void state(Context context,String state){
        if(state.startsWith("Connected:"))connections++;
        Map<String,Object> event=snapshot();event.put("event","state");
        event.put("state",state.startsWith("Ready:")?"ready":state.startsWith("Connected:")?"connected":state.startsWith("Disconnected:")?"disconnected":state.startsWith("Starting:")?"starting":"stopped");append(context,event);
    }
    public static synchronized void http(Context context,String source,String method,String route,int status,int bytes){
        requests++;Map<String,Object> event=snapshot();event.put("event","http");
        event.put("source",source);
        event.put("method",method.equals("GET")||method.equals("POST")||method.equals("HEAD")?method:"other");
        event.put("route",route);event.put("status",status);event.put("responseBytes",bytes);append(context,event);
    }
    private static void append(Context context,Map<String,Object> event){
        event.put("schema",1);event.put("build",DiagnosticEngine.BUILD);event.put("serverTimeMs",System.currentTimeMillis());event.put("uptimeMs",SystemClock.elapsedRealtime());
        SessionRecorder.get(context).record("transport",event);
        File directory=new File(context.getFilesDir(),"subarubridge-diagnostics");
        if(!directory.isDirectory()&&!directory.mkdirs()){journalUnavailable=true;return;}
        File file=new File(directory,"transport-private.jsonl");
        byte[] bytes=(new JSONObject(event).toString()+"\n").getBytes(StandardCharsets.UTF_8);
        try{BoundedJournal.append(file,new File(directory,"transport-previous-private.jsonl"),bytes);journalFull=false;journalUnavailable=false;}
        catch(IOException e){journalFull=file.length()+bytes.length>BoundedJournal.LIMIT;journalUnavailable=true;android.util.Log.w("SubaruBridgeLink","Metadata journal unavailable");}
    }
}
