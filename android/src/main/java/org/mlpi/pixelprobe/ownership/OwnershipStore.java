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

/** One private source of truth. No networking, telemetry, credentials or protocol reports. */
public final class OwnershipStore {
    // Leave room for both states in a bounded merge request to the shared engine.
    public static final int MAX_STATE = 1536 * 1024;
    public static final int MAX_BACKUP = MAX_STATE + 4096;
    private static final Object LOCK = new Object();
    private final AtomicFile file;
    private final Clock clock;
    private final boolean liveZone;

    public OwnershipStore(Context context) {
        this(new File(context.getNoBackupFilesDir(), "garage/garage-v1.json"), Clock.systemUTC(), true);
    }
    /** Allows isolated on-device persistence and clock tests without touching a user's garage. */
    public OwnershipStore(File path, Clock clock) { this(path, clock, false); }
    private OwnershipStore(File path, Clock clock, boolean liveZone) { this.file = new AtomicFile(path); this.clock = clock; this.liveZone = liveZone; }

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
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JSONTokener reader = new JSONTokener(text);
            Object object = reader.nextValue();
            if (!(object instanceof JSONObject) || reader.nextClean() != 0) throw new IOException("Expected one garage JSON document");
            return (JSONObject) object;
        } catch (org.json.JSONException | CharacterCodingException error) {
            throw new IOException("Garage file is unreadable; the original was preserved", error);
        }
    }
    private JSONObject load() throws Exception {
        File base = file.getBaseFile();
        if (!base.exists() && !new File(base.getPath() + ".bak").exists()) return empty();
        try (InputStream input = file.openRead()) { return parse(readBounded(input, MAX_STATE)); }
    }
    private JSONObject evaluate(JSONObject state, JSONObject command) throws Exception {
        long now = clock.millis();
        LocalDate today = Instant.ofEpochMilli(now).atZone(liveZone ? ZoneId.systemDefault() : clock.getZone()).toLocalDate();
        return OwnershipCore.execute(new JSONObject().put("state", state).put("today", today.toString())
                .put("nowMs", now).put("command", command));
    }
    private void persist(JSONObject state) throws Exception {
        byte[] bytes = state.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STATE) throw new IOException("Garage storage limit reached; export a backup before adding more records");
        FileOutputStream output = null;
        try {
            output = file.startWrite(); output.write(bytes); output.flush(); output.getFD().sync();
            file.finishWrite(output); output = null;
            try (InputStream input = file.openRead()) {
                if (!Arrays.equals(bytes, readBounded(input, MAX_STATE))) throw new IOException("Garage could not be saved; check free storage");
            }
        } finally { if (output != null) file.failWrite(output); }
    }
    public JSONObject execute(JSONObject command) throws Exception {
        synchronized (LOCK) {
            JSONObject result = evaluate(load(), command);
            if (result.getBoolean("changed")) persist(result.getJSONObject("state"));
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
                || !envelope.getString("format").equals("org.subarubridge.garage") || envelope.getInt("schemaVersion") != 1)
            throw new IOException("Unsupported garage backup format; local records were preserved");
        return execute(new JSONObject().put("type", "mergeBackup").put("backup", envelope.getJSONObject("garage")));
    }
}
