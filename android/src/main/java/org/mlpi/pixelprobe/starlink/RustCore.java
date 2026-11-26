package org.mlpi.pixelprobe.starlink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Android owns Bluetooth; the Rust library owns bounded SLIP/IP/TCP state. */
public final class RustCore implements AutoCloseable {
    static { System.loadLibrary("subarubridge_core"); }
    private long handle;
    public static final class Request {
        public final long connection;
        public final byte[] bytes;
        Request(long connection, byte[] bytes) { this.connection = connection; this.bytes = bytes; }
    }
    public static final class Batch {
        public final byte[] wire;
        public final List<Request> requests;
        Batch(byte[] wire, List<Request> requests) { this.wire = wire; this.requests = requests; }
    }
    public RustCore(long seed) throws IOException {
        handle = nativeCreate(seed);
        if (handle == 0) throw new IOException("Transport capacity unavailable");
    }
    private Batch decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 6 || bytes.length > 1024 * 1024) throw new IOException("Invalid core batch");
        ByteBuffer data = ByteBuffer.wrap(bytes);
        int length = data.getInt();
        if (length < 0 || length > data.remaining() - 2) throw new IOException("Invalid wire length");
        byte[] wire = new byte[length]; data.get(wire);
        int count = data.getShort() & 65535;
        if (count > 16) throw new IOException("Too many core requests");
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (data.remaining() < 12) throw new IOException("Truncated core request");
            long id = data.getLong(); length = data.getInt();
            if (length < 0 || length > 12288 || length > data.remaining()) throw new IOException("Invalid request length");
            byte[] request = new byte[length]; data.get(request); requests.add(new Request(id, request));
        }
        if (data.hasRemaining()) throw new IOException("Unexpected core bytes");
        return new Batch(wire, requests);
    }
    private void requireOpen() throws IOException { if (handle == 0) throw new IOException("Transport closed"); }
    public synchronized Batch feed(byte[] bytes, long now) throws IOException { requireOpen(); return decode(nativeFeed(handle, bytes, now)); }
    public synchronized Batch respond(long connection, byte[] bytes, boolean close, long now) throws IOException {
        requireOpen(); return decode(nativeRespond(handle, connection, bytes, close, now));
    }
    public synchronized Batch tick(long now) throws IOException { requireOpen(); return decode(nativeTick(handle, now)); }
    @Override public synchronized void close() { if (handle != 0) { nativeDestroy(handle); handle = 0; } }
    private static native long nativeCreate(long seed);
    private static native void nativeDestroy(long handle);
    private static native byte[] nativeFeed(long handle, byte[] bytes, long now);
    private static native byte[] nativeRespond(long handle, long connection, byte[] bytes, boolean close, long now);
    private static native byte[] nativeTick(long handle, long now);
}
