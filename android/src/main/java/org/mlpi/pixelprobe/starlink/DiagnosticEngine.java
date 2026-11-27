package org.mlpi.pixelprobe.starlink;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Transport-independent test state; no vehicle commands or credential fields. */
public final class DiagnosticEngine {
    public static final String BUILD = "0.56-dev";
    public interface Journal { void append(Map<String, Object> event) throws IOException; }
    public static final class Failure extends Exception {
        public final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList("run", "seq", "screen", "action", "ok"));
    private static final Set<String> ACTIONS = new HashSet<String>(Arrays.asList("page", "touch", "image", "canvas", "timer", "data", "summary", "bootstrap", "script_error"));
    private static final class Receipt {
        final String signature;
        final long eventId;
        Receipt(String signature, long eventId) { this.signature = signature; this.eventId = eventId; }
    }
    private static final class Session {
        final LinkedHashMap<Integer, Receipt> receipts = new LinkedHashMap<Integer, Receipt>();
        final Set<Integer> screens = new HashSet<Integer>();
        int touches, dataChecks;
        boolean imageOk, canvasOk, timerOk, summarySaved, bootstrapSeen, scriptErrorSeen;
    }
    private final LinkedHashMap<String, Session> sessions = new LinkedHashMap<String, Session>(8, .75f, true);
    private final Journal journal;
    private long eventId;

    public DiagnosticEngine(Journal journal) { this.journal = journal; }
    public static boolean validRun(String run) { return run != null && run.matches("[a-z0-9-]{8,48}"); }
    public synchronized Map<String, Object> accept(Map<String, String> form, long wallMs, long uptimeMs) throws Failure {
        if (!form.keySet().equals(KEYS)) throw new Failure(400, "Unexpected event fields");
        String run = form.get("run"), action = form.get("action"), okText = form.get("ok");
        if (!validRun(run) || !ACTIONS.contains(action) || !("1".equals(okText) || "0".equals(okText)))
            throw new Failure(400, "Invalid event");
        int seq, screen;
        try {
            if (!form.get("seq").matches("[1-9][0-9]{0,3}") || !form.get("screen").matches("[1-4]")) throw new NumberFormatException();
            seq = Integer.parseInt(form.get("seq")); screen = Integer.parseInt(form.get("screen"));
        } catch (RuntimeException error) { throw new Failure(400, "Invalid sequence or screen"); }
        if (("touch".equals(action) && screen != 2) || ("data".equals(action) && screen != 3)
                || ("summary".equals(action) && screen != 4)
                || (("image".equals(action) || "canvas".equals(action) || "timer".equals(action)) && screen != 1))
            throw new Failure(400, "Event does not match screen");
        if (!("image".equals(action) || "canvas".equals(action)) && !"1".equals(okText))
            throw new Failure(400, "Invalid observation state");
        String signature = screen + ":" + action + ":" + okText;
        Session session = sessions.get(run);
        if (session != null && session.receipts.containsKey(seq)) {
            Receipt prior = session.receipts.get(seq);
            if (!prior.signature.equals(signature)) throw new Failure(409, "Sequence already used");
            return receipt(run, seq, prior.eventId, true, session);
        }
        if (session != null && session.receipts.size() >= 128) throw new Failure(429, "Run event limit reached");
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("schema", 1); event.put("build", BUILD); event.put("run", run); event.put("seq", seq);
        event.put("screen", screen); event.put("action", action); event.put("ok", "1".equals(okText));
        event.put("eventId", eventId + 1); event.put("serverTimeMs", wallMs); event.put("uptimeMs", uptimeMs);
        try { journal.append(event); }
        catch (IOException error) { throw new Failure(507, "Diagnostic journal unavailable"); }
        eventId++;
        if (session == null) {
            session = new Session();
            if (sessions.size() == 8) sessions.remove(sessions.keySet().iterator().next());
            sessions.put(run, session);
        }
        session.receipts.put(seq, new Receipt(signature, eventId));
        if ("page".equals(action)) session.screens.add(screen);
        if ("touch".equals(action)) session.touches++;
        if ("data".equals(action)) session.dataChecks++;
        if ("image".equals(action)) session.imageOk = "1".equals(okText);
        if ("canvas".equals(action)) session.canvasOk = "1".equals(okText);
        if ("timer".equals(action)) session.timerOk = true;
        if ("summary".equals(action)) session.summarySaved = true;
        if ("bootstrap".equals(action)) session.bootstrapSeen = true;
        if ("script_error".equals(action)) session.scriptErrorSeen = true;
        return receipt(run, seq, eventId, false, session);
    }
    private Map<String, Object> receipt(String run, int seq, long id, boolean duplicate, Session session) {
        Map<String, Object> result = snapshot(run);
        result.put("ackSeq", seq); result.put("eventId", id); result.put("duplicate", duplicate);
        return result;
    }
    public synchronized Map<String, Object> snapshot(String run) {
        Session session = sessions.get(run);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schema", 1); result.put("build", BUILD); result.put("run", run);
        result.put("events", session == null ? 0 : session.receipts.size());
        result.put("touches", session == null ? 0 : session.touches);
        result.put("dataChecks", session == null ? 0 : session.dataChecks);
        result.put("screensSeen", session == null ? 0 : session.screens.size());
        result.put("imageOk", session != null && session.imageOk);
        result.put("canvasOk", session != null && session.canvasOk);
        result.put("timerOk", session != null && session.timerOk);
        result.put("summarySaved", session != null && session.summarySaved);
        result.put("bootstrapSeen", session != null && session.bootstrapSeen);
        result.put("scriptErrorSeen", session != null && session.scriptErrorSeen);
        int next = 1;
        if (session != null) for (Integer seq : session.receipts.keySet()) next = Math.max(next, seq + 1);
        result.put("nextSeq", next);
        return result;
    }
}
