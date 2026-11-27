package org.mlpi.pixelprobe.starlink;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Isolated recorder regression tests; never delete or fill an owner's records. */
final class RecorderChecks {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    static void run(Instrumentation instrumentation,Context context,Bundle result) throws Exception {
        File directory=new File(context.getCacheDir(),"recorder-test-"+UUID.randomUUID());
        try {
            SessionRecorder recorder=new SessionRecorder(directory);check(!recorder.recording(),"Unexpected active test recording");
            recorder.start();String id=recorder.currentId();check(id!=null,"Recording not started");
            boolean duplicate=false;try{recorder.start();}catch(IOException expected){duplicate=true;}check(duplicate,"Duplicate recording accepted");
            recorder.mark("car_connected","");recorder.mark("note","synthetic observation\nsecond line");
            recorder.record("transport",Map.of("route","launcher","status",200));
            check(recorder.current().getInt("events")==4,"Recorder event count");
            check(recorder.timeline(id).contains("synthetic observation second line"),"Notes not retained/sanitized");
            boolean liveDelete=false;try{recorder.delete(id);}catch(IOException expected){liveDelete=true;}check(liveDelete,"Active recording deleted");
            recorder.stop("saved");check(!recorder.recording(),"Recording did not stop");
            boolean lateMark=false;try{recorder.mark("note","late");}catch(IOException expected){lateMark=true;}check(lateMark,"Observation accepted after stop");
            for(String invalid:new String[]{"../other","",id+"/../../x"}) {
                boolean rejected=false;try{recorder.delete(invalid);}catch(IOException expected){rejected=true;}check(rejected,"Unsafe recording identifier accepted");
            }
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){recorder.export(zip);}
            int entries=0;boolean note=false;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                ZipEntry item;while((item=zip.getNextEntry())!=null){entries++;check(item.getName().startsWith("recordings/"+id),"Unexpected export entry");
                    note|=new String(zip.readAllBytes(),StandardCharsets.UTF_8).contains("synthetic observation");}
            }
            check(entries==2&&note,"Recording export omitted metadata or notes");
            recorder.start();String interrupted=recorder.currentId();recorder.record("transport",Map.of("status",200));
            SessionRecorder recovered=new SessionRecorder(directory);
            check(!recovered.recording(),"Recovered capture falsely still running");
            JSONObject interruptedItem=recovered.list().stream().filter(v->interrupted.equals(v.optString("id"))).findFirst().orElseThrow();
            check("interrupted".equals(interruptedItem.getString("status")),"Process interruption not marked");
            recovered.start();String bounded=recovered.currentId();
            for(int i=0;i<600&&recovered.recording();i++)recovered.record("limit_test",Map.of("padding","x".repeat(2000)));
            check(!recovered.recording(),"Recording exceeded byte limit");
            check(new File(directory,bounded+".jsonl").length()<=SessionRecorder.MAX_BYTES,"Recording grew past limit");
            for(int i=0;i<SessionRecorder.MAX_SESSIONS+2;i++){recovered.start();recovered.stop("saved");}
            check(recovered.list().size()==SessionRecorder.MAX_SESSIONS,"Retention count exceeded");
            check(Objects.requireNonNull(directory.listFiles()).length==SessionRecorder.MAX_SESSIONS*2,"Retention left orphan logs");
            for(JSONObject item:recovered.list())recovered.delete(item.getString("id"));
            check(recovered.list().isEmpty(),"Recording delete incomplete");
            File journal=new File(directory,"journal.jsonl"),previous=new File(directory,"previous.jsonl");
            BoundedJournal.append(journal,previous,new byte[BoundedJournal.LIMIT]);
            BoundedJournal.append(journal,previous,"first\n".getBytes(StandardCharsets.UTF_8));
            check(previous.length()==BoundedJournal.LIMIT&&new String(Files.readAllBytes(journal.toPath()),StandardCharsets.UTF_8).equals("first\n"),"Full journal dropped new event");
            BoundedJournal.append(journal,previous,new byte[BoundedJournal.LIMIT]);
            check(new String(Files.readAllBytes(previous.toPath()),StandardCharsets.UTF_8).equals("first\n")&&journal.length()==BoundedJournal.LIMIT,"Journal rotation did not retain latest prior data");
            boolean tooBig=false;try{BoundedJournal.append(journal,previous,new byte[BoundedJournal.LIMIT+1]);}catch(IOException expected){tooBig=true;}
            check(tooBig&&journal.length()==BoundedJournal.LIMIT,"Oversized event changed journal");
            File outside=new File(directory,"outside.jsonl");Files.createSymbolicLink(new File(directory,"link.jsonl").toPath(),outside.toPath());
            boolean linked=false;try{BoundedJournal.append(new File(directory,"link.jsonl"),previous,new byte[]{1});}catch(IOException expected){linked=true;}
            check(linked&&!outside.exists(),"Journal followed a link");
            Map<String,Object> checks=PhoneSelfCheck.run(context);
            for(String key:new String[]{"rustTransport","sharedOwnership","localDashboard","remoteTargetRejected"})check("pass".equals(checks.get(key)),"Phone self-check: "+key);
            String originRun="origin-"+UUID.randomUUID();
            LocalHttp.Response origin=StarlinkRuntime.get(context).router.handle(LocalHttp.parse(("GET /subarubridge-diag/basic?screen=1&run="+originRun+" HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII)),"selfcheck");
            check(origin.status==200,"Origin fixture failed");
            boolean sourceLabeled=false;
            for(String line:Files.readAllLines(new File(context.getFilesDir(),"subarubridge-diagnostics/events.jsonl").toPath(),StandardCharsets.UTF_8)) {
                JSONObject event=new JSONObject(line);if(originRun.equals(event.optString("run")))sourceLabeled|="selfcheck".equals(event.optString("source"));
            }
            check(sourceLabeled,"Phone check was not distinguished from car transport");
            android.app.Activity activity=instrumentation.startActivitySync(new android.content.Intent(context,DeveloperActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.runOnMainSync(()->{
                check(activity.findViewById(org.mlpi.pixelprobe.R.id.recorder_start)!=null,"Recorder screen missing start control");
                check(activity.findViewById(org.mlpi.pixelprobe.R.id.developer_enabled)!=null,"Developer mode missing");activity.finish();
            });
            // Render the actual packaged adaptive icon for visual review, not a mockup.
            android.graphics.drawable.Drawable icon=context.getPackageManager().getApplicationIcon(context.getPackageName());
            check(icon instanceof android.graphics.drawable.AdaptiveIconDrawable,"Adaptive launcher icon missing");
            if(android.os.Build.VERSION.SDK_INT>=33)check(((android.graphics.drawable.AdaptiveIconDrawable)icon).getMonochrome()!=null,"Themed launcher icon missing");
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(512,512,android.graphics.Bitmap.Config.ARGB_8888);
            icon.setBounds(0,0,512,512);icon.draw(new android.graphics.Canvas(bitmap));
            try(OutputStream out=new FileOutputStream(new File(context.getCacheDir(),"bridge-icon-preview.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
            result.putString("recorder","PASS: no-root recording, privacy, interruption, byte/retention limits, export, self-check and unified UI");
        } finally {
            File[] files=directory.listFiles();if(files!=null)for(File file:files)if(file.isFile()||Files.isSymbolicLink(file.toPath()))Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }
}
