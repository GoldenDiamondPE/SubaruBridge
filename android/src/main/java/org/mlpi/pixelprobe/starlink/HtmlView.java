package org.mlpi.pixelprobe.starlink;

/** Small server-rendered pages remain navigable without any browser JavaScript. */
public final class HtmlView {
    private HtmlView() {}
    public static String escape(Object value) {
        return String.valueOf(value == null ? "" : value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'\">"
                + "<title>" + escape(title) + "</title><style>body{margin:0;padding:12px;background:#10202b;color:#fff;font:20px sans-serif}h1{font-size:26px;margin:4px 0}a,button{display:inline-block;background:#70e1bf;color:#10202b;padding:12px;margin:4px;border:0;font:20px sans-serif;text-decoration:none}form{display:inline}p{margin:10px 0}.small{font-size:15px;color:#c1d6df}</style></head><body><h1>"
                + escape(title) + "</h1><p class=\"small\">SubaruBridge " + DiagnosticEngine.BUILD + " | parked test</p>" + body
                + "<p><a href=\"/smartaccess/launcher/subarubridge-demo.html\">Connection lab</a></p></body></html>";
    }
    public static String hidden(String name, Object value) {
        return "<input type=\"hidden\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\">";
    }
}
