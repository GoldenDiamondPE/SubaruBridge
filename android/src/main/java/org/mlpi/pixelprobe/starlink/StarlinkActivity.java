package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.IOException;
import java.util.ArrayList;

/** The same original dashboard on the phone and on the head unit. */
public final class StarlinkActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView status;
    private WebView preview;
    private boolean coreReady;
    private boolean exporting;
    private final Runnable refresh=new Runnable(){public void run(){status.setText(StarlinkService.state()+"\nRust core: "+(coreReady?"ready":"unavailable")+" · "+DiagnosticEngine.BUILD);handler.postDelayed(this,1000);}};
    private void button(LinearLayout panel,String label,Runnable action) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setOnClickListener(v->action.run());panel.addView(b);
    }
    private void text(LinearLayout panel,String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,12,0,12);panel.addView(t);}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24,24,24,24);scroll.addView(panel);setContentView(scroll);
        panel.setOnApplyWindowInsetsListener((v,insets)->{var bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());panel.setPadding(24+bars.left,24+bars.top,24+bars.right,24+bars.bottom);return insets;});
        text(panel,"SubaruBridge · Car workspace",26);
        text(panel,"Local screens over paired Bluetooth. This standalone transport is experimental and needs a parked-car test. Close the old STARLINK/Research app before starting.",15);
        status=new TextView(this);status.setMinLines(2);panel.addView(status);
        try(RustCore ignored=new RustCore(1)){coreReady=true;}catch(IOException | LinkageError e){status.setText("Rust transport unavailable: "+e.getClass().getSimpleName());}
        button(panel,"Start car connection",this::startConnection);
        button(panel,"Stop car connection",()->stopService(new Intent(this,StarlinkService.class)));
        button(panel,"Pair a Bluetooth device",()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        button(panel,"Reload dashboard preview",()->preview.loadUrl(local("/subarubridge-diag/dashboard")));
        button(panel,"Export private test report",this::exportReport);
        text(panel,"Preview · JavaScript off",17);
        preview=new WebView(this);preview.getSettings().setJavaScriptEnabled(false);
        preview.getSettings().setAllowFileAccess(false);preview.getSettings().setAllowContentAccess(false);preview.getSettings().setDomStorageEnabled(false);
        preview.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !allowed(request.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                return allowed(request.getUrl())?null:new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
            }
        });
        panel.addView(preview,new LinearLayout.LayoutParams(-1,(int)(560*getResources().getDisplayMetrics().density)));
        try{StarlinkRuntime.get(this).startPreview();preview.loadUrl(local("/subarubridge-diag/dashboard"));}
        catch(IOException e){text(panel,"Preview could not start: close other test copies and reopen.",16);}
        if(getIntent().getBooleanExtra("start",false))startConnection();
    }
    private String local(String path){return "http://127.0.0.1:"+StarlinkRuntime.PREVIEW_PORT+path;}
    @Override public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("start",false))startConnection();}
    private boolean allowed(Uri uri){return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort()==StarlinkRuntime.PREVIEW_PORT && uri.getUserInfo()==null;}
    private void startConnection() {
        if(!coreReady){Toast.makeText(this,"Install a build containing the Rust transport",Toast.LENGTH_LONG).show();return;}
        ArrayList<String> missing=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),51);return;}
        launchService();
    }
    private void launchService(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){Toast.makeText(this,"Nearby devices permission is needed for Bluetooth",Toast.LENGTH_LONG).show();return;}
        try{startForegroundService(new Intent(this,StarlinkService.class));}catch(RuntimeException e){Toast.makeText(this,"Open this screen and try starting again",Toast.LENGTH_LONG).show();}
    }
    private void exportReport() {
        if(exporting)return;exporting=true;
        new Thread(()->{
            Uri destination=null;
            try {
                ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"subarubridge-report-"+System.currentTimeMillis()+".zip");
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/zip");values.put(android.provider.MediaStore.Downloads.IS_PENDING,1);
                destination=getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(destination==null)throw new IOException("No export destination");
                try(java.io.OutputStream out=getContentResolver().openOutputStream(destination,"w")){StarlinkRuntime.get(this).router.exportReport(out);}
                ContentValues ready=new ContentValues();ready.put(android.provider.MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(destination,ready,null,null);
                handler.post(()->Toast.makeText(this,"Private report saved in Downloads. Review before sharing.",Toast.LENGTH_LONG).show());
            }catch(IOException | RuntimeException e){if(destination!=null)getContentResolver().delete(destination,null,null);handler.post(()->Toast.makeText(this,"Export failed; original data is preserved",Toast.LENGTH_LONG).show());}
            finally{handler.post(()->exporting=false);}
        },"bridge-export").start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==51)launchService();}
    @Override public void onResume(){super.onResume();handler.post(refresh);}
    @Override public void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){if(preview!=null){preview.stopLoading();preview.destroy();}super.onDestroy();}
}
