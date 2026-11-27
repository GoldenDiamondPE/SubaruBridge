package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Phone-only preview binds loopback. The car uses Bluetooth, not a Wi-Fi port. */
public final class StarlinkRuntime {
    public static final int PREVIEW_PORT=8009;
    private static StarlinkRuntime instance;
    public final StarlinkRouter router;
    private ServerSocket server;
    private final String previewToken;
    private StarlinkRuntime(Context context){
        router=new StarlinkRouter(context);
        byte[] bytes=new byte[24];new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder token=new StringBuilder();for(byte b:bytes)token.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));previewToken=token.toString();
        // Debug tooling can read this with run-as. It is never part of a report.
        try(FileOutputStream out=new FileOutputStream(new File(context.getNoBackupFilesDir(),"preview-session"))){out.write(previewToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
        catch(IOException unavailable){android.util.Log.w("SubaruBridgePreview","Debug session file unavailable");}
    }
    public String previewCookie(){return "bridge-preview="+previewToken+"; Path=/; HttpOnly; SameSite=Strict";}
    private boolean authorized(LocalHttp.Request request){
        String provided=request.headers.getOrDefault("x-bridge-preview","");
        if(provided.equals(previewToken))return true;
        for(String item:request.headers.getOrDefault("cookie","").split(";"))if(item.trim().equals("bridge-preview="+previewToken))return true;
        return false;
    }
    public static synchronized StarlinkRuntime get(Context context){if(instance==null)instance=new StarlinkRuntime(context.getApplicationContext());return instance;}
    public synchronized void startPreview() throws IOException {
        if(server!=null && !server.isClosed())return;
        ServerSocket socket=new ServerSocket();
        socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),PREVIEW_PORT),4);server=socket;
        ThreadPoolExecutor clients=new ThreadPoolExecutor(0,4,15,TimeUnit.SECONDS,new SynchronousQueue<>());
        Thread thread=new Thread(()->{
            try {while(!socket.isClosed()) {
                Socket peer=socket.accept();peer.setSoTimeout(5000);
                try{clients.execute(()->serve(peer));}catch(RejectedExecutionException busy){peer.close();}
            }}catch(IOException ignored){}finally{clients.shutdownNow();try{socket.close();}catch(IOException ignored){}}
        },"bridge-loopback");thread.setDaemon(true);thread.start();
    }
    private void serve(Socket peer) {
        try(Socket socket=peer) {
            for(int i=0;i<128;i++) {
                LocalHttp.Request request;
                try{request=LocalHttp.read(socket.getInputStream());}
                catch(LocalHttp.Error invalid){socket.getOutputStream().write(router.error(invalid.status,invalid.getMessage(),true).wire);return;}
                if(request==null)return;
                String path;
                try{path=new URI(request.target).getPath();}catch(URISyntaxException invalid){path="";}
                LocalHttp.Response response=path!=null && path.startsWith("/subarubridge-diag/apps/") && !authorized(request)
                    ? router.error(403,"Open personal apps through SubaruBridge",request.close) : router.handle(request);
                socket.getOutputStream().write(response.wire);socket.getOutputStream().flush();if(response.close)return;
            }
        }catch(IOException ignored){}
    }
}
