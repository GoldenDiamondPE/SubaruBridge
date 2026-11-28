package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.graphics.Color;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Phone and head-unit colors come from the same editable dashboard.json. */
final class PhonePalette {
    final String id, brand;
    final int background, surface, raised, text, muted, accent, onAccent, border,controlAccent;

    PhonePalette(Context context) {
        JSONObject theme = new JSONObject();
        String chosen = "midnight", title = "SubaruBridge";
        try (InputStream in = context.getAssets().open("subarubridge-ui/dashboard.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] block = new byte[2048]; int count;
            while ((count = in.read(block)) != -1) {
                if (bytes.size() + count > 16384) throw new java.io.IOException("Palette limit");
                bytes.write(block, 0, count);
            }
            JSONObject config = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            title = config.getString("brand");
            chosen = context.getSharedPreferences("subarubridge-car-ui", Context.MODE_PRIVATE)
                    .getString("theme", config.getString("defaultTheme"));
            JSONObject themes = config.getJSONObject("themes");
            if (!themes.has(chosen)) chosen = config.getString("defaultTheme");
            theme = themes.getJSONObject(chosen);
        } catch (Exception ignored) { /* A readable fallback still allows diagnostics. */ }
        id = chosen; brand = title;
        background = color(theme, "background", "#101114");
        surface = color(theme, "surface", "#1c1c1e");
        raised = color(theme, "surfaceRaised", "#2c2c2e");
        text = color(theme, "text", "#f5f5f7");
        muted = color(theme, "muted", "#98989f");
        accent = color(theme, "accent", "#0a84ff");
        onAccent = color(theme, "onAccent", "#ffffff");
        border = color(theme, "border", "#38383a");
        controlAccent=readableFill(accent,onAccent);
    }

    void applyWindow(android.app.Activity activity){
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        activity.getWindow().getDecorView().post(()->{
            android.view.WindowInsetsController controller=activity.getWindow().getInsetsController();
            if(controller!=null)controller.setSystemBarsAppearance(id.equals("daylight")?android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
    }
    private static double luminance(int color){double total=0;int[] channels={Color.red(color),Color.green(color),Color.blue(color)};double[] weights={0.2126,0.7152,0.0722};for(int i=0;i<3;i++){double v=channels[i]/255.0;total+=weights[i]*(v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4));}return total;}
    private static int readableFill(int fill,int foreground){
        double text=luminance(foreground);for(int i=0;i<36;i++){
            double background=luminance(fill);if((Math.max(text,background)+0.05)/(Math.min(text,background)+0.05)>=4.5)return fill;
            int target=text>0.179?0:255;fill=Color.rgb(Math.round(Color.red(fill)*0.88f+target*0.12f),Math.round(Color.green(fill)*0.88f+target*0.12f),Math.round(Color.blue(fill)*0.88f+target*0.12f));
        }return fill;
    }

    private int color(JSONObject values, String key, String fallback) {
        try { return Color.parseColor(values.optString(key, fallback)); }
        catch (IllegalArgumentException ignored) { return Color.parseColor(fallback); }
    }
}
