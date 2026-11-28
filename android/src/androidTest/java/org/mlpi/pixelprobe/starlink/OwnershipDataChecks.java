package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/** Real Android storage, crypto, PDF and native-review paths using synthetic private files only. */
final class OwnershipDataChecks {
    private interface Action{void run() throws Exception;}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void rejected(Action action) throws Exception{try{action.run();throw new AssertionError("Invalid ownership data was accepted");}catch(IOException|JSONException|IllegalArgumentException expected){}}
    private static JSONObject car() throws Exception{return new JSONObject("{\"id\":\"qa-car\",\"nickname\":\"Demo Legacy\",\"make\":\"Subaru\",\"model\":\"Legacy\",\"year\":2017,\"engine\":\"2.5L\",\"transmission\":\"CVT\",\"maintenanceProfile\":\"subaru-us-2017-legacy-25-cvt\"}");}
    private static JSONObject command(String type,String key,JSONObject value) throws Exception{return new JSONObject().put("type",type).put(key,value);}
    static void run(Instrumentation i,Context context,Bundle results) throws Exception{
        File root=new File(context.getCacheDir(),"ownership-data-qa-"+UUID.randomUUID());check(root.mkdir(),"Cannot prepare data test folder");
        try{data(root,context);ui(i,context,root);results.putString("ownershipData","PASS: v1 migration/future preservation, photo/PDF receipts, complete/encrypted round-trip, wrong password/tamper/truncation, unsafe ZIP rejection, stale restore/conflict review, native encrypted restore and receipt screens, reminder calendar");}
        finally{GarageActivity.testStore=null;OwnershipFiles.removeTree(root);}
    }
    private static void data(File root,Context context) throws Exception{
        Clock clock=Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC);File path=new File(root,"main.json");OwnershipStore store=new OwnershipStore(path,clock);
        store.execute(command("addVehicle","vehicle",car()));
        byte[] state=store.view().getJSONObject("state").toString().getBytes(StandardCharsets.UTF_8);
        File legacy=new File(root,"legacy.json");Files.write(legacy.toPath(),state);OwnershipStore old=new OwnershipStore(legacy,clock);long revision=old.view().getJSONObject("state").getLong("revision");
        JSONObject envelope=OwnershipStore.parse(Files.readAllBytes(legacy.toPath()));check(envelope.getInt("storageVersion")==2&&old.view().getJSONObject("state").getLong("revision")==revision,"Legacy migration lost data or changed business revision");
        File future=new File(root,"future.json");envelope.put("storageVersion",3);byte[] unsupported=envelope.toString().getBytes(StandardCharsets.UTF_8);Files.write(future.toPath(),unsupported);rejected(()->new OwnershipStore(future,clock).view());check(Arrays.equals(unsupported,Files.readAllBytes(future.toPath())),"Future storage was overwritten");
        rejected(()->OwnershipStore.parse("{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream photo=new ByteArrayOutputStream();Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(30,110,220));check(bitmap.compress(Bitmap.CompressFormat.PNG,100,photo),"Cannot create photo fixture");bitmap.recycle();
        JSONObject image=store.addAttachment("vehicles","qa-car","Synthetic photo.png",new ByteArrayInputStream(photo.toByteArray()));
        File pdfFile=new File(root,"receipt.pdf");PdfDocument pdf=new PdfDocument();try{PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(400,500,1).create());Paint paint=new Paint();paint.setTextSize(22);page.getCanvas().drawText("SYNTHETIC SERVICE RECEIPT",15,45,paint);pdf.finishPage(page);try(OutputStream out=new FileOutputStream(pdfFile)){pdf.writeTo(out);}}finally{pdf.close();}
        JSONObject receipt;try(InputStream in=new FileInputStream(pdfFile)){receipt=store.addAttachment("vehicles","qa-car","Synthetic service receipt.pdf",in);}
        check(store.attachments("vehicles","qa-car").length()==2&&store.attachmentFile(receipt).isFile(),"Receipt persistence failed");
        byte[] before=Files.readAllBytes(path.toPath());rejected(()->store.addAttachment("vehicles","unknown","orphan.png",new ByteArrayInputStream(photo.toByteArray())));check(Arrays.equals(before,Files.readAllBytes(path.toPath())),"Orphan receipt changed garage");
        rejected(()->store.addAttachment("vehicles","qa-car","fake.pdf",new ByteArrayInputStream("not an actual pdf".getBytes(StandardCharsets.UTF_8))));
        File zip=new File(root,"backup.zip");store.archive(zip);OwnershipStore restored=new OwnershipStore(new File(root,"restored.json"),clock);
        try(OwnershipBackup.Plan plan=OwnershipBackup.read(zip,root)){
            JSONObject review=restored.preview(plan);check(review.getInt("newAttachments")==2,"Receipt import preview missing");restored.restore(plan,review,new JSONArray());check(restored.attachments(null,null).length()==2,"Complete backup lost receipts");
            JSONObject same=restored.preview(plan);check(same.getJSONArray("conflicts").length()==0,"Identical complete backup conflicted");long rev=restored.view().getJSONObject("state").getLong("revision");restored.restore(plan,same,new JSONArray());check(restored.view().getJSONObject("state").getLong("revision")==rev,"Repeated complete backup duplicated history");
            JSONObject stale=restored.preview(plan);restored.removeAttachment(image.getString("id"));rejected(()->restored.restore(plan,stale,new JSONArray()));
        }
        // More than one crypto chunk, plus a separately authenticated end record.
        File plain=new File(root,"multi.bin"),encrypted=new File(root,"backup.sbbk"),opened=new File(root,"opened.bin");byte[] random=new byte[600_000];new java.security.SecureRandom().nextBytes(random);Files.write(plain.toPath(),random);
        char[] password="Synthetic QA password 🔑".toCharArray();OwnershipCrypto.encrypt(plain,encrypted,password);OwnershipCrypto.decrypt(encrypted,opened,password);check(Arrays.equals(random,Files.readAllBytes(opened.toPath())),"Chunked encryption round trip failed");
        rejected(()->OwnershipCrypto.decrypt(encrypted,opened,"wrong password".toCharArray()));check(!opened.exists(),"Failed authentication left plaintext output");
        byte[] cipher=Files.readAllBytes(encrypted.toPath());cipher[70]^=1;File tampered=new File(root,"tampered.sbbk");Files.write(tampered.toPath(),cipher);rejected(()->OwnershipCrypto.decrypt(tampered,opened,password));check(!opened.exists(),"Tamper left plaintext output");
        cipher=Files.readAllBytes(encrypted.toPath());Files.write(tampered.toPath(),Arrays.copyOf(cipher,cipher.length-20));rejected(()->OwnershipCrypto.decrypt(tampered,opened,password));check(!opened.exists(),"Truncated end record accepted");
        Arrays.fill(password,'\0');
        File vectorPlain=new File(root,"interop.txt");Files.write(vectorPlain.toPath(),"SubaruBridge synthetic backup interoperability\n".getBytes(StandardCharsets.UTF_8));
        OwnershipCrypto.encrypt(vectorPlain,new File(context.getCacheDir(),"ownership058-crypto-vector.sbbk"),"Interop-only passphrase 🔑".toCharArray());
        File unsafe=new File(root,"unsafe.zip");try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(unsafe))){out.putNextEntry(new ZipEntry("../outside"));out.write(1);out.closeEntry();}rejected(()->OwnershipBackup.read(unsafe,root));check(!new File(root.getParentFile(),"outside").exists(),"ZIP escaped staging");
        File changed=new File(root,"conflict.json");JSONObject backup=OwnershipStore.parse(store.backup());backup.getJSONObject("garage").getJSONArray("vehicles").getJSONObject(0).put("nickname","Incoming name");Files.write(changed.toPath(),backup.toString().getBytes(StandardCharsets.UTF_8));
        try(OwnershipBackup.Plan plan=OwnershipBackup.read(changed,root)){
            JSONObject review=store.preview(plan);check(review.getJSONArray("conflicts").length()==1,"Conflict preview missing");rejected(()->store.restore(plan,review,new JSONArray()));check(Arrays.equals(before,Files.readAllBytes(path.toPath())),"Unreviewed backup changed data");
            store.restore(plan,review,new JSONArray().put(new JSONObject().put("key","vehicles/qa-car").put("useIncoming",false)));check(store.view().getJSONObject("state").getJSONArray("vehicles").getJSONObject(0).getString("nickname").equals("Demo Legacy"),"Keep local choice ignored");
        }
        ZoneId zone=ZoneId.of("America/New_York");long morning=Instant.parse("2026-11-27T12:00:00Z").toEpochMilli();check(GarageReminders.nextCheck(morning,zone)==Instant.parse("2026-11-27T14:00:00Z").toEpochMilli(),"Reminder local morning wrong");
        check(GarageReminders.nextCheck(Instant.parse("2026-03-07T15:00:00Z").toEpochMilli(),zone)==Instant.parse("2026-03-08T13:00:00Z").toEpochMilli(),"Reminder DST calendar wrong");
    }
    private static View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText())||text.equals(v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int j=0;j<group.getChildCount();j++){View found=find(group.getChildAt(j),text);if(found!=null)return found;}}return null;}
    private static void await(Instrumentation i,Activity a,String text) throws Exception{long until=System.currentTimeMillis()+20_000;while(System.currentTimeMillis()<until){AtomicReference<View> found=new AtomicReference<>();i.runOnMainSync(()->found.set(find(a.getWindow().getDecorView(),text)));if(found.get()!=null)return;Thread.sleep(100);}throw new AssertionError("Native data screen missing "+text);}
    private static android.view.accessibility.AccessibilityNodeInfo dialogNode(Instrumentation i,String text)throws Exception{
        android.accessibilityservice.AccessibilityServiceInfo info=i.getUiAutomation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;i.getUiAutomation().setServiceInfo(info);
        long until=System.currentTimeMillis()+12000;StringBuilder seen=new StringBuilder();
        while(System.currentTimeMillis()<until){
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> roots=new java.util.ArrayList<>();android.view.accessibility.AccessibilityNodeInfo active=i.getUiAutomation().getRootInActiveWindow();if(active!=null)roots.add(active);
            for(android.view.accessibility.AccessibilityWindowInfo window:i.getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root!=null)roots.add(root);}
            for(android.view.accessibility.AccessibilityNodeInfo root:roots)for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text)){
                String label=n.getText()==null?"":n.getText().toString(),description=n.getContentDescription()==null?"":n.getContentDescription().toString();
                if(text.equalsIgnoreCase(label)||text.equalsIgnoreCase(description))return n;
            }Thread.sleep(80);
        }throw new AssertionError("Backup dialog missing "+text);
    }
    private static void ui(Instrumentation i,Context context,File root) throws Exception{
        OwnershipStore fixture=new OwnershipStore(new File(root,"ui.json"),Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"),ZoneOffset.UTC));fixture.execute(command("addVehicle","vehicle",car()));GarageActivity.testStore=fixture;
        Activity a=i.startActivitySync(new Intent(context,GarageDataActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("kind","vehicles").putExtra("recordId","qa-car"));
        try{await(i,a,"garage-add-receipt");await(i,a,"No receipts attached yet.");}finally{i.runOnMainSync(a::finish);}
        // Import handler exercises the same file-picker result path as an owner restore.
        File raw=new File(root,"ui-backup.zip"),source=new File(root,"ui-backup.sbbk");fixture.archive(raw);OwnershipCrypto.encrypt(raw,source,"Synthetic restore password".toCharArray());Activity backup=i.startActivitySync(new Intent(context,GarageDataActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            await(i,backup,"garage-encrypted-backup");
            i.runOnMainSync(()->((GarageDataActivity)backup).onActivityResult(862,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(source))));
            android.view.accessibility.AccessibilityNodeInfo field=dialogNode(i,"Backup password");Bundle text=new Bundle();text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Synthetic restore password");check(field.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,text),"Backup password input failed");dialogNode(i,"Unlock").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);
            await(i,backup,"Review backup");await(i,backup,"garage-apply-restore");
            i.runOnMainSync(()->{View button=find(backup.getWindow().getDecorView(),"garage-cancel-restore");check(button!=null,"Cancel restore missing");button.performClick();});await(i,backup,"garage-encrypted-backup");
        }finally{i.runOnMainSync(backup::finish);GarageActivity.testStore=null;}
    }
}
