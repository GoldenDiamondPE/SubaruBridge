package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.os.SystemClock;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Original, bounded app-metadata recorder. No shell, root helper or vendor runtime. */
public final class SessionRecorder {
    static final int MAX_SESSIONS=12, MAX_BYTES=1024*1024;
    private static SessionRecorder instance;
    private final File directory;
    private JSONObject active;
    private String error="";
    public static synchronized SessionRecorder get(Context context) {
        if(instance==null)instance=new SessionRecorder(new File(context.getFilesDir(),"diagnostic-sessions"));
        return instance;
    }
    /** Isolated directory constructor used by device tests. */
    SessionRecorder(File directory) {
        this.directory=directory;
        try {
            ensureDirectory();
            // A process cannot resume its former live capture. Retain the partial run honestly.
            for(JSONObject item:list()) if("recording".equals(item.optString("status"))) {
                item.put("status","interrupted");item.put("endedAt",System.currentTimeMillis());save(item);
            }
        } catch(Exception failure){error="Previous recording could not be recovered";}
    }
    private void ensureDirectory() throws IOException {
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Recording storage unavailable");
    }
    private static boolean valid(String id){return id!=null&&id.matches("[a-f0-9-]{36}");}
    private File file(String id,String suffix) throws IOException {
        if(!valid(id))throw new IOException("Invalid recording");
        File value=new File(directory,id+suffix);
        if(Files.isSymbolicLink(value.toPath()))throw new IOException("Recording link rejected");
        return value;
    }
    private void save(JSONObject value) throws IOException {
        AtomicFile atomic=new AtomicFile(file(value.optString("id"),".json"));FileOutputStream out=null;
        try {out=atomic.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}
        catch(IOException failure){atomic.failWrite(out);throw failure;}
    }
    public synchronized List<JSONObject> list() {
        List<JSONObject> result=new ArrayList<>();File[] files=directory.listFiles((d,n)->n.endsWith(".json"));
        if(files!=null)for(File value:files)try {
            if(value.length()>8192||Files.isSymbolicLink(value.toPath()))continue;
            JSONObject item;
            try(InputStream in=new AtomicFile(value).openRead()) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] block=new byte[2048];int n;
                while((n=in.read(block))!=-1){if(bytes.size()+n>8192)throw new IOException("Recording header limit");bytes.write(block,0,n);}
                item=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
            if(item.optInt("schema")==1&&valid(item.optString("id"))&&value.getName().equals(item.optString("id")+".json"))result.add(item);
        }catch(Exception ignored){/* Keep unreadable originals for explicit recovery. */}
        result.sort((a,b)->Long.compare(b.optLong("startedAt"),a.optLong("startedAt")));return result;
    }
    public synchronized boolean recording(){return active!=null;}
    public synchronized String error(){return error;}
    public synchronized String currentId(){return active==null?null:active.optString("id");}
    public synchronized JSONObject current() {return active==null?null:copy(active);}
    private static JSONObject copy(JSONObject value){try{return new JSONObject(value.toString());}catch(Exception impossible){return new JSONObject();}}
    public synchronized void start() throws IOException {
        if(active!=null)throw new IOException("A recording is already running");ensureDirectory();
        List<JSONObject> previous=list();
        // Retain at most twelve complete app sessions. Never walk arbitrary paths.
        while(previous.size()>=MAX_SESSIONS) {
            JSONObject oldest=previous.remove(previous.size()-1);delete(oldest.optString("id"));
        }
        JSONObject next=new JSONObject();String id=UUID.randomUUID().toString();
        try {
            next.put("schema",1);next.put("product","SubaruBridge");next.put("build",DiagnosticEngine.BUILD);
            next.put("id",id);next.put("startedAt",System.currentTimeMillis());next.put("status","recording");
            next.put("events",0);next.put("bytes",0);
            if(!file(id,".jsonl").createNewFile())throw new IOException("Recording already exists");
            save(next);active=next;error="";record("start",Map.of("scope","App metadata only; no audio, messages, GPS or raw packets"));
        }catch(org.json.JSONException failure){error="Recording metadata failed";throw new IOException(error,failure);}
        catch(IOException failure){error="Recording could not start: storage unavailable";throw failure;}
    }
    public synchronized void record(String kind,Map<String,?> fields) {
        if(active==null)return;
        try {
            JSONObject event=new JSONObject(fields);event.put("event",kind);event.put("schema",1);
            event.put("timeMs",System.currentTimeMillis());event.put("uptimeMs",SystemClock.elapsedRealtime());
            byte[] bytes=(event.toString()+"\n").getBytes(StandardCharsets.UTF_8);
            File journal=file(active.optString("id"),".jsonl");
            if(bytes.length>8192||journal.length()+bytes.length>MAX_BYTES){stop("storage_limit");return;}
            try(FileOutputStream out=new FileOutputStream(journal,true)){out.write(bytes);out.getFD().sync();}
            active.put("events",active.optInt("events")+1);active.put("bytes",journal.length());
            // Checkpoint counters, so a killed process retains its last acknowledged event.
            save(active);
        }catch(Exception failure){error="Recording stopped: storage unavailable";try{stop("storage_error");}catch(IOException ignored){active=null;}}
    }
    public synchronized void mark(String kind,String note) throws IOException {
        if(active==null)throw new IOException("Start a recording first");
        if(!Set.of("car_connected","starlink_opened","display_visible","touch_received","error_seen","disconnected","reconnected","note").contains(kind))throw new IOException("Unknown observation");
        if(note==null)note="";if(note.length()>240)throw new IOException("Keep the note under 240 characters");
        record("observation",Map.of("kind",kind,"note",note.replaceAll("[\\p{Cntrl}]"," ")));
        if(!error.isEmpty())throw new IOException(error);
    }
    public synchronized void stop(String reason) throws IOException {
        if(active==null)return;
        try {active.put("status",reason);active.put("endedAt",System.currentTimeMillis());save(active);active=null;}
        catch(org.json.JSONException failure){throw new IOException("Could not finalize recording",failure);}
    }
    public synchronized void delete(String id) throws IOException {
        if(!valid(id))throw new IOException("Invalid recording");
        if(active!=null&&id.equals(active.optString("id")))throw new IOException("Stop this recording first");
        for(String suffix:new String[]{".jsonl",".json",".json.bak",".json.new"}) {
            File value=file(id,suffix);if(value.exists()&&!value.delete())throw new IOException("Could not delete recording");
        }
    }
    public synchronized String timeline(String id) throws IOException {
        File value=file(id,".jsonl");if(!value.isFile()||value.length()>MAX_BYTES)throw new IOException("Recording unavailable");
        ArrayDeque<String> lines=new ArrayDeque<>();
        try(BufferedReader in=Files.newBufferedReader(value.toPath(),StandardCharsets.UTF_8)) {
            String line;while((line=in.readLine())!=null){if(lines.size()==60)lines.removeFirst();lines.addLast(line);}
        }
        return String.join("\n\n",lines);
    }
    /** Caller must choose a destination; sensitive free-text notes are explicitly private. */
    public synchronized void export(ZipOutputStream zip) throws IOException {
        for(JSONObject item:list())for(String suffix:new String[]{".json",".jsonl"}) {
            File value=file(item.optString("id"),suffix);if(!value.isFile())continue;
            if(value.length()>MAX_BYTES)throw new IOException("Recording size limit");
            zip.putNextEntry(new ZipEntry("recordings/"+value.getName()));
            try(InputStream in=new FileInputStream(value)) {byte[] block=new byte[8192];int n,total=0;
                while((n=in.read(block))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Recording size limit");zip.write(block,0,n);}}
            zip.closeEntry();
        }
    }
}
