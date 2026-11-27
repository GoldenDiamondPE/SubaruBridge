package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Server-rendered UI: editable assets own the presentation; handlers own behavior. */
public final class CarUi {
    private static final String PREFIX = "/subarubridge-diag/";
    private static final Pattern TOKEN = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*)\\}\\}");
    private static final Map<String, String> CACHE = new HashMap<String, String>();
    private final Context context;
    private final Properties config = new Properties();
    private final SharedPreferences preferences;
    private final String theme, layout;

    public CarUi(Context context) throws IOException {
        this.context = context;
        config.load(new StringReader(asset("ui.properties")));
        preferences = context.getSharedPreferences("subarubridge-car-ui", Context.MODE_PRIVATE);
        String chosen = preferences.getString("theme", config.getProperty("defaultTheme"));
        theme = validTheme(chosen) ? chosen : config.getProperty("defaultTheme");
        chosen = preferences.getString("layout", config.getProperty("defaultLayout"));
        layout = validLayout(chosen) ? chosen : config.getProperty("defaultLayout");
    }
    private String p(String key) throws IOException {
        String value = config.getProperty(key);
        if (value == null) throw new IOException("Missing UI configuration");
        return value;
    }
    private String asset(String file) throws IOException {
        synchronized (CACHE) {
            String value = CACHE.get(file);
            if (value != null) return value;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = context.getAssets().open("subarubridge-ui/" + file)) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > 65536) throw new IOException("UI asset limit");
                    bytes.write(buffer, 0, count);
                }
            }
            value = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            CACHE.put(file, value); return value;
        }
    }
    private String template(String name, Map<String, String> values) throws IOException {
        Matcher matcher = TOKEN.matcher(asset("templates/" + name + ".html"));
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) throw new IOException("Missing UI template value");
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result); return result.toString();
    }
    private String icon(String id) throws IOException { return asset("icons/" + id + ".svg"); }
    private String url(String route, String run) {
        return PREFIX + route + (run == null ? "" : "?run=" + run);
    }
    private String stepUrl(int screen, String run) {
        return PREFIX + "basic?" + (run == null ? "" : "run=" + run + "&") + "screen=" + screen;
    }
    private Map<String, String> urls(String run) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("homeUrl", HtmlView.escape(url("dashboard", run)));
        values.put("settingsUrl", HtmlView.escape(url("settings", run)));
        values.put("developerUrl", HtmlView.escape(url("developer", run)));
        values.put("startUrl", HtmlView.escape(stepUrl(1, run))); return values;
    }
    public boolean isDefault() {
        return theme.equals(config.getProperty("defaultTheme")) && layout.equals(config.getProperty("defaultLayout"));
    }
    public boolean validTheme(String value) {
        if (value == null) return false;
        for (String id : config.getProperty("theme.ids", "").split(",")) if (id.equals(value)) return true;
        return false;
    }
    public static boolean validLayout(String value) { return "grid".equals(value) || "list".equals(value); }
    public boolean save(String newTheme, String newLayout) {
        if (!validTheme(newTheme) || !validLayout(newLayout)) return false;
        return preferences.edit().putString("theme", newTheme).putString("layout", newLayout).commit();
    }
    private String frame(String title, String body, String active, String run) throws IOException {
        Map<String, String> values = urls(run);
        StringBuilder dock = new StringBuilder();
        String[] ids = {"home", "test", "settings"}, symbols = {"home", "results", "settings"}, labels = {"Home", "Check", "Appearance"};
        String[] targets = {url("dashboard", run), stepUrl(1, run), url("settings", run)};
        for (int i = 0; i < ids.length; i++) dock.append("<a id=\"dock-").append(ids[i]).append("\" class=\"dock-link")
                .append(ids[i].equals(active) ? " current" : "").append("\" href=\"").append(HtmlView.escape(targets[i])).append("\">")
                .append(icon(symbols[i])).append("<span>").append(labels[i]).append("</span></a>");
        values.put("pageTitle", HtmlView.escape(title)); values.put("brand", HtmlView.escape(p("brand")));
        values.put("css", asset("theme-" + theme + ".css")); values.put("theme", theme); values.put("layout", layout);
        values.put("dock", dock.toString()); values.put("devCurrent", "developer".equals(active) ? "current" : "");
        values.put("toolsIcon", icon("tools")); values.put("body", body);
        return template("shell", values);
    }
    public String home(String run) throws IOException {
        Map<String, String> values = urls(run);
        StringBuilder tiles = new StringBuilder();
        int count = Integer.parseInt(p("app.count"));
        java.util.List<Integer> order = new java.util.ArrayList<>();
        for (int i=0;i<count;i++) order.add(i);
        if (context.getSharedPreferences("subarubridge-apps",Context.MODE_PRIVATE).getBoolean("navigationFirst",false)) {
            for(int i=0;i<count;i++)if(p("app."+i+".id").equals("navigation")){order.remove(Integer.valueOf(i));order.add(0,i);break;}
        }
        for (int i : order) {
            String key = "app." + i + ".";
            Map<String, String> tile = new LinkedHashMap<String, String>();
            for (String field : new String[] {"id", "tone", "title", "subtitle"}) tile.put(field, HtmlView.escape(p(key + field)));
            tile.put("url", PREFIX + "apps/" + p(key + "id"));
            tile.put("icon", icon(p(key + "icon"))); tiles.append(template("tile", tile));
        }
        values.put("tiles", tiles.toString()); values.put("arrowIcon", icon("arrow"));
        android.os.BatteryManager battery=context.getSystemService(android.os.BatteryManager.class);
        int capacity=battery==null?-1:battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        values.put("clock",new SimpleDateFormat("h:mm a",Locale.getDefault()).format(new Date()));
        values.put("battery",capacity<0 || capacity>100 ? "Unavailable" : capacity+"%");
        for (String field : new String[] {"eyebrow", "homeTitle", "homeSubtitle"}) values.put(field, HtmlView.escape(p(field)));
        return frame("Home", template("home", values), "home", run);
    }
    public String app(String title,String subtitle,String content,String id) throws IOException {
        Map<String,String> values=urls(null);values.put("title",HtmlView.escape(title));values.put("subtitle",HtmlView.escape(subtitle));
        values.put("content",content);values.put("refreshUrl",PREFIX+"apps/"+id);
        return frame(title,template("app",values),"home",null);
    }
    public String basic(String run, int screen, Map<String, Object> state, Map<String, Object> phone, String form) throws IOException {
        Map<String, String> values = urls(run);
        String[] labels = {"Display", "Touch", "Phone", "Results"};
        StringBuilder steps = new StringBuilder();
        for (int i = 1; i <= 4; i++) steps.append("<a id=\"basic-nav").append(i).append("\" class=\"step-link")
                .append(i == screen ? " current" : "").append("\" href=\"").append(HtmlView.escape(stepUrl(i, run))).append("\">")
                .append(i).append(" ").append(labels[i-1]).append("</a>");
        values.put("steps", steps.toString()); values.put("title", labels[screen-1]); values.put("step", "" + screen); values.put("run", HtmlView.escape(run));
        values.put("touches", "" + state.get("touches")); values.put("form", form); values.put("nextUrl", HtmlView.escape(stepUrl(2, run)));
        values.put("battery", phone == null || ((Number) phone.get("batteryPercent")).intValue() < 0 ? "?" : "" + phone.get("batteryPercent"));
        values.put("clock", new SimpleDateFormat("h:mm", Locale.getDefault()).format(new Date()));
        values.put("uptime", "" + SystemClock.elapsedRealtime()/60000);
        values.put("screens", "" + state.get("screensSeen")); values.put("reads", "" + state.get("dataChecks"));
        boolean saved = Boolean.TRUE.equals(state.get("summarySaved"));
        boolean complete = ((Number) state.get("screensSeen")).intValue() == 4 && ((Number) state.get("touches")).intValue() >= 3 && ((Number) state.get("dataChecks")).intValue() >= 1;
        values.put("saved", "" + saved); values.put("savedLabel", saved ? "Yes" : "No");
        values.put("resultTitle", saved && complete ? "Check complete" : saved ? "Saved. More checks remaining" : "Your results");
        values.put("resultSubtitle", saved ? "Your result is saved on your phone." : "Save your result when you are ready.");
        values.put("panel", template(new String[] {"display", "touch", "phone", "results"}[screen-1], values));
        return frame(labels[screen-1], template("basic", values), "test", run);
    }
    private String choice(String run, String id, String newTheme, String newLayout, String label, String swatch, boolean selected) {
        return "<form method=\"post\" action=\"" + HtmlView.escape(url("settings", run)) + "\">"
                + HtmlView.hidden("theme", newTheme) + HtmlView.hidden("layout", newLayout)
                + "<button id=\"" + id + "\" type=\"submit\" class=\"choice" + (selected ? " selected" : "") + "\" aria-pressed=\"" + selected + "\">"
                + (swatch == null ? "" : "<span class=\"swatch\" style=\"background:" + HtmlView.escape(swatch) + "\"></span>")
                + HtmlView.escape(label) + "<span class=\"choice-mark\">" + (selected ? "&#10003;" : "") + "</span></button></form>";
    }
    public String settings(String run, String notice) throws IOException {
        Map<String, String> values = urls(run);
        StringBuilder themes = new StringBuilder(), layouts = new StringBuilder();
        for (String id : p("theme.ids").split(",")) themes.append(choice(run, "theme-" + id, id, layout, p("theme." + id + ".label"), p("theme." + id + ".swatch"), id.equals(theme)));
        layouts.append(choice(run, "layout-grid", theme, "grid", "App grid", null, "grid".equals(layout)));
        layouts.append(choice(run, "layout-list", theme, "list", "Simple list", null, "list".equals(layout)));
        values.put("themes", themes.toString()); values.put("layouts", layouts.toString()); values.put("notice", HtmlView.escape(notice));
        return frame("Appearance", template("settings", values), "settings", run);
    }
    public String developer(String run, String state) throws IOException {
        Map<String, String> values = urls(run); values.put("state", HtmlView.escape(state));
        return frame("Tools", template("developer", values), "developer", run);
    }
}
