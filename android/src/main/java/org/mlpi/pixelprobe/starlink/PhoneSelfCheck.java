package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import org.json.JSONObject;
import org.mlpi.pixelprobe.ownership.OwnershipCore;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only bench checks available on the phone, with no physical-car claims. */
final class PhoneSelfCheck {
    static Map<String,Object> run(Context context) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("scope","Phone checks only. Car display, touch, audio and reconnect still require the parked Subaru.");
        try(RustCore core=new RustCore(101)) {core.feed(new byte[]{(byte)0xc0},0);core.tick(1);result.put("rustTransport","pass");}
        catch(IOException|LinkageError failure){result.put("rustTransport","failed");}
        try {
            JSONObject empty=new JSONObject("{\"schemaVersion\":1,\"revision\":0,\"vehicles\":[],\"readings\":[],\"services\":[],\"fuel\":[],\"expenses\":[],\"trips\":[],\"overrides\":[]}");
            JSONObject request=new JSONObject().put("state",empty).put("today",java.time.LocalDate.now().toString())
                    .put("nowMs",System.currentTimeMillis()).put("command",new JSONObject().put("type","view"));
            OwnershipCore.execute(request);result.put("sharedOwnership","pass");
        }catch(Exception|LinkageError failure){result.put("sharedOwnership","failed");}
        try {
            StarlinkRuntime runtime=StarlinkRuntime.get(context);runtime.startPreview();
            for(String route:new String[]{"/version",StarlinkRouter.ENTRY,"/subarubridge-diag/dashboard"}) {
                try(Socket socket=new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1",StarlinkRuntime.get(context).previewPort()),2000);socket.setSoTimeout(3000);
                    socket.getOutputStream().write(("GET "+route+" HTTP/1.0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
                    while((n=socket.getInputStream().read(block))!=-1){if(out.size()+n>128*1024)throw new IOException("Response limit");out.write(block,0,n);}
                    String response=new String(out.toByteArray(),StandardCharsets.UTF_8);
                    if(!response.startsWith("HTTP/1.1 200 "))throw new IOException("Local route failed");
                }
            }
            result.put("localDashboard","pass");
            LocalHttp.Response denied=runtime.router.handle(LocalHttp.parse("GET http://example.invalid/ HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)),"selfcheck");
            result.put("remoteTargetRejected",denied.status==403?"pass":"failed");
        }catch(IOException|RuntimeException failure){result.put("localDashboard","failed");}
        SessionRecorder.get(context).record("phone_self_check",result);return result;
    }
    private PhoneSelfCheck(){}
}
