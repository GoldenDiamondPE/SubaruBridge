package org.mlpi.pixelprobe.starlink;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.util.function.Consumer;

/** One explicit Downloads export shared by everyday and developer screens. */
final class PrivateReport {
    static void save(Activity activity,Consumer<Boolean> done) {
        new Thread(()->{
            Uri destination=null;boolean success=false;
            try {
                ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,"subarubridge-report-"+System.currentTimeMillis()+".zip");
                values.put(MediaStore.Downloads.MIME_TYPE,"application/zip");values.put(MediaStore.Downloads.IS_PENDING,1);
                destination=activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(destination==null)throw new IOException("Export destination unavailable");
                try(OutputStream out=activity.getContentResolver().openOutputStream(destination,"w")) {
                    if(out==null)throw new IOException("Export destination unavailable");
                    StarlinkRuntime.get(activity).router.exportReport(out);
                }
                ContentValues ready=new ContentValues();ready.put(MediaStore.Downloads.IS_PENDING,0);
                if(activity.getContentResolver().update(destination,ready,null,null)!=1)throw new IOException("Export could not be finalized");
                success=true;
            }catch(IOException|RuntimeException failure) {
                if(destination!=null)try{activity.getContentResolver().delete(destination,null,null);}catch(RuntimeException ignored){}
            }
            boolean saved=success;activity.runOnUiThread(()->done.accept(saved));
        },"bridge-private-export").start();
    }
    private PrivateReport(){}
}
