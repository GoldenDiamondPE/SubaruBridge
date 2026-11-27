package org.mlpi.pixelprobe.ownership;

import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** The same versioned, deterministic ownership API is available to Swift through C. */
public final class OwnershipCore {
    public static final int MAX_INPUT = 4 * 1024 * 1024;
    static { System.loadLibrary("subarubridge_core"); }
    private OwnershipCore() {}
    private static native byte[] nativeExecute(byte[] request);

    public static JSONObject execute(JSONObject request) throws IOException {
        byte[] input = request.toString().getBytes(StandardCharsets.UTF_8);
        if (input.length > MAX_INPUT) throw new IOException("Garage request exceeds the supported size");
        byte[] output = nativeExecute(input);
        if (output == null || output.length == 0) throw new IOException("Ownership engine unavailable");
        try {
            JSONObject response = new JSONObject(new String(output, StandardCharsets.UTF_8));
            if (!response.getBoolean("ok")) throw new IOException(response.optString("error", "Garage validation failed"));
            return response;
        } catch (org.json.JSONException error) {
            throw new IOException("Ownership engine returned an unreadable result", error);
        }
    }
}
