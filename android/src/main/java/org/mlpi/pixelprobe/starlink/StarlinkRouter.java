package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Original local application routes. Independent of the discontinued APK/SDK. */
public final class StarlinkRouter {
    public static final String ENTRY = "/smartaccess/launcher/subarubridge-demo.html";
    private static final String PREFIX = "/subarubridge-diag/";
    private final Context context;
    private final DiagnosticEngine engine;
    private final DashboardApps apps;
    private final String instance = UUID.randomUUID().toString();
    private final ThreadLocal<String> requestSource=ThreadLocal.withInitial(()->"unknown");

    public StarlinkRouter(Context context) {
        this.context = context.getApplicationContext();
        apps = new DashboardApps(this.context);
        engine = new DiagnosticEngine(event -> {
            File directory = new File(this.context.getFilesDir(), "subarubridge-diagnostics");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Journal directory unavailable");
            File file = new File(directory,"events.jsonl");
            event.put("instance",instance);
            event.put("source",requestSource.get());
            byte[] bytes = (new JSONObject(event).toString()+"\n").getBytes(StandardCharsets.UTF_8);
            BoundedJournal.append(file,new File(directory,"events-previous-private.jsonl"),bytes);
            SessionRecorder.get(this.context).record("connection_check",event);
            Log.i("SubaruBridgeDiag","Saved "+event.get("action")+" screen="+event.get("screen")+" run="+event.get("run")+" seq="+event.get("seq"));
        });
    }
    public static Map<String,String> fields(String encoded) throws LocalHttp.Error {
        Map<String,String> result=new LinkedHashMap<>();
        if(encoded==null || encoded.isEmpty())return result;
        try {
            for(String item:encoded.split("&",-1)) {
                int eq=item.indexOf('='); if(eq<1)throw new IllegalArgumentException();
                String key=URLDecoder.decode(item.substring(0,eq),"UTF-8"), value=URLDecoder.decode(item.substring(eq+1),"UTF-8");
                if(result.put(key,value)!=null)throw new IllegalArgumentException();
            }
        }catch(IllegalArgumentException | UnsupportedEncodingException error){throw new LocalHttp.Error(400,"Malformed fields");}
        return result;
    }
    private Map<String,String> body(LocalHttp.Request request,int limit) throws LocalHttp.Error {
        if(request.body.length==0)throw new LocalHttp.Error(400,"Form required");
        if(request.body.length>limit)throw new LocalHttp.Error(413,"Form too large");
        String type=request.headers.getOrDefault("content-type", "");
        if(!type.split(";",2)[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded"))throw new LocalHttp.Error(400,"Form encoding required");
        return fields(new String(request.body,StandardCharsets.UTF_8));
    }
    private void method(LocalHttp.Request request,String... allowed) throws LocalHttp.Error {
        if(!Arrays.asList(allowed).contains(request.method))throw new LocalHttp.Error(405,"Method unsupported");
    }
    private String run(Map<String,String> query,boolean required) throws LocalHttp.Error {
        String run=query.get("run");
        if((required || run!=null) && !DiagnosticEngine.validRun(run))throw new LocalHttp.Error(400,"Invalid run ID");
        return run;
    }
    private Map<String,String> event(String run,int screen,String action) {
        Map<String,String> f=new LinkedHashMap<>();f.put("run",run);f.put("seq",""+engine.snapshot(run).get("nextSeq"));
        f.put("screen",""+screen);f.put("action",action);f.put("ok","1");return f;
    }
    private Map<String,Object> nativeStatus(String run) {
        Map<String,Object> state=engine.snapshot(run);
        BatteryManager battery=(BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
        int capacity=battery==null?-1:battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        state.put("batteryPercent",capacity>=0 && capacity<=100?capacity:-1);
        state.put("instance",instance);state.put("serverTimeMs",System.currentTimeMillis());state.put("uptimeMs",SystemClock.elapsedRealtime());
        return state;
    }
    private LocalHttp.Response json(int code,Map<String,Object> value,boolean close) {
        value.put("instance",instance);value.put("serverTimeMs",System.currentTimeMillis());value.put("uptimeMs",SystemClock.elapsedRealtime());
        return LocalHttp.text(code,"application/json; charset=utf-8",new JSONObject(value).toString(),close);
    }
    public LocalHttp.Response error(int code,String message,boolean close) {
        Map<String,Object> value=new LinkedHashMap<>();value.put("error",message);value.put("build",DiagnosticEngine.BUILD);return json(code,value,close);
    }
    private LocalHttp.Response html(String content,boolean close) {return LocalHttp.text(200,"text/html; charset=utf-8",content,close);}
    private byte[] asset(String file,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=context.getAssets().open("subarubridge-lab/"+file)) {
            byte[] block=new byte[2048];int n;while((n=in.read(block))!=-1){if(out.size()+n>max)throw new IOException("Asset limit");out.write(block,0,n);}
        }return out.toByteArray();
    }
    private synchronized LocalHttp.Response basic(LocalHttp.Request request,Map<String,String> query) throws Exception {
        String run;int screen;Map<String,Object> state;
        method(request,"GET","POST");
        if(request.method.equals("POST")) {
            Map<String,String> fields=body(request,1024);String action=fields.get("action");
            if(!Arrays.asList("touch","data","summary").contains(action))throw new LocalHttp.Error(400,"Invalid basic action");
            state=engine.accept(fields,System.currentTimeMillis(),SystemClock.elapsedRealtime());
            run=fields.get("run");screen=Integer.parseInt(fields.get("screen"));
        }else {
            run=run(query,false);if(run==null)run="basic-"+Long.toHexString(System.nanoTime());
            String step=query.getOrDefault("screen","1");if(!step.matches("[1-4]"))throw new LocalHttp.Error(400,"Invalid screen");
            screen=Integer.parseInt(step);state=engine.accept(event(run,screen,"page"),System.currentTimeMillis(),SystemClock.elapsedRealtime());
        }
        StringBuilder form=new StringBuilder();
        if(screen>1) {
            Map<String,String> f=event(run,screen,screen==2?"touch":screen==3?"data":"summary");
            form.append("<form method=\"post\" action=\"/subarubridge-diag/basic\">");
            for(Map.Entry<String,String> entry:f.entrySet())form.append(HtmlView.hidden(entry.getKey(),entry.getValue()));
            form.append("<button id=\"basic-submit\" class=\"primary-button\" type=\"submit\">")
                    .append(screen==2?"Send test touch":screen==3?"Read phone data":"Save result").append("</button></form>");
        }
        return html(new CarUi(context).basic(run,screen,state,screen==3?nativeStatus(run):null,form.toString()),request.close);
    }
    public LocalHttp.Response handle(LocalHttp.Request request) { return handle(request,"preview"); }
    LocalHttp.Response handle(LocalHttp.Request request,String source) {
        requestSource.set(source);
        if(request==null)return error(400,"Missing request",true);
        String loggedPath="invalid";
        LocalHttp.Response response;
        try {
            URI uri=new URI(request.target);
            if(uri.getFragment()!=null || uri.getUserInfo()!=null || request.target.startsWith("//"))throw new LocalHttp.Error(400,"Invalid target");
            if(uri.isAbsolute() && (!"http".equalsIgnoreCase(uri.getScheme()) || !"192.168.1.1".equals(uri.getHost()) || (uri.getPort()!=-1 && uri.getPort()!=80)))throw new LocalHttp.Error(403,"Local target required");
            String path=uri.getPath();
            if(path==null || !path.startsWith("/") || path.contains("..") || path.chars().anyMatch(c->c<32 || c==127))throw new LocalHttp.Error(400,"Invalid path");
            // Never log bootstrap body/query: a car may put identifying values there.
            String route=path.startsWith(PREFIX)?path.substring(PREFIX.length()):"";
            loggedPath=route.startsWith("apps/")?"dashboard-app":Arrays.asList("basic","dashboard","settings","developer","status","event","pattern.png").contains(route)?PREFIX+route:ENTRY.equals(path)?"launcher":path.equals("/kvs/launcher_url_vehicle/")?"launcher-lookup":path.equals("/timestamp/")?"clock":"other";
            Map<String,String> query=fields(uri.getRawQuery());
            if(path.equals(ENTRY) || path.equals("/")) {
                method(request,"GET","POST");response=html(new CarUi(context).home(null),request.close);
            }else if(path.equals("/kvs/launcher_url_vehicle/")) {
                method(request,"GET");response=LocalHttp.text(200,"text/plain; charset=utf-8","http://192.168.1.1"+ENTRY,request.close);
            }else if(path.equals("/timestamp/")) {
                method(request,"GET");response=LocalHttp.text(200,"text/plain; charset=utf-8",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US).format(new Date()),request.close);
            }else if(path.equals("/version") || path.equals("/version/")) {
                method(request,"GET");Map<String,Object> value=new LinkedHashMap<>();value.put("product","SubaruBridge");value.put("build",DiagnosticEngine.BUILD);value.put("transport","original-rust-rfcomm");response=json(200,value,request.close);
            }else if(path.equals(PREFIX+"basic"))response=basic(request,query);
            else if(path.equals(PREFIX+"apps/action")) {
                method(request,"POST");
                String origin=request.headers.get("origin");
                if(origin!=null && !origin.equals("http://192.168.1.1") && !origin.equals("http://127.0.0.1:"+StarlinkRuntime.PREVIEW_PORT))throw new LocalHttp.Error(403,"Local origin required");
                response=html(apps.actionPage(apps.perform(body(request,1024))),request.close);
            }else if(route.startsWith("apps/")) {method(request,"GET");response=html(apps.page(route.substring(5)),request.close);}
            else if(path.equals(PREFIX+"dashboard")) {method(request,"GET");response=html(new CarUi(context).home(run(query,false)),request.close);}
            else if(path.equals(PREFIX+"settings")) {
                method(request,"GET","POST");String run=run(query,false),notice="Choose a shared look for your phone and car.";CarUi ui=new CarUi(context);
                if(request.method.equals("POST")) {
                    Map<String,String> f=body(request,512);
                    if(f.size()!=2 || !ui.validTheme(f.get("theme")) || !CarUi.validLayout(f.get("layout")))throw new LocalHttp.Error(400,"Unknown appearance choice");
                    if(!ui.save(f.get("theme"),f.get("layout")))throw new LocalHttp.Error(507,"Appearance could not be saved");
                    ui=new CarUi(context);notice="Saved. Your phone and car now share this look.";
                }response=html(ui.settings(run,notice),request.close);
            }else if(path.equals(PREFIX+"developer")) {
                method(request,"GET");String run=run(query,false);Map<String,Object> value=new LinkedHashMap<>();value.put("build",DiagnosticEngine.BUILD);value.put("instance",instance);value.put("transport","Original Rust core; no legacy runtime");value.put("test",run==null?"Open a connection check to inspect its run":nativeStatus(run));
                response=html(new CarUi(context).developer(run,new JSONObject(value).toString(2)),request.close);
            }else if(path.equals(PREFIX+"status")) {method(request,"GET");response=json(200,nativeStatus(run(query,true)),request.close);}
            else if(path.equals(PREFIX+"event")) {method(request,"POST");response=json(200,engine.accept(body(request,1024),System.currentTimeMillis(),SystemClock.elapsedRealtime()),request.close);}
            else if(path.equals(PREFIX+"pattern.png")) {method(request,"GET");response=new LocalHttp.Response(200,"image/png",asset("pattern.png",8192),request.close);}
            else response=error(404,"Unknown local route",request.close);
        }catch(DiagnosticEngine.Failure e){response=error(e.status,e.getMessage(),request.close);}
        catch(LocalHttp.Error e){response=error(e.status,e.getMessage(),request.close);}
        catch(java.net.URISyntaxException e){response=error(400,"Invalid request target",true);}
        catch(Exception e){Log.w("SubaruBridgeHttp","Local handler failed: "+e.getClass().getSimpleName());response=error(500,"Local handler unavailable",true);}
        finally{requestSource.remove();}
        Log.i("SubaruBridgeHttp",request.method+" "+loggedPath+" status="+response.status+" bytes="+response.wire.length);
        LinkDiagnostics.http(context,source,request.method,loggedPath,response.status,response.wire.length);
        return response;
    }
    public LocalHttp.Response handle(byte[] raw) {
        try{return handle(LocalHttp.parse(raw),"bluetooth");}catch(LocalHttp.Error e){return error(e.status,e.getMessage(),true);}catch(IOException e){return error(400,"Invalid request",true);}
    }
    /** Only our bounded diagnostic data; never HCI, vendor files or identifiers. */
    public void exportReport(OutputStream destination) throws IOException {
        synchronized(engine) {
            synchronized(LinkDiagnostics.class) {
            try(ZipOutputStream zip=new ZipOutputStream(destination)) {
                Map<String,Object> metadata=new LinkedHashMap<>();
                metadata.put("product","SubaruBridge");metadata.put("build",DiagnosticEngine.BUILD);
                metadata.put("serverTimeMs",System.currentTimeMillis());metadata.put("uptimeMs",SystemClock.elapsedRealtime());
                metadata.put("connection",StarlinkService.state());metadata.put("androidApi",android.os.Build.VERSION.SDK_INT);
                metadata.put("transport",LinkDiagnostics.snapshot());
                metadata.put("iPhoneMedia",AppleMediaClient.get(context).diagnostics());
                metadata.put("recordings",SessionRecorder.get(context).list().size());
                metadata.put("recordingScope","Opt-in app metadata and user observations; 12 sessions, 1 MiB each, 10 minutes each");
                metadata.put("scope","Original app events only. No raw Bluetooth or USB capture included.");
                zip.putNextEntry(new ZipEntry("report.json"));zip.write(new JSONObject(metadata).toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
                SessionRecorder.get(context).export(zip);
                for(String name:new String[]{"events.jsonl","events-previous-private.jsonl","transport-private.jsonl","transport-previous-private.jsonl"}) {
                File journal=new File(context.getFilesDir(),"subarubridge-diagnostics/"+name);
                if(journal.isFile()) {
                    if(journal.length()>1024*1024)throw new IOException("Journal size limit");
                    zip.putNextEntry(new ZipEntry(name.equals("events.jsonl")?"events-private.jsonl":name));
                    try(InputStream in=new FileInputStream(journal)) {byte[] bytes=new byte[8192];int n,total=0;
                        while((n=in.read(bytes))!=-1){total+=n;if(total>1024*1024)throw new IOException("Journal size limit");zip.write(bytes,0,n);}}
                    zip.closeEntry();
                }
                }
            }
            }
        }
    }
}
