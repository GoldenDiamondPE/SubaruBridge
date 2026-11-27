package org.mlpi.pixelprobe.starlink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** The same original Rust decoder can be used by any platform through its C API. */
final class AppleMediaCodec {
    static { System.loadLibrary("subarubridge_core"); }
    static JSONObject decode(byte[] input) throws Exception {
        if(input==null || input.length>512)throw new IOException("AMS input limit");
        byte[] output=nativeDecode(input);
        if(output==null || output.length>8192)throw new IOException("AMS decoder unavailable");
        JSONObject value=new JSONObject(new String(output,StandardCharsets.UTF_8));
        if(!value.optBoolean("ok"))throw new IOException("Invalid AMS update");
        return value;
    }
    private static native byte[] nativeDecode(byte[] input);
}
