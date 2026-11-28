package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Synthetic factory-address exchange through the actual Android JNI boundary. */
final class FactoryLinkChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int checksum(byte[] data){int sum=0;for(int i=0;i<data.length;i+=2)sum+=((data[i]&255)<<8)|(i+1<data.length?data[i+1]&255:0);while((sum>>>16)!=0)sum=(sum&65535)+(sum>>>16);return (~sum)&65535;}
    private static byte[] packet(int seq,int ack,int flags,byte[] payload){
        byte[] tcp=ByteBuffer.allocate(20+payload.length).order(ByteOrder.BIG_ENDIAN).putShort((short)39400).putShort((short)80).putInt(seq).putInt(ack).put((byte)0x50).put((byte)flags).putShort((short)8192).putInt(0).put(payload).array();
        byte[] pseudo=ByteBuffer.allocate(12+tcp.length).put(new byte[]{(byte)192,(byte)168,0,1,(byte)192,(byte)168,1,1,0,6}).putShort((short)tcp.length).put(tcp).array();
        ByteBuffer.wrap(tcp).putShort(16,(short)checksum(pseudo));
        byte[] ip=ByteBuffer.allocate(20+tcp.length).put(new byte[]{0x45,0}).putShort((short)(20+tcp.length)).putInt(0).put((byte)64).put((byte)6).putShort((short)0).put(new byte[]{(byte)192,(byte)168,0,1,(byte)192,(byte)168,1,1}).put(tcp).array();
        ByteBuffer.wrap(ip).putShort(10,(short)checksum(Arrays.copyOf(ip,20)));
        ByteArrayOutputStream wire=new ByteArrayOutputStream();wire.write(0xc0);for(byte b:ip){int value=b&255;if(value==0xc0){wire.write(0xdb);wire.write(0xdc);}else if(value==0xdb){wire.write(0xdb);wire.write(0xdd);}else wire.write(value);}wire.write(0xc0);return wire.toByteArray();
    }
    private static byte[] first(byte[] wire){ByteArrayOutputStream out=new ByteArrayOutputStream();boolean escaped=false;for(byte b:wire){int value=b&255;if(value==0xc0){if(out.size()>0)return out.toByteArray();continue;}if(escaped){out.write(value==0xdc?0xc0:0xdb);escaped=false;}else if(value==0xdb)escaped=true;else out.write(value);}throw new AssertionError("No factory response");}
    static void run(Context context)throws Exception{
        try(RustCore core=new RustCore(5901)){
            byte[] syn=first(core.feed(packet(100,0,2,new byte[0]),0).wire);
            check(Arrays.equals(Arrays.copyOfRange(syn,16,20),new byte[]{(byte)192,(byte)168,0,1}),"JNI response used bench IP instead of factory IP");
            check((syn[33]&255)==0x12,"Factory SYN not acknowledged");int server=ByteBuffer.wrap(syn).getInt(24)+1;
            core.feed(packet(101,server,16,new byte[0]),1);
            byte[] request="GET /version HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
            RustCore.Batch batch=core.feed(packet(101,server,24,request),2);check(batch.requests.size()==1,"Factory HTTP did not reach Android");
            RustCore.Request received=batch.requests.get(0);LocalHttp.Response response=StarlinkRuntime.get(context).router.handle(received.bytes);
            byte[] reply=first(core.respond(received.connection,response.wire,true,3).wire);
            check(Arrays.equals(Arrays.copyOfRange(reply,16,20),new byte[]{(byte)192,(byte)168,0,1}),"HTTP returned to wrong peer");
            int offset=20+((reply[32]&255)>>>4)*4;check(new String(reply,offset,reply.length-offset,StandardCharsets.UTF_8).startsWith("HTTP/1.1 200"),"Factory HTTP response missing");
        }
    }
}
