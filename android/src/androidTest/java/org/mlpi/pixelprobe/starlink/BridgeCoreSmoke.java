package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Device ABI and JNI lifetime checks; protocol cases also run as native Rust tests. */
public final class BridgeCoreSmoke extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onStart() {
        Bundle result=new Bundle();finish(run(getTargetContext(),result),result);
    }
    public static int run(android.content.Context context,Bundle result) {
        int code=Activity.RESULT_CANCELED;
        try {
            for(int i=0;i<64;i++) {
                RustCore core=new RustCore(i+1);
                try {
                    check(core.feed(new byte[]{(byte)192,1,2,3,(byte)192},0).wire.length==0,"Invalid IP accepted");
                    check(core.tick(1000).requests.isEmpty(),"Unexpected request");
                }finally{core.close();core.close();}
                try{core.tick(1001);throw new AssertionError("Closed handle accepted");}catch(IOException expected){}
            }
            String[] bad={"GET / HTTP/1.1\r\nContent-Length: 0\r\nContent-Length: 0\r\n\r\n",
                    "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", "GET / HTTP/1.1\r\nHost : no\r\n\r\n"};
            for(String request:bad){try{LocalHttp.parse(request.getBytes(StandardCharsets.US_ASCII));throw new AssertionError("Ambiguous HTTP accepted");}catch(LocalHttp.Error expected){check(expected.status==400,"Wrong rejection");}}
            check(LocalHttp.parse("GET / HTTP/1.1\r\nConnection: keep-alive, close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)).close,"Close token ignored");
            StarlinkRouter router=StarlinkRuntime.get(context).router;
            check(router.handle("GET /version HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)).status==200,"Local router unavailable");
            check(router.handle("GET http://example.invalid/ HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)).status==403,"Remote target accepted");
            check(router.handle("GET /subarubridge-diag/%0aInjected HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII)).status==400,"Control character in path accepted");
            result.putString("core","PASS: 64 JNI lifetimes, malformed HTTP rejection and local-only routes");code=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("core","FAIL: "+error);}
        return code;
    }
}
