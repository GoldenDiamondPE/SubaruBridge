package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.mlpi.pixelprobe.R;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Captures actual native screens using an isolated synthetic garage; never changes owner records. */
final class OwnershipPreview {
    static void run(Instrumentation i,Context c,Bundle result)throws Exception{
        File root=new File(c.getCacheDir(),"ownership-preview-fixture-"+UUID.randomUUID()),output=new File(c.getCacheDir(),"ownership058-previews");root.mkdirs();output.mkdirs();
        OwnershipStore store=new OwnershipStore(new File(root,"garage.json"),Clock.systemDefaultZone());
        JSONObject car=new JSONObject("{\"id\":\"demo\",\"nickname\":\"Demo Legacy · sample data\",\"make\":\"Subaru\",\"model\":\"Legacy\",\"year\":2017,\"engine\":\"2.5L\",\"trim\":\"2.5i Premium\",\"transmission\":\"CVT\",\"maintenanceProfile\":\"subaru-us-2017-legacy-25-cvt\",\"severeConditions\":[\"shortTrips\"]}");
        store.execute(new JSONObject().put("type","addVehicle").put("vehicle",car));
        for(int n=0;n<4;n++){String date="2026-"+String.format(Locale.ROOT,"%02d",8+n)+"-15";long at=LocalDate.parse(date).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),miles=80000+n*350;
            JSONObject reading=new JSONObject().put("id","r"+n).put("vehicleId","demo").put("atMs",at).put("distanceMm",miles*1609344L).put("confidence","confirmed").put("source","manual");store.execute(new JSONObject().put("type","addReading").put("reading",reading));
            JSONObject fill=new JSONObject().put("id","f"+n).put("vehicleId","demo").put("atMs",at).put("distanceMm",miles*1609344L).put("volumeMl",41000+n*1000).put("costCents",3900+n*150).put("full",true);store.execute(new JSONObject().put("type","addFuel").put("entry",fill));
            JSONObject trip=new JSONObject().put("id","t"+n).put("vehicleId","demo").put("startedMs",at+86400000).put("endedMs",at+86400000+1800000).put("distanceMm",(20+n*7)*1609344L).put("source","manual");store.execute(new JSONObject().put("type","addTrip").put("trip",trip));
        }
        JSONObject service=new JSONObject().put("id","service").put("vehicleId","demo").put("itemIds",new JSONArray().put("engine-oil").put("oil-filter")).put("date","2026-09-01").put("mileage",new JSONObject().put("lowMm",79500*1609344L).put("highMm",80000*1609344L)).put("costCents",7400).put("provider","Sample service visit").put("notes","Synthetic demonstration record; approximate mileage retained.");store.execute(new JSONObject().put("type","addService").put("service",service));
        File receipt=new File(root,"receipt.pdf");PdfDocument pdf=new PdfDocument();try{PdfDocument.Page p=pdf.startPage(new PdfDocument.PageInfo.Builder(500,650,1).create());Paint paint=new Paint();paint.setTextSize(24);p.getCanvas().drawText("SAMPLE SERVICE RECEIPT",30,60,paint);paint.setTextSize(18);p.getCanvas().drawText("Demonstration data · $74.00",30,110,paint);pdf.finishPage(p);try(OutputStream out=new FileOutputStream(receipt)){pdf.writeTo(out);}}finally{pdf.close();}
        try(InputStream in=new FileInputStream(receipt)){store.addAttachment("services","service","Sample oil service receipt.pdf",in);}
        GarageActivity.testStore=store;Activity screen=null;
        try{
            screen=i.startActivitySync(new Intent(c,GarageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));keep(i,screen);await(i,screen,R.id.garage_mileage);capture(i,new File(output,"overview.png"));
            click(i,screen,R.id.garage_maintenance);capture(i,new File(output,"maintenance.png"));
            click(i,screen,R.id.garage_more);click(i,screen,R.id.garage_analytics);await(i,screen,R.id.garage_chart_mode);capture(i,new File(output,"analytics.png"));
            click(i,screen,R.id.garage_more);final Activity active=screen;i.runOnMainSync(()->{View button=find(active.getWindow().getDecorView(),"Fluids, parts & specifications");if(button==null)throw new AssertionError("Reference missing");button.performClick();});capture(i,new File(output,"reference.png"));i.runOnMainSync(screen::finish);screen=null;
            screen=i.startActivitySync(new Intent(c,GarageDataActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));keep(i,screen);capture(i,new File(output,"backups.png"));i.runOnMainSync(screen::finish);screen=null;
            screen=i.startActivitySync(new Intent(c,GarageDataActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind","services").putExtra("recordId","service"));keep(i,screen);awaitText(i,screen,"Sample oil service receipt.pdf");capture(i,new File(output,"receipts.png"));
            result.putString("ownershipPreview","PASS: six actual Android screenshots captured from isolated synthetic data");
        }finally{if(screen!=null)i.runOnMainSync(screen::finish);GarageActivity.testStore=null;c.getSharedPreferences("garage-qa-selection",Context.MODE_PRIVATE).edit().clear().commit();OwnershipFiles.removeTree(root);}
    }
    private static void keep(Instrumentation i,Activity a){i.runOnMainSync(()->a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}
    private static View find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(text))return v;if(v instanceof ViewGroup){for(int n=0;n<((ViewGroup)v).getChildCount();n++){View f=find(((ViewGroup)v).getChildAt(n),text);if(f!=null)return f;}}return null;}
    private static void awaitText(Instrumentation i,Activity a,String text)throws Exception{long until=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<until){AtomicReference<View> v=new AtomicReference<>();i.runOnMainSync(()->v.set(find(a.getWindow().getDecorView(),text)));if(v.get()!=null)return;Thread.sleep(50);}throw new AssertionError("Preview screen missing "+text);}
    private static void await(Instrumentation i,Activity a,int id)throws Exception{long until=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<until){AtomicReference<View> v=new AtomicReference<>();i.runOnMainSync(()->v.set(a.findViewById(id)));if(v.get()!=null)return;Thread.sleep(50);}throw new AssertionError("Preview control missing "+id);}
    private static void click(Instrumentation i,Activity a,int id)throws Exception{await(i,a,id);i.runOnMainSync(()->a.findViewById(id).performClick());}
    private static void capture(Instrumentation i,File out)throws Exception{i.waitForIdleSync();Thread.sleep(1200);Bitmap image=i.getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot unavailable");try(OutputStream stream=new FileOutputStream(out)){image.compress(Bitmap.CompressFormat.PNG,100,stream);}finally{image.recycle();}}
}
