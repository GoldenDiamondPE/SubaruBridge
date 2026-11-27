package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Device ABI and JNI lifetime checks; protocol cases also run as native Rust tests. */
public final class BridgeCoreSmoke extends Instrumentation {
    private boolean apps;
    @Override public void onCreate(Bundle args){super.onCreate(args);apps=args!=null && "true".equals(args.getString("apps"));start();}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onStart() {
        Bundle result=new Bundle();int code=run(getTargetContext(),result);
        if(code==Activity.RESULT_OK && apps){try{AppIntegrationChecks.run(getTargetContext(),result);}catch(Throwable error){result.putString("apps","FAIL: "+error);code=Activity.RESULT_CANCELED;}}
        if(code==Activity.RESULT_OK) {
            try {
                android.content.Intent intent=new android.content.Intent(getTargetContext(),StarlinkActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                Activity screen=startActivitySync(intent);
                runOnMainSync(()->{
                    check(screen.findViewById(org.mlpi.pixelprobe.R.id.phone_home)!=null,"Cold launch missing navigation");
                    screen.finish();
                });
                result.putString("ui","PASS: cold Activity launch and navigation");
            }catch(Throwable error){result.putString("ui","FAIL: "+error);code=Activity.RESULT_CANCELED;}
        }
        finish(code,result);
    }
    public static int run(android.content.Context context,Bundle result) {
        int code=Activity.RESULT_CANCELED;
        try {
            check(StartupPolicy.automatic(StartupPolicy.Need.READY,true,false,false,false,false)==StartupPolicy.Action.START,"Open should start a ready connection");
            check(StartupPolicy.automatic(StartupPolicy.Need.READY,true,false,false,false,true)==StartupPolicy.Action.NONE,"Existing listener should not duplicate");
            check(StartupPolicy.automatic(StartupPolicy.Need.READY,false,false,false,false,false)==StartupPolicy.Action.NONE,"Automatic-off ignored");
            check(StartupPolicy.automatic(StartupPolicy.Need.READY,true,true,false,false,false)==StartupPolicy.Action.NONE,"Manual pause ignored");
            check(StartupPolicy.automatic(StartupPolicy.Need.NEARBY_PERMISSION,true,false,false,false,false)==StartupPolicy.Action.ASK_PERMISSION,"First launch needs permission");
            check(StartupPolicy.automatic(StartupPolicy.Need.NEARBY_PERMISSION,true,false,true,false,false)==StartupPolicy.Action.NONE,"Denied permission prompted repeatedly");
            check(StartupPolicy.automatic(StartupPolicy.Need.BLUETOOTH_OFF,true,false,false,false,false)==StartupPolicy.Action.ASK_BLUETOOTH,"Bluetooth setup missing");
            check(StartupPolicy.automatic(StartupPolicy.Need.BLUETOOTH_OFF,true,false,true,false,false)==StartupPolicy.Action.NONE,"Dismissed Bluetooth prompt repeated");
            check(StartupPolicy.automatic(StartupPolicy.Need.READY,true,false,false,true,false)==StartupPolicy.Action.NONE,"Setup in progress duplicated");
            for (StartupPolicy.Need unavailable : new StartupPolicy.Need[]{StartupPolicy.Need.NO_BLUETOOTH,StartupPolicy.Need.CORE_UNAVAILABLE})
                check(StartupPolicy.automatic(unavailable,true,false,false,false,false)==StartupPolicy.Action.NONE,"Unavailable device prompted");
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
            String marker="test-private-bootstrap-value";
            String body="pid="+marker;
            check(router.handle(("POST "+StarlinkRouter.ENTRY+" HTTP/1.0\r\nContent-Length: "+body.length()+"\r\n\r\n"+body).getBytes(StandardCharsets.US_ASCII)).status==200,"Bootstrap failed");
            java.io.File log=new java.io.File(context.getFilesDir(),"subarubridge-diagnostics/transport-private.jsonl");
            String journal=new String(java.nio.file.Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8);
            check(!journal.contains(marker)&&journal.contains("\"route\":\"launcher\""),"Bootstrap body leaked or route metadata missing");
            result.putString("core","PASS: startup policy, 64 JNI lifetimes, HTTP validation and private metadata");code=Activity.RESULT_OK;
        }catch(Throwable error){result.putString("core","FAIL: "+error);}
        return code;
    }
}
