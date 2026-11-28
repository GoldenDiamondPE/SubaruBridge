package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.mlpi.pixelprobe.R;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;

/** Everyday connection controls and the same editable dashboard as the car. */
public final class StarlinkActivity extends Activity {
    private static final int NEARBY = 52, ENABLE_BT = 53;
    private static final String HOME_PATH = "/subarubridge-diag/dashboard";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private PhonePalette palette;
    private LinearLayout root, pageHost;
    private TextView statusTitle, statusDetail, details;
    private View statusDot;
    private Button primary, pauseButton, exportButton;
    private WebView preview;
    private int tab;
    private String previewPath = HOME_PATH, localIssue;
    private boolean showConnectionDetails;
    private boolean coreReady, resumed, permissionInFlight, enableInFlight;
    private boolean paused, bluetoothPrompted, startRequested, exporting, receiverRegistered, manualSetup, stoppedSinceResume;
    private long lastStart;

    private final BroadcastReceiver bluetoothState = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                // Query the adapter itself; do not trust broadcast extras.
                if (need() == StartupPolicy.Need.READY) {
                    startRequested = false;
                    handler.postDelayed(() -> tryAutomatic(), 200);
                }
                refreshStatus();
            }
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            PhonePalette chosen = new PhonePalette(StarlinkActivity.this);
            if (!chosen.id.equals(palette.id)) {
                palette = chosen;
                buildUi();
            }
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        preferences = getSharedPreferences("subarubridge-connection", MODE_PRIVATE);
        palette = new PhonePalette(this);
        if (saved != null) {
            tab = saved.getInt("tab");
            previewPath = saved.getString("previewPath", HOME_PATH);
            paused = saved.getBoolean("paused");
            bluetoothPrompted = saved.getBoolean("bluetoothPrompted");
            permissionInFlight = saved.getBoolean("permissionInFlight");
            enableInFlight = saved.getBoolean("enableInFlight");
            manualSetup = saved.getBoolean("manualSetup");
        }
        paused=StarlinkService.pausedByUser(this);
        openRequestedScreen();
        try (RustCore ignored = new RustCore(1)) { coreReady = true; }
        catch (IOException | LinkageError unavailable) { localIssue = "App components unavailable. Reinstall this build."; }
        try { StarlinkRuntime.get(this).startPreview(); }
        catch (IOException unavailable) { localIssue = "Phone preview unavailable. The car connection can still run. Try reopening the app."; }
        buildUi();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable shape(int color, int radius, boolean border) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(color); value.setCornerRadius(dp(radius));
        if (border) value.setStroke(dp(1), palette.border);
        return value;
    }
    private LinearLayout column() {
        LinearLayout value = new LinearLayout(this); value.setOrientation(LinearLayout.VERTICAL); return value;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(color);
        label.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        label.setIncludeFontPadding(false); label.setLineSpacing(dp(3), 1); return label;
    }
    private void gap(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void add(LinearLayout parent, View child) { parent.addView(child, new LinearLayout.LayoutParams(-1, -2)); }
    private void heading(LinearLayout parent, String title, String subtitle) {
        add(parent, text(title, 28, palette.text, true)); gap(parent, 7);
        if (!subtitle.isEmpty()) { add(parent, text(subtitle, 15, palette.muted, false)); gap(parent, 18); }
    }
    private void section(LinearLayout parent, String title) {
        gap(parent, 20); TextView label = text(title, 14, palette.muted, true);
        label.setPadding(dp(4), 0, 0, dp(9)); add(parent, label);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout value = column(); value.setBackground(shape(palette.surface, 16, false)); add(parent, value); return value;
    }
    private Button button(String label, int id, boolean filled, Runnable action) {
        Button value = new Button(this); value.setId(id); value.setText(label); value.setTextSize(16); value.setAllCaps(false);
        value.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        value.setTextColor(filled ? palette.onAccent : palette.accent);
        value.setBackground(shape(filled ? palette.controlAccent : palette.surface, 14, false));
        value.setStateListAnimator(null); value.setElevation(0); value.setMinimumHeight(dp(50));
        value.setPadding(dp(18), dp(13), dp(18), dp(13)); value.setOnClickListener(v -> action.run()); return value;
    }
    private void row(LinearLayout parent, String title, String subtitle, String icon, int id, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14)); row.setMinimumHeight(dp(68)); row.setId(id);
        Icon symbol = new Icon(icon, palette.accent); row.addView(symbol, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout words = column(); words.setPadding(dp(15), 0, dp(12), 0);
        add(words, text(title, 16, palette.text, false));
        if (!subtitle.isEmpty()) { gap(words, 4); add(words, text(subtitle, 14, palette.muted, false)); }
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 27, palette.muted, false); row.addView(arrow);
        row.setClickable(true); row.setFocusable(true); row.setContentDescription(title + ". " + subtitle);
        row.setOnClickListener(v -> action.run()); add(parent, row);
    }
    private void divider(LinearLayout parent) {
        View line = new View(this); line.setBackgroundColor(palette.border);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1)); params.setMargins(dp(57), 0, dp(16), 0); parent.addView(line, params);
    }

    private void buildUi() {
        if (preview != null) { preview.stopLoading(); preview.destroy(); preview = null; }
        statusTitle = null; statusDetail = null; statusDot = null; primary = null; details = null; pauseButton = null; exportButton = null;
        root = column(); root.setBackgroundColor(palette.background);
        palette.applyWindow(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        int appearance = palette.id.equals("daylight") ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
        pageHost = column(); root.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this); navigation.setBackgroundColor(palette.surface);
        String[] labels = {"Home", "Dashboard", "Tools"}, icons = {"home", "grid", "tools"};
        int[] ids = {R.id.phone_home, R.id.phone_dashboard, R.id.phone_tools};
        for (int i = 0; i < 3; i++) {
            final int selected = i; int color = tab == i ? palette.accent : palette.muted;
            LinearLayout item = column(); item.setId(ids[i]); item.setGravity(Gravity.CENTER); item.setPadding(0, dp(9), 0, dp(9));
            item.addView(new Icon(icons[i], color), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView title = text(labels[i], 12, color, tab == i); title.setGravity(Gravity.CENTER); title.setPadding(0, dp(5), 0, 0); item.addView(title);
            item.setContentDescription(labels[i] + (tab == i ? ", selected" : "")); item.setClickable(true); item.setFocusable(true);
            item.setOnClickListener(v -> select(selected)); navigation.addView(item, new LinearLayout.LayoutParams(0, dp(66), 1));
        }
        add(root, navigation); setContentView(root); root.requestApplyInsets();
        root.post(() -> {
            WindowInsetsController controller = root.getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(appearance,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
        if (tab == 1) dashboard(); else {
            ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
            LinearLayout page = column(); page.setPadding(dp(20), dp(18), dp(20), dp(24)); scroll.addView(page);
            add(pageHost, scroll); if (tab == 0) home(page); else tools(page);
        }
        refreshStatus();
    }
    private void select(int selected) { if (tab != selected) { tab = selected; buildUi(); } }
    private void showDashboard(String path) {
        previewPath = path; if (tab == 1 && preview != null) preview.loadUrl(local(path)); else { tab = 1; buildUi(); }
    }
    private void home(LinearLayout page) {
        heading(page, palette.brand, "Your car, connected. Your records, on this phone.");
        LinearLayout connection = card(page); connection.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout titleLine = new LinearLayout(this); titleLine.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this); titleLine.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusTitle = text("Getting ready…", 20, palette.text, true); statusTitle.setPadding(dp(10), 0, 0, 0);
        statusTitle.setId(R.id.phone_connection_status); statusTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        titleLine.addView(statusTitle, new LinearLayout.LayoutParams(0, -2, 1)); add(connection, titleLine);
        gap(connection, 12); statusDetail = text("", 15, palette.muted, false); statusDetail.setId(R.id.phone_connection_detail); add(connection, statusDetail);
        gap(connection, 20); primary = button("Open dashboard", R.id.phone_primary_action, true, this::primaryAction); add(connection, primary);
        section(page, "Your workspace");
        LinearLayout shortcuts = card(page);
        row(shortcuts, "Garage & maintenance", "Mileage, service history, fuel and costs", "home", R.id.phone_garage, () -> startActivity(new Intent(this, GarageActivity.class)));
        divider(shortcuts);
        row(shortcuts, "Apps & integrations", "Music, directions, calls and notifications", "grid", View.NO_ID, () -> startActivity(new Intent(this, IntegrationsActivity.class)));
        divider(shortcuts); row(shortcuts, "Appearance", "A shared look for phone and car", "appearance", View.NO_ID, () -> showDashboard("/subarubridge-diag/settings"));
        divider(shortcuts); row(shortcuts, "Record & diagnose", "Connection checks and private recordings", "tools", R.id.phone_recorder, () -> startActivity(new Intent(this, DeveloperActivity.class)));
        if(PhoneIntegrations.get(this).pending()!=null){gap(page,12);add(page,button("Review phone action",View.NO_ID,false,()->startActivity(new Intent(this,PhoneActionActivity.class))));}
        section(page,"Connect to your Subaru");
        add(page,text("1. Pair this phone in Bluetooth settings.\n2. Leave SubaruBridge ready.\n3. Open Apps → STARLINK on the parked car.",15,palette.muted,false));
        gap(page,12);add(page,button("Bluetooth settings",R.id.phone_pair,false,this::pair));
        gap(page,12);add(page,text("Everything runs on this phone. No computer or original STARLINK app is needed.",14,palette.muted,false));
        gap(page, 18); add(page, text("Development preview · in-car validation pending", 12, palette.muted, false));
    }
    private void tools(LinearLayout page) {
        heading(page, "Tools", "Connection settings, checks and private diagnostics.");
        LinearLayout controls = card(page);
        LinearLayout autoRow = new LinearLayout(this); autoRow.setGravity(Gravity.CENTER_VERTICAL); autoRow.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout words = column(); add(words, text("Connect on open", 16, palette.text, false)); gap(words, 4);
        add(words, text("Prepare Bluetooth when you open the app", 13, palette.muted, false)); autoRow.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch automatic = new Switch(this); automatic.setId(R.id.phone_auto_connect); automatic.setContentDescription("Connect on open"); automatic.setChecked(autoEnabled());
        automatic.setThumbTintList(ColorStateList.valueOf(palette.accent));
        automatic.setOnCheckedChangeListener((v, checked) -> {
            preferences.edit().putBoolean("automatic", checked).apply();
            paused = !checked; StarlinkService.pausedByUser(this,paused); startRequested = false;
            if (checked) begin(true); else stopService(new Intent(this, StarlinkService.class));
            refreshStatus();
        });
        autoRow.addView(automatic); add(controls, autoRow);
        LinearLayout pauseArea = column(); pauseArea.setPadding(dp(16), 0, dp(16), dp(16));
        pauseButton = button("Pause connection", R.id.phone_pause, false, () -> {
            if (activeOrStarting()) { paused = true; StarlinkService.pausedByUser(this,true); startRequested = false; manualSetup = false; stopService(new Intent(this, StarlinkService.class)); }
            else { paused = false; startRequested = false; begin(true); }
            refreshStatus();
        }); add(pauseArea, pauseButton); add(controls, pauseArea);
        section(page, "Diagnostics");
        LinearLayout diagnostics = card(page);
        row(diagnostics, "Record & diagnose", "Record a car test and inspect it on this phone", "tools", R.id.phone_developer, () -> startActivity(new Intent(this, DeveloperActivity.class)));
        divider(diagnostics);
        row(diagnostics, "App integrations", "Permissions, favorites and dashboard order", "grid", View.NO_ID, () -> startActivity(new Intent(this, IntegrationsActivity.class)));
        divider(diagnostics);
        row(diagnostics, "Run connection check", "Four checks, confirmed on your phone", "check", View.NO_ID, () -> showDashboard("/subarubridge-diag/basic?screen=1"));
        divider(diagnostics); row(diagnostics, "Head-unit tools", "Test state and editable UI files", "grid", View.NO_ID, () -> showDashboard("/subarubridge-diag/developer"));
        divider(diagnostics); row(diagnostics, "Bluetooth settings", "Pairing and system connection controls", "link", View.NO_ID, this::pair);
        divider(diagnostics); row(diagnostics,"Review phone action","Confirm a prepared call, direction or reply","phone",View.NO_ID,()->startActivity(new Intent(this,PhoneActionActivity.class)));
        gap(page, 16); exportButton = button("Export private report", R.id.phone_export, true, this::exportReport); add(page, exportButton);
        gap(page, 9); add(page, text("Saved in Downloads. Reports contain app diagnostics; review them before sharing.", 13, palette.muted, false));
        section(page, "Connection details");
        Button expand=button(showConnectionDetails?"Hide technical details":"Show technical details",View.NO_ID,false,()->{showConnectionDetails=!showConnectionDetails;buildUi();});add(page,expand);gap(page,8);
        LinearLayout info = card(page); info.setPadding(dp(16), dp(16), dp(16), dp(16));
        info.setVisibility(showConnectionDetails?View.VISIBLE:View.GONE);
        details = text("", 13, palette.muted, false); details.setId(R.id.phone_diagnostics); details.setTypeface(Typeface.MONOSPACE); details.setTextIsSelectable(true); add(info, details);
        gap(page, 16); add(page, text("SubaruBridge " + DiagnosticEngine.BUILD + " · original local services", 12, palette.muted, false));
    }
    private void dashboard() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(dp(20), dp(12), dp(16), dp(12));
        LinearLayout words = column(); add(words, text("Dashboard", 22, palette.text, true)); gap(words, 4);
        add(words, text("The same screens as your head unit", 12, palette.muted, false)); bar.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Button reload = button("Reload", View.NO_ID, false, () -> preview.reload()); bar.addView(reload); add(pageHost, bar);
        preview = new WebView(this); preview.setId(R.id.phone_preview); preview.setBackgroundColor(palette.background);
        preview.getSettings().setJavaScriptEnabled(false); preview.getSettings().setAllowFileAccess(false);
        preview.getSettings().setAllowContentAccess(false); preview.getSettings().setDomStorageEnabled(false);
        preview.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !allowed(request.getUrl()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return allowed(request.getUrl()) ? null : new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (allowed(Uri.parse(url))) { Uri uri = Uri.parse(url); previewPath = uri.getEncodedPath() + (uri.getEncodedQuery() == null ? "" : "?" + uri.getEncodedQuery()); }
            }
        });
        pageHost.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
        WebView expected=preview;
        CookieManager.getInstance().setCookie("http://127.0.0.1:"+StarlinkRuntime.get(this).previewPort(),
                StarlinkRuntime.get(this).previewCookie(), accepted -> { if(preview==expected&&!isFinishing())expected.loadUrl(local(previewPath)); });
    }

    private String local(String path) { return "http://127.0.0.1:" + StarlinkRuntime.get(this).previewPort() + (path.startsWith("/subarubridge-diag/") ? path : HOME_PATH); }
    private boolean allowed(Uri uri) { return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == StarlinkRuntime.get(this).previewPort() && uri.getUserInfo() == null; }
    private BluetoothAdapter adapter() { BluetoothManager manager = getSystemService(BluetoothManager.class); return manager == null ? null : manager.getAdapter(); }
    private boolean hasNearby() { return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED; }
    private StartupPolicy.Need need() {
        if (!coreReady) return StartupPolicy.Need.CORE_UNAVAILABLE;
        BluetoothAdapter value = adapter(); if (value == null) return StartupPolicy.Need.NO_BLUETOOTH;
        if (!hasNearby()) return StartupPolicy.Need.NEARBY_PERMISSION;
        try { return value.isEnabled() ? StartupPolicy.Need.READY : StartupPolicy.Need.BLUETOOTH_OFF; }
        catch (SecurityException denied) { return StartupPolicy.Need.NEARBY_PERMISSION; }
    }
    private boolean autoEnabled() { return preferences.getBoolean("automatic", true); }
    private boolean activeOrStarting() { return StarlinkService.running() || (startRequested && SystemClock.elapsedRealtime() - lastStart < 3000); }
    private void tryAutomatic() {
        if (!resumed || isFinishing()) return;
        paused=StarlinkService.pausedByUser(this);
        StartupPolicy.Need requirement = need();
        boolean prompted = requirement == StartupPolicy.Need.NEARBY_PERMISSION ? preferences.getBoolean("askedNearby", false) : bluetoothPrompted;
        StartupPolicy.Action action = StartupPolicy.automatic(requirement, autoEnabled(), paused, prompted,
                permissionInFlight || enableInFlight || startRequested, StarlinkService.running());
        if (action != StartupPolicy.Action.NONE) begin(false);
    }
    private void begin(boolean explicit) {
        if (permissionInFlight || enableInFlight) return;
        if (explicit) {manualSetup = true; paused=false; StarlinkService.pausedByUser(this,false);}
        StartupPolicy.Need requirement = need();
        if (requirement == StartupPolicy.Need.NEARBY_PERMISSION) {
            boolean asked = preferences.getBoolean("askedNearby", false);
            if (!explicit && asked) return;
            if (explicit && asked && !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); return;
            }
            ArrayList<String> permissions = new ArrayList<>();
            if(Build.VERSION.SDK_INT>=31)permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            if (Build.VERSION.SDK_INT >= 33 && !preferences.getBoolean("askedNotifications", false)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS); preferences.edit().putBoolean("askedNotifications", true).apply();
            }
            preferences.edit().putBoolean("askedNearby", true).apply(); permissionInFlight = true;
            requestPermissions(permissions.toArray(new String[0]), NEARBY); return;
        }
        if (requirement == StartupPolicy.Need.BLUETOOTH_OFF) {
            if (!explicit && bluetoothPrompted) return;
            bluetoothPrompted = true; enableInFlight = true;
            try { startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BT); }
            catch (SecurityException denied) { enableInFlight=false;localIssue="Allow Nearby devices in Android settings, then try again."; }
            catch (RuntimeException unavailable) { enableInFlight = false; localIssue = "Open Bluetooth settings to turn Bluetooth on."; }
            return;
        }
        if (requirement != StartupPolicy.Need.READY || activeOrStarting()) { if (requirement == StartupPolicy.Need.READY) manualSetup = false; return; }
        if (!explicit && (startRequested || paused || !autoEnabled())) return;
        paused = false; manualSetup = false; startRequested = true; lastStart = SystemClock.elapsedRealtime(); localIssue = null;
        try { startForegroundService(new Intent(this, StarlinkService.class)); }
        catch (RuntimeException unavailable) { startRequested = false; localIssue = "Connection could not start. Keep the app open and try again."; }
        refreshStatus();
    }
    private void primaryAction() {
        StartupPolicy.Need requirement = need();
        if (requirement != StartupPolicy.Need.READY || !StarlinkService.running()) {
            paused = false; startRequested = false; begin(true); refreshStatus();
        } else showDashboard(HOME_PATH);
    }
    private void pair() { try { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (RuntimeException unavailable) { localIssue = "Bluetooth settings are unavailable on this device."; refreshStatus(); } }
    private void setText(TextView view, String value) { if (view != null && !value.contentEquals(view.getText())) view.setText(value); }
    private void refreshStatus() {
        if (palette == null) return;
        paused=StarlinkService.pausedByUser(this);
        StartupPolicy.Need requirement = need();
        String title, subtitle, action; boolean active = StarlinkService.running(); String raw = StarlinkService.state();
        int dot = palette.muted;
        if (requirement == StartupPolicy.Need.CORE_UNAVAILABLE) { title = "App needs attention"; subtitle = "Reinstall this build to restore its connection components."; action = "View tools"; }
        else if (requirement == StartupPolicy.Need.NO_BLUETOOTH) { title = "Preview available"; subtitle = "This device has no Bluetooth adapter. You can still explore the dashboard and tools."; action = "Open dashboard"; }
        else if (requirement == StartupPolicy.Need.NEARBY_PERMISSION) { title = "Allow Nearby devices"; subtitle = "SubaruBridge needs Bluetooth permission to connect to your paired Subaru."; action = "Continue setup"; }
        else if (requirement == StartupPolicy.Need.BLUETOOTH_OFF) { title = "Turn on Bluetooth"; subtitle = "Your dashboard will be ready as soon as Bluetooth is on."; action = "Turn on Bluetooth"; }
        else if (active && raw.startsWith("Active:")) { title="STARLINK responding"; subtitle="Requests are arriving from the paired Bluetooth link. Check display and touch on the parked car."; action="Open dashboard"; dot=0xff30b86c; }
        else if (active && raw.startsWith("Waiting:")) { title="Waiting for Bluetooth"; subtitle="The connection service will resume when Bluetooth is on."; action="Turn on Bluetooth"; }
        else if (active && raw.startsWith("Retrying:")) { title="Reconnecting…"; subtitle="The listener is recovering automatically. Close any other STARLINK listener apps."; action="Open dashboard"; dot=palette.accent; }
        else if (active && raw.startsWith("Connected:")) { title = "Bluetooth connected"; subtitle = "Open STARLINK on your car to use the dashboard."; action = "Open dashboard"; dot = 0xff30b86c; }
        else if (active && raw.startsWith("Ready:")) { title = "Ready to connect"; subtitle = "Connect your car’s Bluetooth, then open Apps → STARLINK."; action = "Open dashboard"; dot = palette.accent; }
        else if (active || (startRequested && SystemClock.elapsedRealtime() - lastStart < 3000)) { title = "Preparing connection…"; subtitle = "Getting your local dashboard ready."; action = "Open dashboard"; dot = palette.accent; }
        else if (raw.startsWith("Stopped:") && !paused) { title = "Connection needs attention"; subtitle = "Try again, or check Tools for details. Other STARLINK listeners should be closed."; action = "Try again"; }
        else { title = "Connection paused"; subtitle = autoEnabled() ? "Resume when you’re ready. Your pause stays in effect until you resume." : "Automatic connection is off. Start whenever you’re ready."; action = "Resume connection"; }
        if (localIssue != null) subtitle = localIssue;
        setText(statusTitle, title); setText(statusDetail, subtitle);
        if (statusDot != null) statusDot.setBackground(shape(dot, 5, false));
        if (primary != null) {
            primary.setText(action);
            primary.setOnClickListener(v -> {
                if (requirement == StartupPolicy.Need.NO_BLUETOOTH) showDashboard(HOME_PATH);
                else if (requirement == StartupPolicy.Need.CORE_UNAVAILABLE) select(2);
                else primaryAction();
            });
        }
        if (pauseButton != null) pauseButton.setText(active || (startRequested && SystemClock.elapsedRealtime() - lastStart < 3000) ? "Pause connection" : "Resume connection");
        if (exportButton != null) { exportButton.setEnabled(!exporting); exportButton.setText(exporting ? "Saving report…" : "Export private report"); }
        if (details != null) {
            String diagnostic = "Status: " + raw + "\nRust transport: " + (coreReady ? "ready" : "unavailable")
                    + "\nBluetooth: " + requirement + "\nAutomatic: " + autoEnabled() + "\nBuild: " + DiagnosticEngine.BUILD
                    + "\n\n" + new JSONObject(LinkDiagnostics.snapshot()).toString();
            setText(details, diagnostic);
        }
    }
    private void exportReport() {
        if (exporting) return; exporting = true; refreshStatus();
        PrivateReport.save(this, saved -> {
            exporting=false;refreshStatus();
            Toast.makeText(getApplicationContext(),saved?"Private report saved in Downloads":"Export failed. Your original data is safe.",Toast.LENGTH_LONG).show();
        });
    }
    private void openRequestedScreen() {
        String path=getIntent().getStringExtra("dashboardPath");
        if("/subarubridge-diag/basic?screen=1".equals(path)){tab=1;previewPath=path;getIntent().removeExtra("dashboardPath");}
    }
    private void back() { if (tab != 0) select(0); else finish(); }
    // API 30–32 hardware/legacy back. API 33+ uses the native dispatcher registered above.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }
    @Override public void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); paused = StarlinkService.pausedByUser(this); startRequested = false;
        if(intent.hasExtra("dashboardPath")){openRequestedScreen();buildUi();}
        if (intent.getBooleanExtra("start", false)) begin(true);
        else handler.postDelayed(() -> { if (autoEnabled() && !isFinishing()) begin(false); }, 200);
    }
    @Override public void onResume() {
        super.onResume(); resumed = true;
        paused=StarlinkService.pausedByUser(this);
        if (stoppedSinceResume && autoEnabled()) { startRequested=false; }
        stoppedSinceResume=false;
        if (SystemClock.elapsedRealtime() - lastStart > 3000) startRequested = false;
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(bluetoothState, filter, Context.RECEIVER_EXPORTED); else registerReceiver(bluetoothState, filter);
            receiverRegistered = true;
        }
        handler.post(refresh); handler.postDelayed(() -> {
            if (manualSetup && need() == StartupPolicy.Need.READY) begin(true); else tryAutomatic();
        }, 200);
        if (getIntent().getBooleanExtra("start", false)) { getIntent().removeExtra("start"); begin(true); }
    }
    @Override public void onPause() { resumed = false; handler.removeCallbacks(refresh); if (receiverRegistered) { unregisterReceiver(bluetoothState); receiverRegistered = false; } super.onPause(); }
    @Override public void onStop() { stoppedSinceResume=true;super.onStop(); }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab); out.putString("previewPath", previewPath); out.putBoolean("paused", paused); out.putBoolean("bluetoothPrompted", bluetoothPrompted);
        out.putBoolean("permissionInFlight", permissionInFlight); out.putBoolean("enableInFlight", enableInFlight); super.onSaveInstanceState(out);
        out.putBoolean("manualSetup", manualSetup);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == NEARBY) { permissionInFlight = false; startRequested = false; if (hasNearby() && !paused) begin(manualSetup); else manualSetup = false; refreshStatus(); }
    }
    @Override public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BT) { enableInFlight = false; startRequested = false; if (result == RESULT_OK && manualSetup) begin(true); else { manualSetup = false; tryAutomatic(); } refreshStatus(); }
    }
    @Override public void onDestroy() { handler.removeCallbacksAndMessages(null); if (preview != null) { preview.stopLoading(); preview.destroy(); } super.onDestroy(); }

    /** Original simple line icons; no external font or vendor artwork. */
    private final class Icon extends View {
        private final String name; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(String name, int color) { super(StarlinkActivity.this); this.name = name; paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/24f, getHeight()/24f);
            if (name.equals("home")) { Path p = new Path(); p.moveTo(3,11); p.lineTo(12,3); p.lineTo(21,11); p.moveTo(5,9); p.lineTo(5,21); p.lineTo(10,21); p.lineTo(10,15); p.lineTo(14,15); p.lineTo(14,21); p.lineTo(19,21); p.lineTo(19,9); canvas.drawPath(p,paint); }
            else if (name.equals("grid")) { for (int x : new int[]{4,14}) for (int y : new int[]{4,14}) canvas.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,paint); }
            else if (name.equals("check")) { canvas.drawRoundRect(4,4,20,21,3,3,paint); Path p=new Path();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(17,9);canvas.drawPath(p,paint); }
            else if (name.equals("link")) { canvas.drawArc(3,4,14,15,100,245,false,paint);canvas.drawArc(10,9,21,20,-80,245,false,paint);canvas.drawLine(9,15,15,9,paint); }
            else if (name.equals("appearance")) { canvas.drawCircle(12,12,8,paint);canvas.drawLine(12,4,12,20,paint);paint.setStyle(Paint.Style.FILL);canvas.drawArc(4,4,20,20,90,180,true,paint);paint.setStyle(Paint.Style.STROKE); }
            else { canvas.drawLine(4,6,20,6,paint);canvas.drawLine(4,12,20,12,paint);canvas.drawLine(4,18,20,18,paint);canvas.drawCircle(9,6,2,paint);canvas.drawCircle(16,12,2,paint);canvas.drawCircle(8,18,2,paint); }
            canvas.restore();
        }
    }
}
