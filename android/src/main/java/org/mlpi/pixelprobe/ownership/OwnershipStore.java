package org.mlpi.pixelprobe.ownership;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import org.json.JSONArray;

/** One private source of truth. No networking, telemetry, credentials or protocol reports. */
public final class OwnershipStore {
    // Leave room for both states in a bounded merge request to the shared engine.
    public static final int MAX_STATE = 1536 * 1024;
    public static final int MAX_BACKUP = MAX_STATE + 4096;
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    private final Clock clock;
    private final boolean liveZone;
    private final File blobs;
    public static final int MAX_MANIFEST = MAX_STATE + 512 * 1024;
    private static final String STORAGE = "org.subarubridge.garage.storage";
    private static final class Data {
        JSONObject garage; JSONArray attachments; long generation; boolean legacy;
        Data(JSONObject garage, JSONArray attachments, long generation, boolean legacy) { this.garage=garage; this.attachments=attachments; this.generation=generation; this.legacy=legacy; }
    }

    public OwnershipStore(Context context) {
        this(new File(context.getNoBackupFilesDir(), "garage/garage-v1.json"), Clock.systemUTC(), true);
    }
    /** Allows isolated on-device persistence and clock tests without touching a user's garage. */
    public OwnershipStore(File path, Clock clock) { this(path, clock, false); }
    private OwnershipStore(File path, Clock clock, boolean liveZone) { this.file = new AtomicFile(path); this.clock = clock; this.liveZone = liveZone; this.blobs = new File(path.getParentFile(), path.getName() + ".attachments"); }

    private static JSONObject empty() throws Exception {
        return new JSONObject("{\"schemaVersion\":1,\"revision\":0,\"vehicles\":[],\"readings\":[],\"services\":[],\"fuel\":[],\"expenses\":[],\"trips\":[],\"overrides\":[]}");
    }
    public static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] block = new byte[8192]; int count;
        while ((count = input.read(block)) != -1) {
            if (bytes.size() + count > maximum) throw new IOException("Garage file exceeds the supported size");
            bytes.write(block, 0, count);
        }
        return bytes.toByteArray();
    }
    public static JSONObject parse(byte[] bytes) throws IOException {
        if (!OwnershipCore.validDocument(bytes)) throw new IOException("Garage file is not valid UTF-8 JSON or is nested too deeply; the original was preserved");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\ufeff")) text = text.substring(1);
            JSONTokener reader = new JSONTokener(text);
            Object object = reader.nextValue();
            if (!(object instanceof JSONObject) || reader.nextClean() != 0) throw new IOException("Expected one garage JSON document");
            return (JSONObject) object;
        } catch (org.json.JSONException | CharacterCodingException error) {
            throw new IOException("Garage file is unreadable; the original was preserved", error);
        }
    }
    private Data load() throws Exception {
        File base = file.getBaseFile();
        if (!base.exists() && !new File(base.getPath() + ".bak").exists()) return new Data(empty(), new JSONArray(), 0, false);
        JSONObject object;
        try (InputStream input = file.openRead()) { object = parse(readBounded(input, MAX_MANIFEST)); }
        if (!object.has("format")) return new Data(object, new JSONArray(), 0, true);
        requireKeys(object, "format", "storageVersion", "generation", "garage", "attachments");
        if (!STORAGE.equals(object.getString("format")) || integer(object,"storageVersion") != 2) throw new IOException("Unsupported garage storage version; the file was preserved");
        long generation=integer(object,"generation"); if (generation<0 || generation>9_007_199_254_740_991L) throw new IOException("Invalid garage generation");
        Data data=new Data(object.getJSONObject("garage"), object.getJSONArray("attachments"), generation, false);
        validateAttachments(data.garage, data.attachments); return data;
    }
    static long integer(JSONObject object,String key) throws Exception {
        Object raw=object.get(key);if(!(raw instanceof Number)||!raw.toString().matches("0|[1-9][0-9]{0,15}"))throw new IOException("Expected a nonnegative integer for "+key);
        long value=Long.parseLong(raw.toString());if(value>9_007_199_254_740_991L)throw new IOException("Integer exceeds portable limit");return value;
    }
    static void requireKeys(JSONObject object, String... expected) throws Exception {
        HashSet<String> keys = new HashSet<>(); object.keys().forEachRemaining(keys::add);
        if (!keys.equals(new HashSet<>(Arrays.asList(expected)))) throw new IOException("Unsupported fields in garage data; the original was preserved");
    }
    private JSONObject evaluate(JSONObject state, JSONObject command) throws Exception {
        long now = clock.millis();
        LocalDate today = Instant.ofEpochMilli(now).atZone(liveZone ? ZoneId.systemDefault() : clock.getZone()).toLocalDate();
        return OwnershipCore.execute(new JSONObject().put("state", state).put("today", today.toString())
                .put("nowMs", now).put("command", command));
    }
    private void persist(Data data) throws Exception {
        if (data.garage.toString().getBytes(StandardCharsets.UTF_8).length > MAX_STATE) throw new IOException("Garage storage limit reached; export a backup before adding more records");
        validateAttachments(data.garage, data.attachments);
        if (data.generation>=9_007_199_254_740_991L) throw new IOException("Garage generation limit reached");
        byte[] bytes = new JSONObject().put("format", STORAGE).put("storageVersion", 2).put("generation", data.generation+1)
                .put("garage", data.garage).put("attachments", data.attachments).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MANIFEST) throw new IOException("Garage attachment metadata limit reached");
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(bytes); output.flush(); output.getFD().sync();
            file.finishWrite(output); output = null;
            pruneOldBlobs(data.attachments);
            try (InputStream input = file.openRead()) {
                if (!Arrays.equals(bytes, readBounded(input, MAX_MANIFEST))) throw new IOException("Garage could not be saved; check free storage");
            }
        } finally { if (output != null) file.failWrite(output); }
    }
    public JSONObject execute(JSONObject command) throws Exception {
        synchronized (LOCK) {
            Data data=load(); JSONObject result = evaluate(data.garage, command);
            if (result.getBoolean("changed") || data.legacy) { data.garage=result.getJSONObject("state"); persist(data); }
            result.put("attachmentCount", data.attachments.length());
            return result;
        }
    }
    public JSONObject view() throws Exception { return execute(new JSONObject().put("type", "view")); }
    public byte[] backup() throws Exception {
        synchronized (LOCK) {
            return new JSONObject().put("format", "org.subarubridge.garage").put("schemaVersion", 1)
                    .put("exportedAt", Instant.now(clock).toString()).put("garage", view().getJSONObject("state"))
                    .toString().getBytes(StandardCharsets.UTF_8);
        }
    }
    public JSONObject merge(byte[] bytes) throws Exception {
        if (bytes.length > MAX_BACKUP) throw new IOException("Garage backup exceeds the supported size");
        JSONObject envelope = parse(bytes);
        HashSet<String> keys = new HashSet<>(); envelope.keys().forEachRemaining(keys::add);
        if (!keys.equals(new HashSet<>(Arrays.asList("format", "schemaVersion", "exportedAt", "garage")))
                || !envelope.getString("format").equals("org.subarubridge.garage") || integer(envelope,"schemaVersion") != 1)
            throw new IOException("Unsupported garage backup format; local records were preserved");
        return execute(new JSONObject().put("type", "mergeBackup").put("backup", envelope.getJSONObject("garage")));
    }
    public static boolean recordExists(JSONObject garage, String kind, String id) throws Exception {
        if (!Arrays.asList("vehicles", "services", "fuel", "expenses", "trips").contains(kind)) return false;
        JSONArray records=garage.optJSONArray(kind); if (records==null) return false;
        for (int i=0;i<records.length();i++) if (id.equals(records.getJSONObject(i).getString("id"))) return true;
        return false;
    }
    public static void validateAttachments(JSONObject garage, JSONArray attachments) throws Exception {
        if (attachments.length()>OwnershipFiles.MAX_COUNT) throw new IOException("At most 256 receipt attachments are supported");
        HashSet<String> ids=new HashSet<>(); long total=0;
        for (int i=0;i<attachments.length();i++) {
            JSONObject a=attachments.getJSONObject(i); requireKeys(a,"id","kind","recordId","name","mime","size","sha256","createdMs");
            if (!a.getString("id").matches("[a-zA-Z0-9_-]{1,80}") || !ids.add(a.getString("id")) || !a.getString("sha256").matches("[a-f0-9]{64}")
                    || a.getString("name").isBlank() || a.getString("name").length()>160 || a.getString("name").matches("(?s).*[\\p{Cntrl}].*")
                    || !Arrays.asList("application/pdf","image/jpeg","image/png","image/webp").contains(a.getString("mime"))
                    || integer(a,"size")<=0 || integer(a,"size")>OwnershipFiles.MAX_FILE || integer(a,"createdMs")>9_007_199_254_740_991L
                    || !recordExists(garage,a.getString("kind"),a.getString("recordId"))) throw new IOException("Invalid or orphaned receipt attachment");
            total+=a.getLong("size"); if (total>OwnershipFiles.MAX_TOTAL) throw new IOException("Receipts exceed the 256 MiB garage limit");
        }
    }
    public JSONArray attachments(String kind, String recordId) throws Exception {
        synchronized (LOCK) { Data data=load(); evaluate(data.garage,new JSONObject().put("type","view")); JSONArray list=new JSONArray();
            for (int i=0;i<data.attachments.length();i++) {JSONObject a=data.attachments.getJSONObject(i); if (kind==null || kind.equals(a.getString("kind")) && recordId.equals(a.getString("recordId"))) list.put(a); } return list;
        }
    }
    public JSONObject addAttachment(String kind, String recordId, String name, InputStream input) throws Exception {
        if (!blobs.isDirectory() && !blobs.mkdirs()) throw new IOException("Private receipt storage is unavailable");
        File staging=File.createTempFile("receipt-",".tmp",blobs);
        try {
            try (OutputStream out=new FileOutputStream(staging)) { OwnershipFiles.copy(input,out,OwnershipFiles.MAX_FILE); }
            String mime=OwnershipFiles.inspect(staging), hash=OwnershipFiles.hash(staging);
            String clean=name==null ? "Receipt" : name.replaceAll("[\\p{Cntrl}/\\\\]","_").trim(); if (clean.isEmpty()) clean="Receipt"; if (clean.length()>160) clean=clean.substring(0,160);
            synchronized (LOCK) {
                Data data=load(); evaluate(data.garage,new JSONObject().put("type","view"));
                JSONObject a=new JSONObject().put("id",UUID.randomUUID().toString()).put("kind",kind).put("recordId",recordId).put("name",clean).put("mime",mime).put("size",staging.length()).put("sha256",hash).put("createdMs",clock.millis());
                data.attachments.put(a); validateAttachments(data.garage,data.attachments);
                publish(staging,hash); persist(data); return a;
            }
        } finally { staging.delete(); }
    }
    private void publish(File source, String hash) throws Exception {
        if (!blobs.isDirectory() && !blobs.mkdirs()) throw new IOException("Private receipt storage is unavailable");
        File target=OwnershipFiles.blob(blobs,hash);
        if (target.exists()) { if (!hash.equals(OwnershipFiles.hash(target))) throw new IOException("An existing receipt failed its integrity check"); return; }
        File temp=File.createTempFile("publish-",".tmp",blobs);
        try { try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(temp)){OwnershipFiles.copy(in,out,OwnershipFiles.MAX_FILE);out.getFD().sync();}
            if (!temp.renameTo(target)) throw new IOException("Receipt could not be saved");
        } finally {temp.delete();}
    }
    public File attachmentFile(JSONObject attachment) throws Exception {
        File source=OwnershipFiles.blob(blobs,attachment.getString("sha256"));
        if (!source.isFile() || source.length()!=attachment.getLong("size") || !OwnershipFiles.hash(source).equals(attachment.getString("sha256"))) throw new IOException("Receipt is missing or damaged; restore a complete backup");
        return source;
    }
    private void pruneOldBlobs(JSONArray attachments) {
        try {HashSet<String> used=new HashSet<>();for(int i=0;i<attachments.length();i++)used.add(attachments.getJSONObject(i).getString("sha256"));
            File[] files=blobs.listFiles();if(files!=null)for(File f:files){
                if(f.isFile()&&f.getName().matches("[a-f0-9]{64}")&&!used.contains(f.getName())&&f.lastModified()<System.currentTimeMillis()-86_400_000L)f.delete();
            }
        }catch(Exception ignored){/* Cleanup failure does not invalidate a committed garage. */}
    }
    public void removeAttachment(String id) throws Exception {
        synchronized (LOCK) { Data data=load(); JSONArray keep=new JSONArray(); String hash=null;
            for(int i=0;i<data.attachments.length();i++){JSONObject a=data.attachments.getJSONObject(i);if(id.equals(a.getString("id"))) hash=a.getString("sha256");else keep.put(a);}
            if(hash==null) throw new IOException("Receipt no longer exists"); data.attachments=keep; persist(data);
            boolean used=false;for(int i=0;i<keep.length();i++) if(hash.equals(keep.getJSONObject(i).getString("sha256"))) used=true;
            if(!used) OwnershipFiles.blob(blobs,hash).delete();
        }
    }
    public void archive(File target) throws Exception {
        synchronized (LOCK) { Data data=load(); data.garage=evaluate(data.garage,new JSONObject().put("type","view")).getJSONObject("state");
            OwnershipBackup.write(target,data.garage,data.attachments,this::attachmentFile,clock.instant().toString());
        }
    }
    public JSONObject preview(OwnershipBackup.Plan plan) throws Exception {
        synchronized (LOCK) {
            Data data=load(); JSONObject review=evaluate(data.garage,new JSONObject().put("type","previewImport").put("backup",plan.garage)).getJSONObject("importReview");
            JSONArray conflicts=review.getJSONArray("conflicts"); int added=0,matched=0;
            for(int i=0;i<plan.attachments.length();i++){JSONObject a=plan.attachments.getJSONObject(i),old=findAttachment(data.attachments,a.getString("id"));
                if(old==null)added++;else if(sameAttachment(old,a))matched++;else conflicts.put(new JSONObject().put("key","attachments/"+a.getString("id")).put("collection","attachments").put("local",old).put("incoming",a).put("canReplace",false).put("reason","Receipt IDs retain their original record and file. Keep the local receipt."));
            }
            return review.put("storageGeneration",data.generation).put("newAttachments",added).put("matchingAttachments",matched);
        }
    }
    private static boolean sameAttachment(JSONObject a,JSONObject b) throws Exception {
        for(String key:new String[]{"id","kind","recordId","name","mime","size","sha256","createdMs"})if(!a.get(key).toString().equals(b.get(key).toString()))return false;return true;
    }
    private static JSONObject findAttachment(JSONArray list,String id) throws Exception {for(int i=0;i<list.length();i++)if(id.equals(list.getJSONObject(i).getString("id")))return list.getJSONObject(i);return null;}
    public JSONObject restore(OwnershipBackup.Plan plan, JSONObject review, JSONArray choices) throws Exception {
        synchronized (LOCK) {
            Data data=load(); if(data.generation!=review.getLong("storageGeneration")) throw new IOException("Your garage changed after review. Review this backup again.");
            JSONArray coreChoices=new JSONArray(); HashSet<String> attachmentChoices=new HashSet<>();
            for(int i=0;i<choices.length();i++){JSONObject c=choices.getJSONObject(i);if(c.getString("key").startsWith("attachments/")){if(c.getBoolean("useIncoming") || !attachmentChoices.add(c.getString("key")))throw new IOException("Keep existing receipts when IDs conflict");}else coreChoices.put(c);}
            for(int i=0;i<review.getJSONArray("conflicts").length();i++){String key=review.getJSONArray("conflicts").getJSONObject(i).getString("key");if(key.startsWith("attachments/") && !attachmentChoices.remove(key))throw new IOException("Review every receipt conflict");}
            if(!attachmentChoices.isEmpty())throw new IOException("Unknown receipt resolution");
            JSONObject result=evaluate(data.garage,new JSONObject().put("type","applyImport").put("backup",plan.garage).put("expectedRevision",review.getLong("revision")).put("resolutions",coreChoices));
            boolean added=false; for(int i=0;i<plan.attachments.length();i++){JSONObject a=plan.attachments.getJSONObject(i);if(findAttachment(data.attachments,a.getString("id"))==null){data.attachments.put(a);added=true;}}
            data.garage=result.getJSONObject("state"); validateAttachments(data.garage,data.attachments);
            for(int i=0;i<plan.attachments.length();i++){JSONObject a=plan.attachments.getJSONObject(i);File source=plan.blob(a); if(!OwnershipFiles.hash(source).equals(a.getString("sha256")))throw new IOException("Backup receipt changed after review");publish(source,a.getString("sha256"));}
            if(result.getBoolean("changed") || added || data.legacy)persist(data);return result;
        }
    }
}
