package org.mlpi.pixelprobe.starlink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Fixed-length HTTP only; no outbound requests, proxying or vendor code. */
public final class LocalHttp {
    private LocalHttp() {}
    public static final class Error extends IOException {
        public final int status;
        Error(int status, String message) { super(message); this.status = status; }
    }
    public static final class Request {
        public final String method, target;
        public final Map<String, String> headers;
        public final byte[] body;
        public final boolean close;
        Request(String method, String target, Map<String,String> headers, byte[] body, boolean close) {
            this.method = method; this.target = target; this.headers = headers; this.body = body; this.close = close;
        }
    }
    public static final class Response {
        public final int status;
        public final byte[] wire;
        public final boolean close;
        Response(int status, String type, byte[] body, boolean close) {
            this.status = status; this.close = close;
            byte[] head = ("HTTP/1.1 " + status + " " + reason(status) + "\r\nContent-Type: " + type
                    + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff"
                    + "\r\nConnection: " + (close ? "close" : "keep-alive") + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            wire = new byte[head.length + body.length]; System.arraycopy(head,0,wire,0,head.length); System.arraycopy(body,0,wire,head.length,body.length);
        }
    }
    private static String reason(int status) {
        switch (status) { case 200: return "OK"; case 400: return "Bad Request"; case 403: return "Forbidden";
            case 404: return "Not Found"; case 405: return "Method Not Allowed"; case 409: return "Conflict";
            case 413: return "Content Too Large"; case 429: return "Too Many Requests";
            case 431: return "Request Header Fields Too Large"; case 507: return "Insufficient Storage"; default: return "Internal Server Error"; }
    }
    public static Response text(int code, String type, String body, boolean close) {
        return new Response(code,type,body.getBytes(StandardCharsets.UTF_8),close);
    }
    public static Request parse(byte[] raw) throws IOException {
        return read(new java.io.ByteArrayInputStream(raw));
    }
    public static Request read(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int state = 0;
        while (state != 4) {
            int c = in.read();
            if (c == -1) { if (bytes.size() == 0) return null; throw new Error(400,"Truncated header"); }
            bytes.write(c); if (bytes.size() > 8192) throw new Error(431,"Header limit");
            state = c == "\r\n\r\n".charAt(state) ? state+1 : c == 13 ? 1 : 0;
        }
        String[] lines = bytes.toString(StandardCharsets.ISO_8859_1.name()).split("\r\n");
        String[] first = lines[0].split(" ",-1);
        if (first.length != 3 || !first[0].matches("[A-Z]{1,16}") || first[1].length() > 2048
                || !first[1].matches("[!-~]+") || !(first[2].equals("HTTP/1.1") || first[2].equals("HTTP/1.0"))) throw new Error(400,"Invalid request line");
        Map<String,String> headers = new LinkedHashMap<>();
        for (int i=1;i<lines.length;i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0 || !lines[i].substring(0,colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw new Error(400,"Invalid header");
            String key=lines[i].substring(0,colon).toLowerCase(Locale.ROOT), value=lines[i].substring(colon+1).trim();
            if (!value.matches("[\\t -~]*") || headers.put(key,value) != null) throw new Error(400,"Ambiguous header");
        }
        if (headers.containsKey("transfer-encoding")) throw new Error(400,"Transfer encoding unsupported");
        int length=0;
        if (headers.containsKey("content-length")) {
            String value=headers.get("content-length");
            if (!value.matches("[0-9]{1,9}")) throw new Error(400,"Invalid body length");
            length=Integer.parseInt(value); if(length>4096) throw new Error(413,"Body limit");
        }
        byte[] body=new byte[length]; int pos=0;
        while(pos<length) {int n=in.read(body,pos,length-pos);if(n<0)throw new Error(400,"Truncated body");pos+=n;}
        String connection=headers.getOrDefault("connection", "");
        java.util.Set<String> options=new java.util.HashSet<>();
        for(String option:connection.split(","))options.add(option.trim().toLowerCase(Locale.ROOT));
        boolean close=options.contains("close") || (first[2].equals("HTTP/1.0") && !options.contains("keep-alive"));
        return new Request(first[0],first[1],headers,body,close);
    }
}
