package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Native receipt library and explicit backup review. No cloud or PC helper. */
public final class GarageDataActivity extends Activity {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private static final int PICK_RECEIPT=861,PICK_BACKUP=862,SAVE_FILE=863;
    private OwnershipStore store;private PhonePalette palette;private LinearLayout page;private TextView status;
    private String kind,recordId;private boolean receipts,busy,protect,isolated,externalPending;private File transfer,exportFile,workspace;private JSONObject exportingAttachment;
    private OwnershipBackup.Plan plan;private JSONObject review,localState;private final Map<String,Boolean> choices=new LinkedHashMap<>();
    private interface Job{void run() throws Exception;}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);palette=new PhonePalette(this);isolated=GarageActivity.testStore!=null;store=isolated?GarageActivity.testStore:new OwnershipStore(this);
        kind=getIntent().getStringExtra("kind");recordId=getIntent().getStringExtra("recordId");receipts=kind!=null&&recordId!=null;
        String transferId=saved==null?UUID.randomUUID().toString():saved.getString("transferId",UUID.randomUUID().toString());
        if(!transferId.matches("[a-f0-9-]{36}"))transferId=UUID.randomUUID().toString();
        workspace=new File(getNoBackupFilesDir(),"garage-transfers/"+transferId);workspace.mkdirs();
        final File transfersRoot=workspace.getParentFile(),activeWorkspace=workspace;
        WORK.execute(()->{File[] old=transfersRoot.listFiles();if(old!=null)for(File folder:old){if(!folder.equals(activeWorkspace)&&folder.getName().matches("[a-f0-9-]{36}")&&folder.lastModified()<System.currentTimeMillis()-86_400_000L)try{OwnershipFiles.removeTree(folder);}catch(IOException ignored){}}});
        if(saved!=null){String name=saved.getString("exportName","");if(name.matches("[A-Za-z0-9._-]{1,160}"))exportFile=new File(workspace,name);externalPending=saved.getBoolean("externalPending");}
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        home();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("transferId",workspace.getName());out.putBoolean("externalPending",externalPending);if(exportFile!=null)out.putString("exportName",exportFile.getName());}
    private boolean alive(){return !isFinishing()&&!isDestroyed();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextColor(bold?palette.text:palette.muted);t.setTextSize(size);t.setPadding(0,dp(8),0,dp(8));t.setTextIsSelectable(true);if(bold)t.setTypeface(null,Typeface.BOLD);page.addView(t);return t;}
    private Button button(String title,String id,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(palette.accent);b.setMinHeight(dp(50));b.setBackground(bg(palette.surface));b.setContentDescription(id);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));page.addView(b,p);b.setOnClickListener(v->{if(!busy)run.run();});return b;}
    private void begin(String title,String subtitle){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(palette.background);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(page);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());scroll.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        Button back=button("‹ Garage","garage-data-back",this::back);back.setBackgroundColor(Color.TRANSPARENT);back.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);back.setPadding(0,0,0,0);back.setMinHeight(dp(44));text(title,28,true);text(subtitle,14,false);status=text(busy?"Working on this phone…":"",14,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    private void error(Throwable e){String message=e.getMessage();status.setText(message==null||message.length()>240?"Could not complete this change. Your saved garage was preserved.":message);}
    private void job(Job job,Runnable done){
        if(busy)return;busy=true;status.setText("Working on this phone…");WORK.execute(()->{
            try{job.run();runOnUiThread(()->{busy=false;if(alive())done.run();else cleanup();});}
            catch(Exception|LinkageError e){runOnUiThread(()->{busy=false;if(alive())error(e);else cleanup();});}
        });
    }
    private void home(){
        if(receipts){begin("Receipts & photos","Private copies linked to this saved record. JPEG, PNG, WebP and PDF · 16 MiB per file.");
            button("Add photo or PDF","garage-add-receipt",()->pick(PICK_RECEIPT,"*/*",new String[]{"image/jpeg","image/png","image/webp","application/pdf"}));
            job(()->{store.view();},this::listReceipts);
        }else{
            begin("Backups & data","Complete backups include your garage and receipt files. Choose where to save them.");
            button("Create encrypted backup","garage-encrypted-backup",()->password(true));
            button("Create unencrypted backup","garage-complete-backup",()->new AlertDialog.Builder(this).setTitle("Unencrypted backup").setMessage("Vehicle identifiers, records and receipts will be readable by anyone who can access this file.").setPositiveButton("Create backup",(d,w)->prepareExport(null)).setNegativeButton("Cancel",null).show());
            button("Restore or merge a backup","garage-restore-backup",()->pick(PICK_BACKUP,"*/*",null));
            button("Export records-only JSON","garage-legacy-backup",()->job(()->{exportFile=new File(workspace,"SubaruBridge-records-"+LocalDate.now()+".json");try(OutputStream out=new FileOutputStream(exportFile)){out.write(store.backup());}},()->savePicker("application/json")));
            text("Records-only JSON keeps the original portable format and omits receipt files. Restore always shows a review before changing your garage.",14,false);
            text("No account or network is needed. Keep an exported backup before uninstalling. Encrypted backups require your password on any device; there is no password recovery.",14,false);
        }
    }
    private void listReceipts(){
        try{status.setText("");JSONArray files=store.attachments(kind,recordId);if(files.length()==0)text("No receipts attached yet.",17,false);
            for(int i=0;i<files.length();i++){JSONObject a=files.getJSONObject(i);String title=a.getString("name");button(title+" · "+fileSize(a.getLong("size")),"receipt-"+a.getString("id"),()->receiptActions(a));}
        }catch(Exception e){error(e);}
    }
    private static String fileSize(long bytes){return bytes<1024?bytes+" bytes":bytes<1048576?String.format(Locale.getDefault(),"%.1f KB",bytes/1024.0):String.format(Locale.getDefault(),"%.1f MiB",bytes/1048576.0);}
    private void receiptActions(JSONObject a){new AlertDialog.Builder(this).setTitle(a.optString("name")).setItems(new String[]{"View privately","Save a copy…","Remove attachment"},(d,index)->{
        if(index==0)job(()->{store.attachmentFile(a);},()->preview(a,0));
        if(index==1)job(()->{File source=store.attachmentFile(a);String extension=a.optString("mime").equals("application/pdf")?".pdf":a.optString("mime").equals("image/png")?".png":a.optString("mime").equals("image/webp")?".webp":".jpg";exportFile=new File(workspace,"Receipt-"+a.optString("id")+extension);try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(exportFile)){OwnershipFiles.copy(in,out,OwnershipFiles.MAX_FILE);}},()->savePicker(a.optString("mime")));
        if(index==2)new AlertDialog.Builder(this).setTitle("Remove this receipt?").setMessage("The saved vehicle or service record will remain. Previous exported backups may still contain this receipt.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(dialog,w)->job(()->store.removeAttachment(a.getString("id")),this::home)).show();
    }).show();}
    private void preview(JSONObject a,int index){
        begin(a.optString("name"),"Private receipt preview");
        job(()->{
            File source=store.attachmentFile(a);final Bitmap image;final int count;
            if(a.getString("mime").equals("application/pdf")){
                try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer pdf=new PdfRenderer(fd);PdfRenderer.Page p=pdf.openPage(index)){
                    count=pdf.getPageCount();float scale=Math.min(2f,Math.min(1800f/p.getWidth(),2400f/p.getHeight()));image=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);p.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                }
            }else{count=1;BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),options);int sample=1;while(options.outWidth/sample>1800||options.outHeight/sample>2400)sample*=2;options.inJustDecodeBounds=false;options.inSampleSize=sample;image=BitmapFactory.decodeFile(source.getPath(),options);if(image==null)throw new IOException("This image cannot be displayed");}
            runOnUiThread(()->{if(!alive()){image.recycle();return;}ImageView view=new ImageView(this);view.setAdjustViewBounds(true);view.setImageBitmap(image);view.setContentDescription("Receipt preview");page.addView(view,new LinearLayout.LayoutParams(-1,-2));
                text("Page "+(index+1)+" of "+count,14,false);if(index>0)button("Previous page","receipt-previous",()->{view.setImageDrawable(null);image.recycle();preview(a,index-1);});if(index+1<count)button("Next page","receipt-next",()->{view.setImageDrawable(null);image.recycle();preview(a,index+1);});button("Back to receipts","receipt-close",this::home);
            });
        },()->status.setText(""));
    }
    private void password(boolean exporting){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),0,dp(20),0);
        EditText password=new EditText(this);password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);password.setHint("Backup password");password.setContentDescription("Backup password");password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);fields.addView(password);
        EditText confirm=new EditText(this);if(exporting){confirm.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);confirm.setHint("Confirm password");confirm.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);fields.addView(confirm);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(exporting?"Protect your backup":"Unlock backup").setMessage(exporting?"Use at least 10 characters. Keep this password safely; it cannot be recovered.":"The file is authenticated before any restore review.").setView(fields).setPositiveButton(exporting?"Create":"Unlock",null).setNegativeButton("Cancel",(d,w)->{password.getText().clear();confirm.getText().clear();}).create();
        dialog.setOnDismissListener(d->{password.getText().clear();confirm.getText().clear();});
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            char[] secret=new char[password.length()];password.getText().getChars(0,password.length(),secret,0);
            if(exporting&&(secret.length<10||secret.length>256||!password.getText().toString().contentEquals(confirm.getText()))){Arrays.fill(secret,'\0');password.setError("Passwords must match and contain 10–256 characters");return;}
            password.getText().clear();confirm.getText().clear();dialog.dismiss();if(exporting)prepareExport(secret);else readBackup(secret);
        }));dialog.show();
    }
    private void prepareExport(char[] secret){
        protect=secret!=null;
        job(()->{File plain=new File(workspace,"complete.zip");try{store.archive(plain);exportFile=new File(workspace,"SubaruBridge-garage-"+LocalDate.now()+(secret==null?".zip":".sbbk"));if(secret==null){if(!plain.renameTo(exportFile))throw new IOException("Cannot prepare backup");}else OwnershipCrypto.encrypt(plain,exportFile,secret);}finally{if(secret!=null)Arrays.fill(secret,'\0');plain.delete();}},()->savePicker(protect?"application/octet-stream":"application/zip"));
    }
    private void readBackup(char[] secret){
        job(()->{File source=transfer;File plain=new File(workspace,"unlocked.zip");try{
            if(secret!=null){OwnershipCrypto.decrypt(transfer,plain,secret);source=plain;}
            if(plan!=null){plan.close();plan=null;}plan=OwnershipBackup.read(source,workspace);localState=store.view().getJSONObject("state");review=store.preview(plan);choices.clear();
        }finally{if(secret!=null)Arrays.fill(secret,'\0');plain.delete();}},this::showReview);
    }
    private void showReview(){
        begin("Review backup",plan.legacy?"Records-only JSON · no receipt files in this backup":"Complete garage and receipt backup");
        text(review.optInt("newRecords")+" new records · "+review.optInt("matchingRecords")+" matching records",18,true);
        text(review.optInt("newAttachments")+" new receipts · "+review.optInt("matchingAttachments")+" matching receipts",14,false);
        text("Existing records remain unless you explicitly choose an incoming version below. Import is applied together after validation.",14,false);
        JSONArray conflicts=review.optJSONArray("conflicts");
        for(int i=0;i<conflicts.length();i++){JSONObject c=conflicts.optJSONObject(i);String key=c.optString("key");button((choices.containsKey(key)?(choices.get(key)?"Use backup · ":"Keep local · "):"Review · ")+conflictTitle(c),"conflict-"+key,()->conflict(c));}
        Button apply=button("Import reviewed backup","garage-apply-restore",()->new AlertDialog.Builder(this).setTitle("Import this reviewed backup?").setMessage("This will add the reviewed records and apply your conflict choices to the garage on this phone.").setNegativeButton("Cancel",null).setPositiveButton("Import",(d,w)->{
            job(()->{JSONArray resolutions=new JSONArray();for(Map.Entry<String,Boolean> c:choices.entrySet())resolutions.put(new JSONObject().put("key",c.getKey()).put("useIncoming",c.getValue()));store.restore(plan,review,resolutions);plan.close();plan=null;if(!isolated)GarageReminders.refresh(getApplicationContext());},()->{home();status.setText("Backup imported. Your garage is saved on this phone.");});
        }).show());apply.setEnabled(choices.size()==conflicts.length());
        button("Cancel restore","garage-cancel-restore",()->{try{plan.close();plan=null;home();}catch(Exception e){error(e);}});
    }
    private void conflict(JSONObject c){
        String key=c.optString("key");String message="LOCAL\n"+describe(c.optJSONObject("local"),localState)+"\n\nBACKUP\n"+describe(c.optJSONObject("incoming"),plan.garage)+"\n\n"+c.optString("reason");
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle("Review "+c.optString("collection")).setMessage(message).setNegativeButton("Back",null).setPositiveButton("Keep local",(dialog,w)->{choices.put(key,false);showReview();});
        if(c.optBoolean("canReplace"))d.setNeutralButton("Use backup",(dialog,w)->{choices.put(key,true);showReview();});d.show();
    }
    private static String conflictTitle(JSONObject conflict){
        JSONObject value=conflict.optJSONObject("incoming");String category=conflict.optString("collection");
        String title=category.equals("vehicles")?"Vehicle profile":category.equals("readings")?"Odometer reading":category.equals("preferences")?"Reminder settings":category.equals("customItems")?"Maintenance item":category.equals("overrides")?"Service interval":category.equals("snoozes")?"Reminder snooze":category.equals("attachments")?"Receipt":category.equals("services")?"Service visit":category.equals("fuel")?"Fuel entry":category.equals("expenses")?"Expense":"Trip";
        if(value!=null)title+=" · "+value.optString("nickname",value.optString("title",value.optString("name",value.optString("date",value.optString("category","Saved record")))));
        return title;
    }
    private static String describe(JSONObject value,JSONObject garage){
        if(value==null)return "Unavailable";StringBuilder out=new StringBuilder();String currency=value.optString("currency","USD");boolean metric=false;
        try{JSONArray cars=garage.getJSONArray("vehicles");for(int i=0;i<cars.length();i++){JSONObject car=cars.getJSONObject(i);if(car.getString("id").equals(value.optString("vehicleId",value.optString("id")))){currency=car.optString("currency","USD");metric=car.optString("units").equals("metric");break;}}}catch(Exception ignored){}
        String[][] fields={{"nickname","Vehicle"},{"year","Year"},{"make","Make"},{"model","Model"},{"trim","Trim"},{"engine","Engine"},{"transmission","Transmission"},{"drivetrain","Drivetrain"},{"purchaseDate","Purchased"},{"inServiceDate","First in service"},{"currency","Currency"},{"units","Units"},{"vin","VIN"},{"plate","Plate"},{"date","Date"},{"provider","Provider"},{"category","Category"},{"title","Item"},{"action","Action"},{"name","Receipt"},{"warranty","Warranty"},{"notes","Notes"}};
        for(String[] field:fields)if(value.has(field[0])&&!value.isNull(field[0])&&!value.optString(field[0]).isEmpty())out.append(field[1]).append(": ").append(value.optString(field[0])).append('\n');
        if(value.has("purchaseMileageMm")&&!value.isNull("purchaseMileageMm"))out.append("Purchase odometer: ").append(OwnershipNumbers.distance(value.optLong("purchaseMileageMm"),metric)).append(metric?" km\n":" mi\n");
        if(value.has("purchasePriceCents")&&!value.isNull("purchasePriceCents"))out.append("Purchase price: ").append(OwnershipNumbers.money(value.optLong("purchasePriceCents"),currency)).append('\n');
        if(value.has("maintenanceProfile"))out.append("Manufacturer schedule: ").append(value.isNull("maintenanceProfile")?"Off":"2017 US Legacy 2.5 CVT").append('\n');
        if(value.has("severe")&&value.optBoolean("severe"))out.append("Unresolved broad severe-service flag\n");
        if(value.has("diy"))out.append("Service by: ").append(value.optBoolean("diy")?"Owner / DIY":"Service provider").append('\n');
        if(value.has("factoryBaseline")&&value.optBoolean("factoryBaseline"))out.append("Factory baseline, not a paid service\n");
        JSONObject breakdown=value.optJSONObject("costBreakdown");if(breakdown!=null)for(String key:new String[]{"partsCents","laborCents","taxCents"})out.append(key.equals("partsCents")?"Parts: ":key.equals("laborCents")?"Labor: ":"Tax: ").append(OwnershipNumbers.money(breakdown.optLong(key),currency)).append('\n');
        if(value.has("itemId"))out.append("Maintenance item: ").append(value.optString("itemId").replace('-', ' ')).append('\n');
        if(value.has("mime"))out.append("File type: ").append(value.optString("mime")).append(" · ").append(fileSize(value.optLong("size"))).append('\n');
        if(value.has("distanceMm"))out.append("Odometer/distance: ").append(OwnershipNumbers.distance(value.optLong("distanceMm"),metric)).append(metric?" km\n":" mi\n");
        JSONObject range=value.optJSONObject("mileage");if(range!=null)out.append("Service odometer: ").append(OwnershipNumbers.distance(range.optLong("lowMm"),metric)).append(" – ").append(OwnershipNumbers.distance(range.optLong("highMm"),metric)).append(metric?" km\n":" mi\n");
        if(value.has("costCents"))out.append("Total paid: ").append(OwnershipNumbers.money(value.optLong("costCents"),currency)).append('\n');
        if(value.has("atMs"))out.append("Recorded: ").append(java.time.Instant.ofEpochMilli(value.optLong("atMs")).atZone(java.time.ZoneId.systemDefault()).toString()).append('\n');
        if(value.has("full"))out.append("Fill: ").append(value.optBoolean("full")?"Full":"Partial").append('\n');
        if(value.has("volumeMl"))out.append("Fuel: ").append(String.format(Locale.getDefault(),"%.2f L",value.optLong("volumeMl")/1000.0)).append('\n');
        if(value.has("itemIds"))out.append("Completed work: ").append(value.optJSONArray("itemIds").toString().replaceAll("[\\[\\]\"]", "").replace('-', ' ')).append('\n');
        if(value.has("partNumbers"))out.append("Parts: ").append(value.optJSONArray("partNumbers")).append('\n');
        if(value.has("startedMs"))out.append("Trip starts: ").append(java.time.Instant.ofEpochMilli(value.optLong("startedMs"))).append('\n');
        if(value.has("endedMs"))out.append("Trip ends: ").append(java.time.Instant.ofEpochMilli(value.optLong("endedMs"))).append('\n');
        if(value.has("financing"))out.append("Cost category: ").append(value.optBoolean("financing")?"Financing":"Operating").append('\n');
        if(value.has("missedFill")&&value.optBoolean("missedFill"))out.append("An earlier fill-up was missed\n");
        if(value.has("confidence"))out.append("Confidence: ").append(value.optString("confidence")).append('\n');
        if(value.has("severeConditions"))out.append("Driving conditions: ").append(value.optJSONArray("severeConditions").toString().replaceAll("[\\[\\]\"]","")).append('\n');
        if(value.has("dueSoonMm"))out.append("Reminder lead distance: ").append(OwnershipNumbers.distance(value.optLong("dueSoonMm"),metric)).append(metric?" km\n":" mi\n");
        if(value.has("dueSoonDays"))out.append("Reminder lead time: ").append(value.optLong("dueSoonDays")).append(" days\n");
        JSONObject interval=value.optJSONObject("interval");if(interval!=null)out.append("Interval: ").append(interval.isNull("miles")?"":interval.optLong("miles")+" miles").append(interval.isNull("months")?"":" / "+interval.optLong("months")+" months").append('\n');
        if(value.has("until"))out.append("Snoozed until: ").append(value.optString("until")).append('\n');
        if(value.optBoolean("disabled"))out.append("Disabled\n");if(value.optBoolean("archived"))out.append("Archived vehicle\n");
        return out.length()==0?"Saved settings":out.toString();
    }
    private void pick(int code,String mime,String[] types){try{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);if(types!=null)i.putExtra(Intent.EXTRA_MIME_TYPES,types);externalPending=true;startActivityForResult(i,code);}catch(ActivityNotFoundException e){error(new IOException("No system file picker is available"));}}
    private void savePicker(String mime){try{externalPending=true;startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,exportFile.getName()),SAVE_FILE);}catch(ActivityNotFoundException e){error(e);}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);externalPending=false;if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==PICK_RECEIPT)job(()->{String name="Receipt";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open the selected receipt");store.addAttachment(kind,recordId,name,in);}},this::home);
        if(request==PICK_BACKUP)job(()->{transfer=new File(workspace,"selected.backup");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(transfer)){if(in==null)throw new IOException("Cannot open the selected backup");OwnershipFiles.copy(in,out,OwnershipCrypto.MAX_ARCHIVE+128*1024);}},()->{try{if(OwnershipCrypto.encrypted(transfer))password(false);else readBackup(null);}catch(Exception e){error(e);}});
        if(request==SAVE_FILE)job(()->{if(exportFile==null||!exportFile.isFile())throw new IOException("The prepared export is no longer available. Create the backup again; your garage is unchanged.");try(InputStream in=new FileInputStream(exportFile);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot write to the selected file");OwnershipFiles.copy(in,out,OwnershipCrypto.MAX_ARCHIVE+128*1024);}exportFile.delete();exportFile=null;},()->status.setText("Saved to your chosen file."));
    }
    private void back(){if(busy){Toast.makeText(this,"Finishing the current file operation…",Toast.LENGTH_SHORT).show();return;}if(plan!=null)new AlertDialog.Builder(this).setTitle("Cancel restore review?").setMessage("Your saved garage will stay as it is.").setNegativeButton("Keep reviewing",null).setPositiveButton("Cancel restore",(d,w)->finish()).show();else finish();}
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){back();}
    private void cleanup(){try{if(plan!=null){plan.close();plan=null;}if(workspace!=null)OwnershipFiles.removeTree(workspace);}catch(IOException ignored){/* Private temporary files; no owner records are removed. */}}
    @Override protected void onDestroy(){super.onDestroy();if(!busy&&!isChangingConfigurations()&&(!externalPending||isFinishing()))cleanup();}
}
