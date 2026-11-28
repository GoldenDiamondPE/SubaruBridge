package org.mlpi.pixelprobe.ownership;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Portable SBBK v1: authenticated header, PBKDF2-SHA256, AES-256-GCM. No stored password. */
public final class OwnershipCrypto {
    private static final byte[] MAGIC = new byte[]{'S','B','B','K',1,0,0,0};
    public static final int ITERATIONS = 600_000, HEADER_SIZE = 40;
    public static final long MAX_ARCHIVE = OwnershipFiles.MAX_TOTAL + 4L * 1024 * 1024;
    private OwnershipCrypto() {}
    public static boolean encrypted(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) { return in.read() == 'S' && in.read() == 'B' && in.read() == 'B' && in.read() == 'K'; }
    }
    private static final int CHUNK = 256 * 1024;
    private static byte[] key(byte[] header, char[] password) throws Exception {
        if (password.length < 1 || password.length > 256) throw new IOException("Enter the backup password (up to 256 characters)");
        byte[] magic=Arrays.copyOf(header,8); int work=ByteBuffer.wrap(header,8,4).getInt();
        if (!Arrays.equals(magic,MAGIC)||work!=ITERATIONS) throw new IOException("Unsupported encrypted backup version");
        PBEKeySpec spec=new PBEKeySpec(password,Arrays.copyOfRange(header,12,28),work,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    private static byte[] chunk(int mode, byte[] key, byte[] header, int index, int length, byte[] input) throws Exception {
        byte[] nonce=Arrays.copyOfRange(header,28,40); int base=ByteBuffer.wrap(nonce,8,4).getInt(); ByteBuffer.wrap(nonce,8,4).putInt(base^index);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        c.updateAAD(header);c.updateAAD(ByteBuffer.allocate(8).putInt(index).putInt(length).array());return c.doFinal(input);
    }
    private static byte[] readChunk(InputStream in) throws IOException {
        byte[] block=new byte[CHUNK];int total=0,n;while(total<CHUNK&&(n=in.read(block,total,CHUNK-total))!=-1){if(n==0)continue;total+=n;}return Arrays.copyOf(block,total);
    }
    public static void encrypt(File plain, File target, char[] password) throws Exception {
        if(password.length<10)throw new IOException("Use a backup password with at least 10 characters");
        byte[] salt=new byte[16],nonce=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(nonce);
        byte[] header=ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).putInt(ITERATIONS).put(salt).put(nonce).array(),key=key(header,password);boolean done=false;
        try(DataInputStream in=new DataInputStream(new FileInputStream(plain));DataOutputStream out=new DataOutputStream(new FileOutputStream(target))){
            out.write(header);long total=0;int index=0;
            while(true){byte[] block=readChunk(in);total+=block.length;if(total>MAX_ARCHIVE)throw new IOException("Backup exceeds the supported size");
                out.writeInt(block.length);out.write(chunk(Cipher.ENCRYPT_MODE,key,header,index++,block.length,block));if(block.length==0)break;
            }done=true;
        }finally{Arrays.fill(key,(byte)0);if(!done)target.delete();}
    }
    public static void decrypt(File source, File target, char[] password) throws Exception {
        boolean done=false;byte[] key=null;
        try(DataInputStream in=new DataInputStream(new FileInputStream(source));OutputStream out=new FileOutputStream(target)){
            byte[] header=new byte[HEADER_SIZE];in.readFully(header);key=key(header,password);long total=0;int index=0;
            while(true){int length=in.readInt();if(length<0||length>CHUNK||(total+=length)>MAX_ARCHIVE)throw new IOException("Invalid encrypted backup chunk");
                byte[] block=new byte[length+16];in.readFully(block);
                try{out.write(chunk(Cipher.DECRYPT_MODE,key,header,index++,length,block));}catch(GeneralSecurityException e){throw new IOException("Wrong password or damaged backup. Your garage was not changed.",e);}
                if(length==0){if(in.read()!=-1)throw new IOException("Unexpected data after encrypted backup");break;}
            }done=true;
        }finally{if(key!=null)Arrays.fill(key,(byte)0);if(!done)target.delete();}
    }
}
