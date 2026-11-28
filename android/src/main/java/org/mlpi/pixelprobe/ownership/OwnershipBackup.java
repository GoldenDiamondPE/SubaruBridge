package org.mlpi.pixelprobe.ownership;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Versioned complete local backup. Legacy v1 JSON remains importable and exportable. */
public final class OwnershipBackup {
    public static final String FORMAT="org.subarubridge.garage.bundle";
    public interface Source {File file(JSONObject attachment) throws Exception;}
    public static final class Plan implements AutoCloseable {
        public final JSONObject garage; public final JSONArray attachments; public final boolean legacy;
        private final File directory;
        private Plan(JSONObject garage,JSONArray attachments,File directory,boolean legacy){this.garage=garage;this.attachments=attachments;this.directory=directory;this.legacy=legacy;}
        public File blob(JSONObject a) throws Exception {return OwnershipFiles.blob(directory,a.getString("sha256"));}
        @Override public void close() throws IOException {OwnershipFiles.removeTree(directory);}
    }
    private OwnershipBackup(){}
    public static void write(File target,JSONObject garage,JSONArray attachments,Source source,String at) throws Exception {
        OwnershipStore.validateAttachments(garage,attachments);
        JSONObject manifest=new JSONObject().put("format",FORMAT).put("schemaVersion",2).put("exportedAt",at).put("garage",garage).put("attachments",attachments);
        byte[] json=manifest.toString().getBytes(StandardCharsets.UTF_8);if(json.length>OwnershipStore.MAX_MANIFEST)throw new IOException("Backup manifest exceeds supported size");
        boolean done=false;
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
            zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(json);zip.closeEntry();HashSet<String> hashes=new HashSet<>();
            for(int i=0;i<attachments.length();i++){
                JSONObject a=attachments.getJSONObject(i);if(!hashes.add(a.getString("sha256")))continue;
                File blob=source.file(a);zip.putNextEntry(new ZipEntry("blobs/"+a.getString("sha256")));
                try(InputStream in=new FileInputStream(blob)){OwnershipFiles.copy(in,zip,OwnershipFiles.MAX_FILE);}zip.closeEntry();
            }
            done=true;
        }finally{if(!done)target.delete();}
    }
    public static Plan read(File source,File privateStagingParent) throws Exception {
        if(source.length()>OwnershipCrypto.MAX_ARCHIVE)throw new IOException("Backup exceeds the supported size");
        if(!privateStagingParent.isDirectory()&&!privateStagingParent.mkdirs())throw new IOException("Private restore storage is unavailable");
        File directory=new File(privateStagingParent,"restore-"+UUID.randomUUID());if(!directory.mkdir())throw new IOException("Cannot prepare private restore storage");
        boolean done=false;
        try{
            int magic;try(InputStream in=new FileInputStream(source)){magic=in.read();}
            if(magic!='P'){
                JSONObject json;try(InputStream in=new FileInputStream(source)){json=OwnershipStore.parse(OwnershipStore.readBounded(in,OwnershipStore.MAX_BACKUP));}
                OwnershipStore.requireKeys(json,"format","schemaVersion","exportedAt","garage");
                if(!"org.subarubridge.garage".equals(json.getString("format"))||OwnershipStore.integer(json,"schemaVersion")!=1)throw new IOException("Unsupported records backup version");
                done=true;return new Plan(json.getJSONObject("garage"),new JSONArray(),directory,true);
            }
            try(ZipFile zip=new ZipFile(source)){
                HashSet<String> names=new HashSet<>();Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    ZipEntry e=entries.nextElement();String name=e.getName();
                    if(e.isDirectory()||!name.equals("manifest.json")&&!name.matches("blobs/[a-f0-9]{64}")||!names.add(name)||names.size()>OwnershipFiles.MAX_COUNT+1)throw new IOException("Backup has duplicate, unsupported or unsafe file paths");
                }
                ZipEntry entry=zip.getEntry("manifest.json");if(entry==null)throw new IOException("Backup manifest is missing");
                JSONObject manifest;try(InputStream in=zip.getInputStream(entry)){manifest=OwnershipStore.parse(OwnershipStore.readBounded(in,OwnershipStore.MAX_MANIFEST));}
                OwnershipStore.requireKeys(manifest,"format","schemaVersion","exportedAt","garage","attachments");
                if(!FORMAT.equals(manifest.getString("format"))||OwnershipStore.integer(manifest,"schemaVersion")!=2)throw new IOException("Unsupported complete backup version");
                JSONObject garage=manifest.getJSONObject("garage");JSONArray attachments=manifest.getJSONArray("attachments");OwnershipStore.validateAttachments(garage,attachments);
                HashSet<String> expected=new HashSet<>();expected.add("manifest.json");
                for(int i=0;i<attachments.length();i++){
                    JSONObject a=attachments.getJSONObject(i);String hash=a.getString("sha256");File blob=OwnershipFiles.blob(directory,hash);
                    if(expected.add("blobs/"+hash)){
                        ZipEntry e=zip.getEntry("blobs/"+hash);if(e==null)throw new IOException("Backup is missing a receipt");
                        try(InputStream in=zip.getInputStream(e);OutputStream out=new FileOutputStream(blob)){OwnershipFiles.copy(in,out,OwnershipFiles.MAX_FILE);}
                        if(!hash.equals(OwnershipFiles.hash(blob)))throw new IOException("Backup receipt failed its integrity check");
                    }
                    if(blob.length()!=a.getLong("size")||!OwnershipFiles.inspect(blob).equals(a.getString("mime")))throw new IOException("Backup receipt metadata does not match its file");
                }
                if(!names.equals(expected))throw new IOException("Backup contains unreferenced files");
                done=true;return new Plan(garage,attachments,directory,false);
            }
        }finally{if(!done)OwnershipFiles.removeTree(directory);}
    }
}
