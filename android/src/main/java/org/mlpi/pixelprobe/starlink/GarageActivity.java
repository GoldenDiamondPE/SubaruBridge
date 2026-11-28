package org.mlpi.pixelprobe.starlink;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.mlpi.pixelprobe.R;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;

/** Native ownership workspace. Vehicle connectivity is never needed to open or use it. */
public final class GarageActivity extends Activity {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final int EXPORT = 810, IMPORT = 811;
    private OwnershipStore store;
    // Process-local instrumentation injection; never read from intents or protocol messages.
    static volatile OwnershipStore testStore;
    private boolean isolated;
    private SharedPreferences selectionPrefs;
    private PhonePalette palette;
    private LinearLayout page;
    private JSONObject snapshot, state, vehicle, summary;
    private String selected = "", section = "Overview", formKind, editingId = "", itemId = "", historyFilter = "All";
    private String draftId = UUID.randomUUID().toString();
    private String historyQuery = "";
    private ZoneId formZone = ZoneId.systemDefault();
    private boolean clockRegistered;
    private int analyticsYear = LocalDate.now().getYear();
    private boolean costChart;
    private boolean fuelChart;
    private final BroadcastReceiver clockChange = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (formKind == null && !busy) perform(() -> store.view(), false);
            if (!isolated) GarageReminders.refresh(getApplicationContext());
        }
    };
    private int historyLimit = 50;
    private boolean busy;
    private Form form;
    private Bundle restoredDraft;
    private String exportKind = "backup";
    private TextView errorText;

    private interface Task { JSONObject run() throws Exception; }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); palette = new PhonePalette(this); OwnershipStore fixture = testStore;
        isolated = fixture != null; store = isolated ? fixture : new OwnershipStore(this);
        selectionPrefs = getSharedPreferences(isolated ? "garage-qa-selection" : "subarubridge-garage-selection", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        selected = selectionPrefs.getString("vehicle", "");
        if (saved != null) {
            selected = saved.getString("selected", selected); section = saved.getString("section", "Overview");
            formKind = saved.getString("form"); editingId = saved.getString("editing", ""); itemId = saved.getString("item", "");
            draftId = saved.getString("draftId", draftId);
            historyQuery = saved.getString("historyQuery", "");
            analyticsYear = saved.getInt("analyticsYear", analyticsYear); costChart = saved.getBoolean("costChart"); fuelChart=saved.getBoolean("fuelChart");
            try { formZone = ZoneId.of(saved.getString("formZone", formZone.getId())); } catch (DateTimeException ignored) { formZone = ZoneId.systemDefault(); }
            restoredDraft = saved.getBundle("draft"); exportKind = saved.getString("export", "backup");
        }
        beginPage("Garage", "Opening your saved vehicle records…");
        perform(() -> store.view(), false);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("selected", selected); out.putString("section", section);
        out.putString("form", formKind); out.putString("editing", editingId); out.putString("item", itemId);
        out.putString("draftId", draftId);
        out.putString("historyQuery", historyQuery);
        out.putInt("analyticsYear", analyticsYear); out.putBoolean("costChart", costChart); out.putBoolean("fuelChart",fuelChart);
        out.putString("formZone", formZone.getId());
        out.putString("export", exportKind); if (form != null) out.putBundle("draft", form.draft());
    }
    private boolean alive() { return !isFinishing() && !isDestroyed(); }
    @Override protected void onResume() {
        super.onResume(); IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_DATE_CHANGED); filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (!clockRegistered) { if (Build.VERSION.SDK_INT >= 33) registerReceiver(clockChange, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(clockChange, filter); clockRegistered = true; }
        if (snapshot != null && formKind == null && !busy) perform(() -> store.view(), false);
        if (!isolated) GarageReminders.refresh(getApplicationContext());
    }
    @Override protected void onPause() { if (clockRegistered) { unregisterReceiver(clockChange); clockRegistered = false; } super.onPause(); }
    private void perform(Task task, boolean saved) {
        if (busy) return; busy = true; if (form != null) form.enable(false);
        WORK.execute(() -> {
            try {
                JSONObject result = task.run();
                runOnUiThread(() -> {
                    if (!alive()) return; busy = false; snapshot = result;
                    if (saved) { formKind = null; form = null; restoredDraft = null; toast("Saved on this phone"); }
                    render();
                });
                if (saved && !isolated) GarageReminders.refresh(getApplicationContext());
            } catch (Exception | LinkageError failure) {
                runOnUiThread(() -> {
                    if (!alive()) return; busy = false;
                    if (form != null) form.enable(true);
                    if (snapshot == null || errorText == null) beginPage("Garage", "Your records were preserved.");
                    showError("Could not complete this change. " + safeError(failure));
                    if (snapshot == null) button("Try again", R.id.garage_retry, () -> perform(() -> store.view(), false));
                });
            }
        });
    }
    private static String safeError(Throwable failure) {
        String message = failure.getMessage(); return message == null || message.length() > 220 ? "Check the saved file or available storage." : message;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) { GradientDrawable value = new GradientDrawable(); value.setColor(color); value.setCornerRadius(dp(radius)); return value; }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        text.setPadding(0, dp(8), 0, dp(8)); text.setTextIsSelectable(true); page.addView(text, new LinearLayout.LayoutParams(-1, -2)); return text;
    }
    private void note(String value) { label(value, 14, palette.muted, false); }
    private void heading(String value) { label(value, 20, palette.text, true); }
    private Button button(String value, int id, Runnable action) {
        Button view = new Button(this); view.setText(value); view.setAllCaps(false); view.setTextSize(16);
        view.setTextColor(palette.accent); view.setBackground(background(palette.surface, 12)); view.setMinHeight(dp(50));
        view.setPadding(dp(12), dp(10), dp(12), dp(10)); view.setId(id); view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(5), 0, dp(5)); page.addView(view, params); return view;
    }
    private LinearLayout row(String title, String detail, Runnable action) {
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); group.setPadding(0, dp(12), 0, dp(12)); group.setMinimumHeight(dp(64));
        TextView primary = new TextView(this); primary.setText(title); primary.setTextColor(palette.text); primary.setTextSize(17); group.addView(primary);
        TextView secondary = new TextView(this); secondary.setText(detail); secondary.setTextColor(palette.muted); secondary.setTextSize(14); secondary.setPadding(0, dp(5), 0, 0); group.addView(secondary);
        if (action != null) { group.setClickable(true); group.setFocusable(true); group.setContentDescription(title + ". " + detail); group.setOnClickListener(v -> action.run()); }
        page.addView(group, new LinearLayout.LayoutParams(-1, -2)); View line = new View(this); line.setBackgroundColor(palette.border); page.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return group;
    }
    private void beginPage(String title, String subtitle) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(palette.background);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(22), dp(34), dp(22), dp(32)); scroll.addView(page); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            page.setPadding(dp(22), dp(14), dp(22), dp(20)); return insets;
        });
        Button back = button(formKind == null ? "‹ SubaruBridge" : "‹ Cancel", View.NO_ID, this::back);
        back.setBackgroundColor(android.graphics.Color.TRANSPARENT); back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); back.setPadding(0, 0, 0, 0); back.setMinHeight(dp(44));
        label(title, 28, palette.text, true); if (!subtitle.isEmpty()) note(subtitle);
        errorText = label("", 14, palette.accent, false); errorText.setId(R.id.garage_error); errorText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); errorText.setVisibility(View.GONE);
    }
    private void showError(String message) { errorText.setText(message); errorText.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE); if (!message.isEmpty() && page.getParent() instanceof ScrollView) ((ScrollView)page.getParent()).smoothScrollTo(0, 0); }
    private void back() {
        if (busy) return;
        if (formKind == null) { finish(); return; }
        new AlertDialog.Builder(this).setTitle("Discard this entry?").setMessage("Your saved records will stay as they are.")
                .setNegativeButton("Keep editing", null).setPositiveButton("Discard", (d, w) -> { formKind = null; form = null; restoredDraft = null; render(); }).show();
    }
    // API 30–32 fallback; modern gestures use the registered native OnBackInvokedDispatcher.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }
    private void render() {
        try {
            state = snapshot.getJSONObject("state"); JSONArray cars = state.getJSONArray("vehicles"); vehicle = null; summary = null;
            for (int i = 0; i < cars.length(); i++) if (cars.getJSONObject(i).getString("id").equals(selected)) vehicle = cars.getJSONObject(i);
            if (vehicle == null && cars.length() > 0) { vehicle = cars.getJSONObject(0); selected = vehicle.getString("id"); }
            if (vehicle != null) for (int i = 0; i < snapshot.getJSONArray("summaries").length(); i++) {
                JSONObject value = snapshot.getJSONArray("summaries").getJSONObject(i); if (selected.equals(value.getString("vehicleId"))) summary = value;
            }
            selectionPrefs.edit().putString("vehicle", selected).apply();
            if (formKind != null) { buildForm(); return; }
            form = null; beginPage("Garage", "Mileage, maintenance and costs. Saved on this phone.");
            if (vehicle == null) { heading("Add your vehicle"); note("Start with a vehicle profile. Odometer and service history can be added whenever you have them."); button("Add vehicle", R.id.garage_add_vehicle, () -> openForm("vehicle", "", "")); dataButtons(); return; }
            row(vehicle.getString("nickname") + (vehicle.optBoolean("archived") ? " · Archived" : "") + " ⌄", vehicle.getInt("year") + " " + vehicle.getString("make") + " " + vehicle.getString("model") + " · " + unit() + " · Switch or add vehicle", this::chooseVehicle).setId(R.id.garage_switch);
            HorizontalScrollView navigation = new HorizontalScrollView(this); LinearLayout links = new LinearLayout(this);
            navigation.setHorizontalScrollBarEnabled(false); links.setPadding(dp(4), dp(4), dp(4), dp(4)); links.setBackground(background(palette.surface, 12));
            for (String title : new String[]{"Overview", "Maintenance", "History", "More"}) {
                boolean chosen = title.equals(section) || title.equals("More") && section.equals("Analytics");
                Button link = new Button(this); link.setText(title); link.setAllCaps(false); link.setTextColor(chosen ? palette.accent : palette.muted); link.setSelected(chosen); link.setContentDescription(title + (chosen ? ", selected" : ""));
                link.setTextSize(14); link.setMinHeight(dp(44)); link.setMinimumHeight(dp(44)); link.setMinWidth(0); link.setMinimumWidth(0); link.setPadding(dp(12), dp(8), dp(12), dp(8));
                link.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(palette.border), background(chosen ? palette.raised : palette.surface, 9), null));
                link.setId(title.equals("Maintenance") ? R.id.garage_maintenance : title.equals("History") ? R.id.garage_history : title.equals("More") ? R.id.garage_more : R.id.garage_overview);
                link.setOnClickListener(v -> { section = title; historyLimit = 50; render(); }); links.addView(link);
            }
            navigation.addView(links); page.addView(navigation);
            switch (section) { case "Maintenance": maintenance(); break; case "History": history(); break; case "More": more(); break; case "Analytics": analytics(); break; case "Reference": reference(); break; default: overview(); }
        } catch (Exception error) { beginPage("Garage", "Your records were preserved."); showError(safeError(error)); }
    }
    private boolean metric() { return vehicle != null && vehicle.optString("units").equals("metric"); }
    private String unit() { return metric() ? "km" : "mi"; }
    private String distance(long mm) { return OwnershipNumbers.distance(mm, metric()) + " " + unit(); }
    private String money(long cents) { return OwnershipNumbers.money(cents, vehicle.optString("currency", "USD")); }
    private String inputDistance(long mm) { return BigDecimal.valueOf(mm).divide(new BigDecimal(metric() ? "1000000" : "1609344"), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String when(long millis) { return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)); }
    private String formWhen(long millis) { return OwnershipTime.format(millis, formZone); }
    private long timestamp(String text) { return OwnershipTime.parse(text, formZone); }
    private static String day(String text, boolean optional) { if (optional && text.trim().isEmpty()) return ""; try { LocalDate date = LocalDate.parse(text.trim()); if (!date.toString().equals(text.trim())) throw new IllegalArgumentException(); return date.toString(); } catch (Exception error) { throw new IllegalArgumentException("Use YYYY-MM-DD for a valid date"); } }
    private void overview() throws Exception {
        JSONObject mileage = summary.getJSONObject("mileage"); TextView number = label(mileage.isNull("currentMm") ? "Add an odometer reading" : distance(mileage.getLong("currentMm")), 32, palette.text, true); number.setId(R.id.garage_mileage);
        note(mileage.isNull("currentMm") ? "No current odometer is recorded." : mileage.getBoolean("estimated") ? "Estimated from recorded readings and completed trips." : "Confirmed odometer reading.");
        if (!mileage.isNull("lastConfirmedMm")) note("Last confirmed: " + distance(mileage.getLong("lastConfirmedMm")));
        JSONArray warnings = mileage.getJSONArray("warnings"); for (int i = 0; i < warnings.length(); i++) note(warnings.getString(i));
        button("Log odometer", R.id.garage_log_odometer, () -> openForm("reading", "", ""));
        heading("Maintenance"); int upcoming = 0, unknown = 0; JSONArray due = summary.getJSONArray("maintenance");
        for (int i = 0; i < due.length(); i++) { String value = due.getJSONObject(i).getString("status"); if (actionable(value)) upcoming++; if (value.equals("unknownHistory")) unknown++; }
        row(quantity(upcoming, "item") + (upcoming == 1 ? " needs" : " need") + " attention", unknown + " with unknown history. Open Maintenance for the calculation and source.", () -> { section = "Maintenance"; render(); });
        button("Log service", R.id.garage_log_service, () -> openForm("service", "", ""));
        heading("Fuel & ownership"); JSONObject fuel = summary.getJSONObject("fuel"), costs = summary.getJSONObject("costs");
        String economy = fuel.isNull("mpgUs") ? "Add two full fill-ups to measure economy" : metric() ? (fuel.isNull("litresPer100Km") ? "No distance in completed fuel cycles" : String.format(Locale.getDefault(), "%.1f L/100 km", fuel.getDouble("litresPer100Km"))) : String.format(Locale.getDefault(), "%.1f MPG (US)", fuel.getDouble("mpgUs"));
        row(economy, fuel.getInt("completedCycles") + " measured full-to-full cycles, including partial fills", null);
        row(money(costs.getLong("recordedOperatingCents")), "Recorded operating costs · fuel, services and other expenses", null);
        button("Log fuel", R.id.garage_log_fuel, () -> openForm("fuel", "", ""));
        button("Log expense", R.id.garage_log_expense, () -> openForm("expense", "", ""));
        button("Log completed trip", R.id.garage_log_trip, () -> openForm("trip", "", ""));
        if (!mileage.isNull("averageMmPerDay")) note("Average confirmed mileage growth: " + distance(mileage.getLong("averageMmPerDay")) + " per day.");
        note("Vehicle monitoring currently uses your records. Live engine data and automatic odometer access have not been verified for STARLINK.");
    }
    private static boolean actionable(String status) { return Arrays.asList("dueSoon", "due", "overdue", "inspectionRecommended").contains(status); }
    private static String quantity(int count, String noun) { return count + " " + noun + (count == 1 ? "" : "s"); }
    private static String statusName(String status) { switch (status) { case "upToDate": return "Up to date"; case "dueSoon": return "Due soon"; case "due": return "Due"; case "overdue": return "Overdue"; case "inspectionRecommended": return "Inspection recommended"; case "notApplicable": return "Disabled"; default: return "Unknown service history"; } }
    private void maintenance() throws Exception {
        JSONArray items = summary.getJSONArray("maintenance");
        button("Add maintenance item", R.id.garage_add_item, () -> openForm("custom", "", ""));
        button("Reminder lead times", R.id.garage_lead_times, () -> openForm("preferences", "", ""));
        if (vehicle.isNull("maintenanceProfile")) note("A verified manufacturer schedule is not selected. Add owner-defined maintenance for this vehicle.");
        else note("Manufacturer US schedule · 2017 Legacy 2.5 CVT. Both time and distance apply, whichever comes first. Unknown history is never assumed overdue.");
        if (vehicle.optBoolean("severe") && vehicle.optJSONArray("severeConditions").length()==0) note("An older broad severe-service flag is set. Choose specific driving conditions in the vehicle profile to apply verified exceptions.");
        if(vehicle.optJSONArray("severeConditions").length()>0)note("Condition-specific exceptions from Subaru’s 2017 booklet are active. Open each item for its source and calculation.");
        List<JSONObject> sorted = new ArrayList<>(); for (int i = 0; i < items.length(); i++) sorted.add(items.getJSONObject(i));
        sorted.sort(Comparator.comparingInt(value -> priority(value.optString("status"))));
        for (JSONObject item : sorted) row(item.getString("title"), statusName(item.getString("status")) + (item.optBoolean("uncertain") ? " · Uncertain" : "") + (item.isNull("reminderSnoozedUntil") ? "" : " · Reminders snoozed until " + item.optString("reminderSnoozedUntil")) + next(item), () -> detail(item));
    }
    private int priority(String status) { switch (status) { case "overdue": return 0; case "due": return 1; case "dueSoon": case "inspectionRecommended": return 2; case "unknownHistory": return 3; case "upToDate": return 4; default: return 5; } }
    private String next(JSONObject item) {
        String text = "";
        if (!item.isNull("nextLowMm")) text += "\nNext: " + distance(item.optLong("nextLowMm")) + (item.optLong("nextHighMm") != item.optLong("nextLowMm") ? " – " + distance(item.optLong("nextHighMm")) : "");
        if (!item.isNull("nextDate")) text += "\nBy " + item.optString("nextDate"); return text;
    }
    private void detail(JSONObject item) {
        StringBuilder message = new StringBuilder(statusName(item.optString("status"))).append(next(item));
        if (!item.isNull("lastDate")) message.append("\nLast service: ").append(item.optString("lastDate"));
        JSONObject last = item.optJSONObject("lastMileage"); if (last != null) message.append("\nLast mileage: ").append(distance(last.optLong("lowMm"))).append(last.optLong("highMm") != last.optLong("lowMm") ? " – " + distance(last.optLong("highMm")) : "");
        if (!item.isNull("remainingLowMm")) message.append("\nDistance to threshold: ").append(distance(item.optLong("remainingLowMm"))).append(item.optLong("remainingHighMm") != item.optLong("remainingLowMm") ? " to " + distance(item.optLong("remainingHighMm")) : "");
        if (!item.isNull("remainingDays")) message.append("\nDays to threshold: ").append(item.optLong("remainingDays"));
        JSONObject forecast=summary.optJSONObject("forecast");
        if(forecast!=null&&!item.isNull("remainingLowMm")&&item.optLong("remainingLowMm")>0){
            double daily=forecast.optDouble("averageMmPerDay",0);long days=(long)Math.ceil(item.optLong("remainingLowMm")/daily),high=(long)Math.ceil(item.optLong("remainingHighMm")/daily);
            if(daily>0&&days<=3650&&high<=3650)message.append("\nEstimated mileage threshold: ").append(LocalDate.now().plusDays(days)).append(days==high?"":" to "+LocalDate.now().plusDays(high)).append(" (at recent confirmed mileage growth; calendar service may be earlier)");
        }
        message.append("\n\nAction: ").append(item.optString("action")).append("\n").append(item.optString("explanation")).append("\n\nSource: ").append(item.optString("sourceRow"));
        new AlertDialog.Builder(this).setTitle(item.optString("title")).setMessage(message.toString())
                .setPositiveButton("Log service", (d, w) -> openForm("service", "", item.optString("itemId")))
                .setNeutralButton("Interval & source", (d, w) -> openForm("interval", "", item.optString("itemId"))).setNegativeButton("Close", null).show();
    }
    private JSONArray records(String name) throws Exception {
        JSONArray all = state.getJSONArray(name), result = new JSONArray();
        for (int i = 0; i < all.length(); i++) if (selected.equals(all.getJSONObject(i).getString("vehicleId"))) result.put(all.getJSONObject(i)); return result;
    }
    private JSONObject find(String name, String id) throws Exception { JSONArray list = state.getJSONArray(name); for (int i = 0; i < list.length(); i++) if (id.equals(list.getJSONObject(i).optString("id"))) return list.getJSONObject(i); return new JSONObject(); }
    private static String collection(String kind) { switch (kind) { case "reading": return "readings"; case "service": return "services"; case "expense": return "expenses"; case "trip": return "trips"; case "custom": return "customItems"; default: return "fuel"; } }
    private static final class Event { String kind, title, detail, id; long at; Event(String kind, String title, String detail, String id, long at) { this.kind=kind; this.title=title; this.detail=detail; this.id=id; this.at=at; } }
    private void history() throws Exception {
        EditText query = new EditText(this); query.setText(historyQuery); query.setHint("Search services, parts or notes"); query.setSingleLine(true); query.setTextColor(palette.text); query.setHintTextColor(palette.muted); query.setId(R.id.garage_search); query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(160)}); page.addView(query);
        button("Search history", View.NO_ID, () -> { historyQuery = query.getText().toString().trim(); historyLimit = 50; if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) getWindow().getInsetsController().hide(WindowInsets.Type.ime()); render(); });
        button("Show: " + historyFilter, R.id.garage_filter, () -> new AlertDialog.Builder(this).setTitle("History").setItems(new String[]{"All", "Odometer", "Services", "Fuel", "Expenses", "Trips"}, (d, which) -> { historyFilter = new String[]{"All", "Odometer", "Services", "Fuel", "Expenses", "Trips"}[which]; historyLimit = 50; render(); }).show());
        List<Event> events = new ArrayList<>(); Set<String> corrected = new HashSet<>(); JSONArray readings = records("readings");
        for (int i = 0; i < readings.length(); i++) if (!readings.getJSONObject(i).isNull("supersedes")) corrected.add(readings.getJSONObject(i).getString("supersedes"));
        String[] kinds = {"reading", "service", "fuel", "expense", "trip"}, filters = {"Odometer", "Services", "Fuel", "Expenses", "Trips"};
        for (int k = 0; k < kinds.length; k++) {
            if (!historyFilter.equals("All") && !historyFilter.equals(filters[k])) continue;
            JSONArray values = records(collection(kinds[k]));
            for (int i = 0; i < values.length(); i++) {
                JSONObject v = values.getJSONObject(i); String id = v.getString("id"), title = "", detail = ""; long at = 0;
                switch (kinds[k]) {
                    case "reading": at = v.getLong("atMs"); title = distance(v.getLong("distanceMm")); detail = when(at) + " · " + v.getString("confidence") + (corrected.contains(id) ? " · Replaced by a correction" : ""); break;
                    case "service": title = serviceTitles(v.getJSONArray("itemIds")); detail = v.isNull("date") ? "Date unknown" : v.getString("date"); if (!v.isNull("date")) at = LocalDate.parse(v.getString("date")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); detail += " · " + money(v.getLong("costCents")); break;
                    case "fuel": at = v.getLong("atMs"); title = "Fuel · " + money(v.getLong("costCents")); detail = when(at) + " · " + distance(v.getLong("distanceMm")) + (v.getBoolean("full") ? " · Full" : " · Partial"); break;
                    case "expense": title = v.getString("category") + " · " + money(v.getLong("costCents")); detail = v.getString("date") + (v.optBoolean("financing") ? " · Financing" : ""); at = LocalDate.parse(v.getString("date")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); break;
                    case "trip": at = v.getLong("endedMs"); title = "Trip · " + distance(v.getLong("distanceMm")); detail = when(v.getLong("startedMs")) + " · " + v.getString("source"); break;
                }
                if (!v.optString("notes").isEmpty()) detail += "\n" + v.getString("notes");
                StringBuilder index = new StringBuilder(title).append(' ').append(detail).append(' ').append(v.optString("provider")).append(' ').append(v.optString("warranty")).append(' ').append(v.optString("source"));
                JSONArray parts = v.optJSONArray("partNumbers"); if (parts != null) for (int p = 0; p < parts.length(); p++) index.append(' ').append(parts.getString(p));
                String searchable = index.toString().toLowerCase(Locale.ROOT);
                if (historyQuery.isEmpty() || searchable.contains(historyQuery.toLowerCase(Locale.ROOT))) events.add(new Event(kinds[k], title, detail, id, at));
            }
        }
        events.sort(Comparator.comparingLong((Event e) -> e.at).reversed()); note(quantity(events.size(), "saved record") + ". Tap a record to review or correct it.");
        for (Event event : events.subList(0, Math.min(events.size(), historyLimit))) row(event.title, event.detail, () -> { if (event.kind.equals("reading") && corrected.contains(event.id)) { toast("This original is preserved. Correct the active reading instead."); return; } openForm(event.kind, event.id, ""); });
        if (events.size() > historyLimit) button("Show 50 more", View.NO_ID, () -> { historyLimit += 50; render(); });
    }
    private String serviceTitles(JSONArray ids) throws Exception { List<String> titles = new ArrayList<>(); for (int i = 0; i < ids.length(); i++) titles.add(rule(ids.getString(i)).optString("title", ids.getString(i))); return String.join(", ", titles); }
    private JSONArray availableRules() throws Exception {
        JSONArray rules = new JSONArray(snapshot.getJSONObject("schedule").getJSONArray("rules").toString()), custom = state.getJSONArray("customItems");
        for (int i = 0; i < custom.length(); i++) { JSONObject item = custom.getJSONObject(i); if (selected.equals(item.getString("vehicleId"))) rules.put(new JSONObject().put("id", item.getString("id")).put("title", item.getString("title")).put("action", item.getString("action")).put("normal", item.getJSONObject("interval")).put("sourceRow", "Owner-defined maintenance item").put("notes", item.getString("notes"))); }
        return rules;
    }
    private JSONObject rule(String id) throws Exception { JSONArray rules = availableRules(); for (int i = 0; i < rules.length(); i++) if (id.equals(rules.getJSONObject(i).getString("id"))) return rules.getJSONObject(i); return new JSONObject(); }
    private void chooseVehicle() {
        try {
            JSONArray cars = state.getJSONArray("vehicles"); String[] names = new String[cars.length() + 1];
            for (int i = 0; i < cars.length(); i++) names[i] = cars.getJSONObject(i).getString("nickname") + (cars.getJSONObject(i).optBoolean("archived") ? " · Archived" : ""); names[cars.length()] = "Add vehicle";
            new AlertDialog.Builder(this).setTitle("Your garage").setItems(names, (d, index) -> { if (index == cars.length()) openForm("vehicle", "", ""); else { selected = cars.optJSONObject(index).optString("id"); render(); } }).show();
        } catch (Exception error) { showError(safeError(error)); }
    }
    private void more() throws Exception {
        button("Edit vehicle profile", R.id.garage_edit_vehicle, () -> openForm("vehicle", selected, ""));
        button("Ownership summaries", R.id.garage_analytics, () -> { section = "Analytics"; render(); });
        button("Maintenance reminders", R.id.garage_reminders, this::reminders);
        button("Fluids, parts & specifications", View.NO_ID, () -> { section="Reference"; render(); });
        heading("Recorded costs"); JSONObject costs = summary.getJSONObject("costs");
        row("Fuel", money(costs.getLong("fuelCents")), null); row("Services", money(costs.getLong("serviceCents")), null);
        row("Other operating costs", money(costs.getLong("otherOperatingCents")), null); row("Financing", money(costs.getLong("financingCents")), null);
        row("Total recorded", money(costs.getLong("recordedTotalCents")), null);
        note("Fuel and service costs are included automatically. Do not enter the same purchase again as an expense. Purchase price is kept on the profile and excluded from these recorded operating totals.");
        heading("Data & privacy"); dataButtons();
        note("Vehicle records stay in this app's private storage and are excluded from connection reports. Export a backup before uninstalling or clearing app data. Complete backups can be password-protected and include receipts. Records-only JSON is also available.");
    }
    private void reference() throws Exception {
        heading("Vehicle reference");
        if (!"subaru-us-2017-legacy-25-cvt".equals(vehicle.optString("maintenanceProfile"))) { note("The bundled reference applies to the US 2017 Legacy 2.5 CVT. This profile does not select that verified configuration."); return; }
        JSONObject database;
        try(InputStream input=getAssets().open("vehicle-reference.json")){database=OwnershipStore.parse(OwnershipStore.readBounded(input,256*1024));}
        note(database.getString("vehicle")+" · verified "+database.getString("verifiedDate"));
        note("Manufacturer facts retained offline with document sections. Quantities are guidelines; follow the specified level-check procedure. Total capacities are not service-fill quantities.");
        JSONArray specs=database.getJSONArray("specifications");
        for(int i=0;i<specs.length();i++){JSONObject spec=specs.getJSONObject(i);row(spec.getString("title"),spec.getString("value"),()->new AlertDialog.Builder(this).setTitle(spec.optString("title")).setMessage(spec.optString("value")+"\n\n"+spec.optString("notes")+"\n\nSource: "+spec.optString("sourceSection")+"\nVerified "+database.optString("verifiedDate"))
                .setPositiveButton("Close",null).setNeutralButton("Manufacturer source",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(database.optString("sourceUrl"))));}catch(ActivityNotFoundException e){toast("No browser is available");}}).show());}
        heading("Parts & compatibility");note("Spark-plug type is verified in the owner’s manual. Oil-filter numbers below are bulletin references; confirm your exact VIN and current supersession before purchase. Other filter fitments and CVT service fluid are left unverified rather than guessed.");
        for(int i=0;i<database.getJSONArray("parts").length();i++){JSONObject part=database.getJSONArray("parts").getJSONObject(i);row(part.getString("title"),part.getString("value")+" · "+part.getString("status"),()->new AlertDialog.Builder(this).setTitle(part.optString("title")).setMessage(part.optString("notes")+"\n\n"+part.optString("sourceSection")).setPositiveButton("Close",null).show());}
        note("Preferred parts and quantities can be recorded with each service. Receipts remain attached to that service and are included in complete backups.");
    }
    private JSONObject period(String id, String label, LocalDate start, LocalDate end) throws Exception {
        ZoneId zone = ZoneId.systemDefault();
        return new JSONObject().put("id", id).put("label", label).put("startDate", start.toString()).put("endDate", end.toString())
                .put("startMs", start.atStartOfDay(zone).toInstant().toEpochMilli()).put("endMs", end.atStartOfDay(zone).toInstant().toEpochMilli());
    }
    private void loadAnalytics() {
        if (busy) return;
        try {
            int year = analyticsYear; LocalDate start = LocalDate.of(year, 1, 1); JSONArray periods = new JSONArray().put(period("year-" + year, Integer.toString(year), start, start.plusYears(1)));
            for (int month = 1; month <= 12; month++) { LocalDate day = LocalDate.of(year, month, 1); periods.put(period(day.toString().substring(0, 7), day.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())), day, day.plusMonths(1))); }
            JSONObject command = new JSONObject().put("type", "analyze").put("periods", periods); perform(() -> store.execute(command), false);
        } catch (Exception error) { showError(safeError(error)); }
    }
    private void analytics() throws Exception {
        heading("Ownership summaries · " + analyticsYear);
        LinearLayout years = new LinearLayout(this);
        Button previous = new Button(this); previous.setText("‹ Previous year"); previous.setAllCaps(false); previous.setTextColor(palette.accent); previous.setEnabled(analyticsYear > 1900);
        Button nextYear = new Button(this); nextYear.setText("Next year ›"); nextYear.setAllCaps(false); nextYear.setTextColor(palette.accent); nextYear.setEnabled(analyticsYear < Math.min(LocalDate.now().getYear(), 2199));
        for (Button control : new Button[]{previous, nextYear}) { control.setBackground(background(palette.surface, 10)); control.setTextSize(16); control.setMinHeight(dp(46)); control.setAlpha(control.isEnabled() ? 1f : .45f); }
        previous.setOnClickListener(v -> { if (!busy) { analyticsYear--; loadAnalytics(); } }); nextYear.setOnClickListener(v -> { if (!busy) { analyticsYear++; loadAnalytics(); } });
        years.addView(previous, new LinearLayout.LayoutParams(0, -2, 1)); years.addView(nextYear, new LinearLayout.LayoutParams(0, -2, 1)); page.addView(years);
        JSONArray data = summary.getJSONArray("analytics");
        if (data.length() != 13 || !data.getJSONObject(0).getJSONObject("period").getString("id").equals("year-" + analyticsYear)) { note("Calculating your recorded history…"); loadAnalytics(); return; }
        JSONObject total = data.getJSONObject(0), costs = total.getJSONObject("costs");
        row(distance(total.getLong("tripDistanceMm")) + " · " + quantity(total.getInt("recordedTrips"), "recorded trip"), duration(total.getLong("tripDurationMs")) + " · " + money(costs.getLong("operatingCents")) + " operating costs", null);
        button("Chart: " + (fuelChart ? "fuel economy" : costChart ? "operating costs" : "trip distance") + " · Change", R.id.garage_chart_mode, () -> new AlertDialog.Builder(this).setTitle("Monthly chart").setItems(new String[]{"Recorded trip distance", "Recorded operating costs", "Measured fuel economy"}, (d, index) -> { costChart=index==1; fuelChart=index==2; render(); }).show());
        long[] values = new long[12]; String[] labels = new String[12];
        for (int i = 0; i < 12; i++) { JSONObject month = data.getJSONObject(i+1); values[i] = fuelChart ? Math.round(month.getJSONObject("fuel").optDouble(metric()?"litresPer100Km":"mpgUs",0)*10) : costChart ? month.getJSONObject("costs").getLong("operatingCents") : month.getLong("tripDistanceMm"); labels[i] = month.getJSONObject("period").getString("label"); }
        OwnershipChart chart = new OwnershipChart(this, values, labels, palette, costChart, metric(), vehicle.optString("currency", "USD"), fuelChart); page.addView(chart, new LinearLayout.LayoutParams(-1, dp(210)));
        button("Read monthly details", R.id.garage_month_details, () -> new AlertDialog.Builder(this).setTitle("Recorded month").setItems(labels, (d, index) -> { try { monthDetails(data.getJSONObject(index+1)); } catch (Exception error) { showError(safeError(error)); } }).show());
        if(fuelChart)note("Blank months have no complete full-to-full fuel cycle inside that month.");
        heading("Fuel"); fuelDetails(total.getJSONObject("fuel"));
        heading("Mileage & costs");
        if (total.isNull("confirmedGrowthMm")) note("Confirmed odometer growth needs two active confirmed readings within this year.");
        else row("Confirmed growth: " + distance(total.getLong("confirmedGrowthMm")), when(total.getLong("growthStartMs")) + " to " + when(total.getLong("growthEndMs")) + ". Only this recorded window is covered.", null);
        JSONObject forecast=summary.optJSONObject("forecast");
        if(forecast!=null)row("Estimated annual mileage: "+distance(forecast.getLong("annualizedMm")),"Current estimate from confirmed readings, "+when(forecast.getLong("growthStartMs"))+" to "+when(forecast.getLong("growthEndMs"))+". Future driving may differ.",null);
        if(total.getLong("tripDistanceMm")>0){double units=total.getLong("tripDistanceMm")/(metric()?1000000.0:1609344.0);row(OwnershipNumbers.money(Math.round(costs.getLong("operatingCents")/units),vehicle.optString("currency","USD"))+" / logged "+unit(),"Recorded operating costs ÷ logged trip distance. Missing trips or costs change this ratio; it is not a measured whole-vehicle cost per mile.",null);}
        row("Financing",money(costs.getLong("financingCents"))+" · excluded from operating costs",null);
        if (total.getInt("undatedServiceCount") > 0) note(total.getInt("undatedServiceCount") + " undated services, totaling " + money(total.getLong("undatedServiceCents")) + ", remain in lifetime costs and are not assigned to a month or year.");
        note("Trips use their full distance in the completion month. Fuel uses complete full-to-full cycles inside the period. These records do not establish complete driving coverage. Saved civil dates and actual local time-zone boundaries are respected.");
    }
    private void fuelDetails(JSONObject fuel) throws Exception {
        row(quantity(fuel.getInt("fillUps"),"fill-up")+" · "+quantity(fuel.getInt("fullFillUps"),"full fill"),String.format(Locale.getDefault(),"%.2f L purchased",fuel.getLong("purchasedVolumeMl")/1000.0)+" · "+money(fuel.getLong("purchasedCostCents")),null);
        if(!fuel.isNull("averagePricePerLitre"))row("Average fuel price",String.format(Locale.getDefault(),"%.2f %s / %s",fuel.getDouble(metric()?"averagePricePerLitre":"averagePricePerUsGallon"),vehicle.optString("currency","USD"),metric()?"L":"US gal"),null);
        if(!fuel.isNull("costPerMile"))row("Measured fuel cost",String.format(Locale.getDefault(),"%.2f %s / %s",fuel.getDouble(metric()?"costPer100Km":"costPerMile"),vehicle.optString("currency","USD"),metric()?"100 km":"mile")+" · "+fuel.getInt("completedCycles")+" complete full-to-full cycles",null);
        if(!fuel.isNull(metric()?"litresPer100Km":"mpgUs"))row("Measured fuel economy",String.format(Locale.getDefault(),metric()?"%.1f L/100 km":"%.1f MPG (US)",fuel.getDouble(metric()?"litresPer100Km":"mpgUs")),null);
    }
    private static String duration(long ms) { return ms / 3600000 + " h " + (ms / 60000) % 60 + " min"; }
    private void monthDetails(JSONObject month) throws Exception {
        JSONObject costs = month.getJSONObject("costs"), fuel = month.getJSONObject("fuel");
        String text = quantity(month.getInt("recordedTrips"), "recorded trip") + " · " + distance(month.getLong("tripDistanceMm")) + "\nRecorded driving duration: " + duration(month.getLong("tripDurationMs"))
                + "\nFuel: " + money(costs.getLong("fuelCents")) + "\nServices: " + money(costs.getLong("serviceCents")) + "\nOther operating: " + money(costs.getLong("otherOperatingCents")) + "\nFinancing: " + money(costs.getLong("financingCents"));
        if (!month.isNull("averageTripMm")) text += "\nAverage trip: " + distance(month.getLong("averageTripMm")) + "\nLongest trip: " + distance(month.getLong("longestTripMm"));
        text += "\n\nFill-ups: "+fuel.getInt("fillUps")+" · full: "+fuel.getInt("fullFillUps")+"\nFuel purchased: "+String.format(Locale.getDefault(),"%.2f L",fuel.getLong("purchasedVolumeMl")/1000.0)+"\nFuel cycles wholly inside month: " + fuel.getInt("completedCycles");
        if(!fuel.isNull("costPerMile"))text+=String.format(Locale.getDefault(),"\nMeasured cycle fuel cost: %.2f %s / %s",fuel.getDouble(metric()?"costPer100Km":"costPerMile"),vehicle.optString("currency","USD"),metric()?"100 km":"mile");
        if (metric() && !fuel.isNull("litresPer100Km")) text += String.format(Locale.getDefault(), "\n%.1f L/100 km", fuel.getDouble("litresPer100Km"));
        if (!metric() && !fuel.isNull("mpgUs")) text += String.format(Locale.getDefault(), "\n%.1f MPG (US)", fuel.getDouble("mpgUs"));
        new AlertDialog.Builder(this).setTitle(month.getJSONObject("period").getString("label") + " " + analyticsYear).setMessage(text).setPositiveButton("Close", null).show();
    }
    private void dataButtons() {
        button("Backups & receipt data", R.id.garage_export, () -> startActivity(new Intent(this, GarageDataActivity.class)));
        button("Restore or merge backup", R.id.garage_import, () -> startActivity(new Intent(this, GarageDataActivity.class)));
        if (vehicle != null) { button("Export history CSV", R.id.garage_csv, () -> export("csv")); button("Export service report", R.id.garage_report, () -> export("report")); }
    }
    private void reminders() {
        boolean enabled = getSharedPreferences("subarubridge-garage-reminders", MODE_PRIVATE).getBoolean("enabled", false);
        new AlertDialog.Builder(this).setTitle("Maintenance reminders").setMessage(GarageReminders.diagnostic(this) + "\n\n" + (enabled ? "Reminders are on. " : "Reminders are off. ") + "A daily, best-effort check uses your saved records. Notifications show counts, without vehicle identifiers. Unknown history is excluded. Mileage reminders update when you save readings or trips.")
                .setPositiveButton(enabled ? "Turn off" : "Turn on", (d, w) -> {
                    getSharedPreferences("subarubridge-garage-reminders", MODE_PRIVATE).edit().putBoolean("enabled", !enabled).apply();
                    if (!enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 812);
                    GarageReminders.refresh(this); toast(enabled ? "Reminders off" : "Reminders enabled; Android notification permission is required");
                }).setNeutralButton("Android notification settings", (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())))
                .setNegativeButton("Send test notification", (d, w) -> toast(GarageReminders.testNotification(this) ? "Test sent to Android" : "Allow maintenance notifications in Android settings first")).show();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) { super.onRequestPermissionsResult(code, permissions, results); if (code == 812) GarageReminders.refresh(this); }
    private void export(String kind) {
        exportKind = kind;
        try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(kind.equals("backup") ? "application/json" : kind.equals("csv") ? "text/csv" : "text/html")
                .putExtra(Intent.EXTRA_TITLE, "SubaruBridge-" + (kind.equals("backup") ? "garage-" : kind + "-") + LocalDate.now() + (kind.equals("backup") ? ".json" : kind.equals("csv") ? ".csv" : ".html")), EXPORT); }
        catch (ActivityNotFoundException error) { toast("No file picker is available"); }
    }
    private void importBackup() {
        new AlertDialog.Builder(this).setTitle("Import garage backup?").setMessage("New records will be added. Existing records with the same IDs must match. A conflict or invalid record cancels the entire import.")
                .setPositiveButton("Choose backup", (d, w) -> { try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), IMPORT); } catch (ActivityNotFoundException error) { toast("No file picker is available"); } }).setNegativeButton("Cancel", null).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (request == IMPORT) perform(() -> { try (InputStream input = getContentResolver().openInputStream(uri)) { if (input == null) throw new IOException("Cannot open the selected backup"); return store.merge(OwnershipStore.readBounded(input, OwnershipStore.MAX_BACKUP)); } }, true);
        if (request == EXPORT) {
            final String kind = exportKind, vehicleId = selected;
            perform(() -> {
                JSONObject fresh = store.view(); byte[] bytes = kind.equals("backup") ? store.backup() : OwnershipExport.render(fresh, vehicleId, kind).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) { if (output == null) throw new IOException("Cannot write to the selected file"); output.write(bytes); }
                runOnUiThread(() -> { if (alive()) toast("Exported to your chosen file"); }); return fresh;
            }, false);
        }
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    private void openForm(String kind, String id, String item) { if (busy) return; formKind = kind; editingId = id; itemId = item; draftId = UUID.randomUUID().toString(); formZone = ZoneId.systemDefault(); restoredDraft = null; buildFormSafely(); }
    private void buildFormSafely() { try { buildForm(); } catch (Exception failure) { formKind = null; form = null; render(); showError(safeError(failure)); } }
    private void buildForm() throws Exception {
        beginPage(formKind.equals("vehicle") ? (editingId.isEmpty() ? "Add vehicle" : "Vehicle profile") : formKind.equals("interval") ? "Service interval" : formKind.equals("custom") ? "Owner maintenance item" : formKind.equals("preferences") ? "Reminder lead times" : editingId.isEmpty() ? "Log " + formKind : formKind.equals("reading") ? "Correct odometer" : "Edit " + formKind, "");
        form = new Form(); JSONObject existing = formKind.equals("vehicle") ? (editingId.isEmpty() ? new JSONObject() : vehicle) : formKind.equals("interval") ? new JSONObject() : find(collection(formKind), editingId);
        switch (formKind) { case "vehicle": vehicleForm(existing); break; case "reading": readingForm(existing); break; case "service": serviceForm(existing); break; case "fuel": fuelForm(existing); break; case "expense": expenseForm(existing); break; case "trip": tripForm(existing); break; case "interval": intervalForm(); break; case "custom": customForm(existing); break; case "preferences": preferencesForm(); break; }
        if (!editingId.isEmpty() && Arrays.asList("vehicle", "service", "fuel", "expense", "trip").contains(formKind)) {
            final String linkedKind = formKind.equals("vehicle") ? "vehicles" : collection(formKind), linkedId = editingId;
            button("Receipts & photos", View.NO_ID, () -> startActivity(new Intent(this, GarageDataActivity.class).putExtra("kind", linkedKind).putExtra("recordId", linkedId)));
        } else if (Arrays.asList("vehicle", "service", "fuel", "expense", "trip").contains(formKind)) note("Save this record first, then open it from History to attach receipts or photos.");
        if (restoredDraft != null) { form.restore(restoredDraft); restoredDraft = null; }
        form.refresh();
    }
    private final class Form {
        final Map<String, View> inputs = new LinkedHashMap<>(); final ArrayList<View> controls = new ArrayList<>();
        final ArrayList<Runnable> refreshers = new ArrayList<>();
        LinearLayout details;
        void fold(int start) { fold(start, "Vehicle details, units & maintenance"); }
        void fold(int start, String title) {
            details = new LinearLayout(GarageActivity.this); details.setOrientation(LinearLayout.VERTICAL); details.setVisibility(View.GONE);
            while (page.getChildCount() > start) { View view = page.getChildAt(start); page.removeViewAt(start); details.addView(view); }
            Button disclosure = button(title, R.id.garage_profile_details, () -> details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
            controls.add(disclosure); page.addView(details, new LinearLayout.LayoutParams(-1, -2));
        }
        EditText text(String key, String title, String value, boolean numeric, int id) {
            boolean multiline = key.equals("notes") || key.equals("warranty");
            note(title); EditText field = new EditText(GarageActivity.this); field.setText(value); field.setSingleLine(!multiline);
            field.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES : InputType.TYPE_TEXT_VARIATION_NORMAL));
            field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(multiline ? 2000 : 160)}); field.setTextColor(palette.text); field.setHintTextColor(palette.muted);
            field.setBackground(background(palette.surface, 10)); field.setPadding(dp(12), dp(12), dp(12), dp(12)); field.setId(id); field.setContentDescription(title);
            page.addView(field, new LinearLayout.LayoutParams(-1, -2)); inputs.put(key, field); controls.add(field); return field;
        }
        void text(String key, String title, String value) { text(key, title, value, false, View.NO_ID); }
        void number(String key, String title, String value, int id) { text(key, title, value, true, id); }
        CheckBox check(String key, String title, boolean value) { CheckBox check = new CheckBox(GarageActivity.this); check.setText(title); check.setTextColor(palette.text); check.setTextSize(16); check.setChecked(value); check.setMinHeight(dp(48)); page.addView(check); inputs.put(key, check); controls.add(check); return check; }
        String text(String key) { return ((EditText)inputs.get(key)).getText().toString().trim(); }
        boolean checked(String key) { return ((CheckBox)inputs.get(key)).isChecked(); }
        void save(TaskFactory factory) { controls.add(button("Save on this phone", R.id.garage_save, () -> { try { JSONObject command = factory.make(); showError(""); perform(() -> store.execute(command), true); } catch (Exception failure) { showError(safeError(failure)); } })); }
        void enable(boolean enabled) { for (View input : controls) input.setEnabled(enabled); }
        void refresh() { for (Runnable action : refreshers) action.run(); }
        void choices(JSONArray rules, JSONArray selectedItems, String first) throws Exception {
            String[] names = new String[rules.length()], keys = new String[rules.length()];
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i); String id = rule.getString("id"); keys[i] = "item-" + id; names[i] = rule.getString("title") + (rule.has("action") ? " · " + rule.getString("action") : "");
                CheckBox check = new CheckBox(GarageActivity.this); check.setChecked(id.equals(first));
                if (selectedItems != null) for (int j = 0; j < selectedItems.length(); j++) if (id.equals(selectedItems.getString(j))) check.setChecked(true);
                inputs.put(keys[i], check);
            }
            boolean drivingConditions = rules.length() > 0 && !rules.getJSONObject(0).has("action");
            Button choose = button(drivingConditions ? "Choose driving conditions" : "Choose completed work", drivingConditions ? View.NO_ID : R.id.garage_service_items, () -> {
                boolean[] choices = new boolean[keys.length]; for (int i = 0; i < keys.length; i++) choices[i] = checked(keys[i]);
                new AlertDialog.Builder(GarageActivity.this).setTitle(drivingConditions ? "Driving conditions" : "Completed service items").setMultiChoiceItems(names, choices, (dialog, index, checked) -> choices[index] = checked)
                        .setPositiveButton("Done", (dialog, which) -> { for (int i = 0; i < keys.length; i++) ((CheckBox)inputs.get(keys[i])).setChecked(choices[i]); refresh(); }).setNegativeButton("Cancel", null).show();
            });
            controls.add(choose); refreshers.add(() -> { int count = 0; for (String key : keys) if (checked(key)) count++; choose.setText(count == 0 ? (drivingConditions ? "Choose driving conditions" : "Choose completed work") : quantity(count, drivingConditions ? "driving condition" : "service item") + " selected"); });
        }
        Bundle draft() { Bundle out = new Bundle(); for (Map.Entry<String, View> entry : inputs.entrySet()) { if (entry.getValue() instanceof EditText) out.putString(entry.getKey(), ((EditText)entry.getValue()).getText().toString()); else out.putBoolean(entry.getKey(), ((CheckBox)entry.getValue()).isChecked()); } if (details != null) out.putBoolean("_details", details.getVisibility() == View.VISIBLE); return out; }
        void restore(Bundle values) { for (Map.Entry<String, View> entry : inputs.entrySet()) if (values.containsKey(entry.getKey())) { if (entry.getValue() instanceof EditText) ((EditText)entry.getValue()).setText(values.getString(entry.getKey(), "")); else ((CheckBox)entry.getValue()).setChecked(values.getBoolean(entry.getKey())); } if (details != null) details.setVisibility(values.getBoolean("_details") ? View.VISIBLE : View.GONE); }
    }
    private interface TaskFactory { JSONObject make() throws Exception; }
    private JSONObject record(JSONObject existing) throws Exception { return new JSONObject(existing.toString()).put("id", editingId.isEmpty() || formKind.equals("reading") ? draftId : editingId).put("vehicleId", selected); }
    private JSONObject command(String add, String update, String key, JSONObject value) throws Exception { return new JSONObject().put("type", editingId.isEmpty() ? add : update).put(key, value); }
    private void notes(JSONObject existing) { form.text("notes", "Notes (optional)", existing.optString("notes")); }
    private JSONArray selectedConditions() throws Exception {
        JSONArray chosen = new JSONArray(), known = snapshot.getJSONObject("schedule").getJSONArray("conditions");
        for (int i=0;i<known.length();i++) { String id=known.getJSONObject(i).getString("id"); if (form.checked("item-"+id)) chosen.put(id); } return chosen;
    }
    private void vehicleForm(JSONObject existing) throws Exception {
        boolean fresh = editingId.isEmpty();
        form.text("nickname", "Vehicle name", existing.optString("nickname", "My Legacy"), false, R.id.garage_vehicle_name);
        form.text("make", "Make", existing.optString("make", "Subaru")); form.text("model", "Model", existing.optString("model", "Legacy"));
        form.number("year", "Model year", existing.optString("year", "2017"), View.NO_ID);
        int detailsStart = page.getChildCount();
        form.text("trim", "Trim", existing.optString("trim", "2.5i Premium"));
        form.text("engine", "Engine", existing.optString("engine", "2.5L")); form.text("transmission", "Transmission", existing.optString("transmission", "CVT")); form.text("drivetrain", "Drivetrain", existing.optString("drivetrain", "AWD"));
        form.check("schedule", "Use verified US 2017 Legacy 2.5 CVT schedule", fresh || !existing.isNull("maintenanceProfile"));
        form.check("metric", "Use kilometres and litres", existing.optString("units").equals("metric"));
        form.text("currency", "Currency (USD, CAD, EUR, GBP, AUD or NZD)", existing.optString("currency", "USD"));
        boolean legacySevere=existing.optBoolean("severe") && (existing.optJSONArray("severeConditions")==null || existing.optJSONArray("severeConditions").length()==0);
        CheckBox legacyFlag=form.check("severe", "Retain unresolved broad severe-service flag",legacySevere);if(!legacySevere)legacyFlag.setVisibility(View.GONE);
        JSONArray conditions = snapshot.getJSONObject("schedule").getJSONArray("conditions");
        form.choices(conditions, existing.optJSONArray("severeConditions"), "");
        note("Choose the driving conditions that apply. Subaru’s 2017 notes change only the listed components; an old broad severe flag keeps calculations unknown until specific conditions are chosen.");
        form.text("vin", "VIN (optional, stays local)", existing.isNull("vin") ? "" : existing.optString("vin")); form.text("plate", "License plate (optional, stays local)", existing.isNull("plate") ? "" : existing.optString("plate"));
        form.text("purchaseDate", "Purchase date (YYYY-MM-DD, optional)", existing.isNull("purchaseDate") ? "" : existing.optString("purchaseDate"));
        form.text("inServiceDate", "First in-service date (YYYY-MM-DD, optional)", existing.isNull("inServiceDate") ? "" : existing.optString("inServiceDate"));
        boolean oldMetric = existing.optString("units").equals("metric");
        String purchase = existing.isNull("purchaseMileageMm") ? "" : BigDecimal.valueOf(existing.optLong("purchaseMileageMm")).divide(new BigDecimal(oldMetric ? "1000000" : "1609344"), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        form.number("purchaseMileage", "Purchase odometer (" + (oldMetric ? "km" : "mi") + ", optional; uses the units shown here)", purchase, View.NO_ID);
        form.number("purchasePrice", "Purchase price (optional)", existing.isNull("purchasePriceCents") ? "" : BigDecimal.valueOf(existing.optLong("purchasePriceCents"), 2).toPlainString(), View.NO_ID);
        form.check("archived", "Archive this vehicle", existing.optBoolean("archived")); notes(existing);
        form.fold(detailsStart);
        form.save(() -> {
            String currency = form.text("currency").toUpperCase(Locale.ROOT); if (!Arrays.asList("USD", "CAD", "EUR", "GBP", "AUD", "NZD").contains(currency)) throw new IllegalArgumentException("Choose a supported currency with two decimal places");
            JSONObject value = new JSONObject(existing.toString()).put("id", fresh ? draftId : editingId).put("nickname", form.text("nickname"))
                    .put("make", form.text("make")).put("model", form.text("model")).put("year", Integer.parseInt(form.text("year"))).put("trim", form.text("trim"))
                    .put("engine", form.text("engine")).put("transmission", form.text("transmission")).put("drivetrain", form.text("drivetrain"))
                    .put("units", form.checked("metric") ? "metric" : "us").put("currency", currency).put("severe", form.checked("severe") && selectedConditions().length()==0).put("severeConditions", selectedConditions()).put("archived", form.checked("archived"))
                    .put("vin", form.text("vin").isEmpty() ? JSONObject.NULL : form.text("vin").toUpperCase(Locale.ROOT)).put("plate", form.text("plate").isEmpty() ? JSONObject.NULL : form.text("plate"))
                    .put("maintenanceProfile", form.checked("schedule") ? "subaru-us-2017-legacy-25-cvt" : JSONObject.NULL).put("notes", form.text("notes"))
                    .put("purchaseDate", form.text("purchaseDate").isEmpty() ? JSONObject.NULL : day(form.text("purchaseDate"), false))
                    .put("inServiceDate", form.text("inServiceDate").isEmpty() ? JSONObject.NULL : day(form.text("inServiceDate"), false))
                    .put("purchaseMileageMm", form.text("purchaseMileage").isEmpty() ? JSONObject.NULL : OwnershipNumbers.distance(form.text("purchaseMileage"), oldMetric))
                    .put("purchasePriceCents", form.text("purchasePrice").isEmpty() ? JSONObject.NULL : OwnershipNumbers.cents(form.text("purchasePrice")));
            if (fresh) selected = value.getString("id"); return command("addVehicle", "updateVehicle", "vehicle", value);
        });
    }
    private void readingForm(JSONObject existing) throws Exception {
        form.number("distance", "Odometer (" + unit() + ")", editingId.isEmpty() ? "" : inputDistance(existing.getLong("distanceMm")), R.id.garage_distance);
        form.text("at", "Reading time (" + formZone + ")", formWhen(editingId.isEmpty() ? System.currentTimeMillis() : existing.getLong("atMs")), false, R.id.garage_time);
        if (!editingId.isEmpty()) note("Correcting the reading from " + when(existing.getLong("atMs")) + ". The original stays in history.");
        form.check("approximate", "This reading is approximate", existing.optString("confidence").equals("approximate")); notes(editingId.isEmpty() ? existing : new JSONObject());
        if (!editingId.isEmpty()) note("A correction reason is required in Notes.");
        form.save(() -> {
            if (!editingId.isEmpty() && form.text("notes").isEmpty()) throw new IllegalArgumentException("Add a correction reason in Notes");
            JSONObject value = record(new JSONObject()).put("distanceMm", OwnershipNumbers.distance(form.text("distance"), metric())).put("atMs", timestamp(form.text("at")))
                    .put("source", "manual").put("confidence", form.checked("approximate") ? "approximate" : "confirmed").put("notes", form.text("notes"));
            if (!editingId.isEmpty()) value.put("supersedes", editingId); return new JSONObject().put("type", "addReading").put("reading", value);
        });
    }
    private void serviceForm(JSONObject existing) throws Exception {
        JSONArray rules = availableRules(), selectedItems = existing.optJSONArray("itemIds");
        note("Select the work completed. For a historical estimate, leave the date blank and enter a mileage range.");
        form.choices(rules, selectedItems, itemId);
        form.text("date", "Service date (YYYY-MM-DD; may be blank)", existing.isNull("date") ? (editingId.isEmpty() ? LocalDate.now().toString() : "") : existing.optString("date"), false, R.id.garage_date);
        JSONObject range = existing.optJSONObject("mileage");
        form.number("low", "Service odometer / lower estimate (" + unit() + ", optional)", range == null ? "" : inputDistance(range.getLong("lowMm")), R.id.garage_service_low);
        form.number("high", "Upper estimate (" + unit() + "; leave blank for an exact reading)", range == null || range.getLong("lowMm") == range.getLong("highMm") ? "" : inputDistance(range.getLong("highMm")), R.id.garage_service_high);
        form.number("cost", "Total paid (" + vehicle.optString("currency") + "; optional)", editingId.isEmpty() ? "" : BigDecimal.valueOf(existing.optLong("costCents"), 2).toPlainString(), R.id.garage_cost);
        form.text("provider", "Service provider (optional)", existing.optString("provider"));
        int extraStart = page.getChildCount();
        form.check("diy", "DIY service", existing.optBoolean("diy"));
        JSONArray partNumbers = existing.optJSONArray("partNumbers"); List<String> parts = new ArrayList<>(); if (partNumbers != null) for (int i = 0; i < partNumbers.length(); i++) parts.add(partNumbers.getString(i));
        form.text("partNumbers", "Part numbers (comma separated, optional)", String.join(", ", parts));
        JSONObject breakdown = existing.optJSONObject("costBreakdown");
        for (String key : new String[]{"parts", "labor", "tax"}) form.number(key, key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1) + " cost (optional)", breakdown == null ? "" : BigDecimal.valueOf(breakdown.optLong(key + "Cents"), 2).toPlainString(), View.NO_ID);
        form.text("warranty", "Warranty information (optional)", existing.optString("warranty"));
        note("If you enter a cost breakdown, parts + labor + tax must equal the total. Leave Total paid blank to use their sum. Part numbers are owner records; compatibility is not inferred.");
        form.fold(extraStart, "Parts, labor & warranty"); notes(existing);
        form.save(() -> {
            JSONArray items = new JSONArray(); for (int i = 0; i < rules.length(); i++) { String id = rules.getJSONObject(i).getString("id"); if (form.checked("item-" + id)) items.put(id); }
            String date = day(form.text("date"), true); JSONObject mileage = null;
            if (!form.text("high").isEmpty() && form.text("low").isEmpty()) throw new IllegalArgumentException("Enter the lower estimate first");
            if (!form.text("low").isEmpty()) { long low = OwnershipNumbers.distance(form.text("low"), metric()); mileage = new JSONObject().put("lowMm", low).put("highMm", form.text("high").isEmpty() ? low : OwnershipNumbers.distance(form.text("high"), metric())); }
            JSONObject costs = null;
            if (!form.text("parts").isEmpty() || !form.text("labor").isEmpty() || !form.text("tax").isEmpty()) costs = new JSONObject().put("partsCents", OwnershipNumbers.cents(form.text("parts"))).put("laborCents", OwnershipNumbers.cents(form.text("labor"))).put("taxCents", OwnershipNumbers.cents(form.text("tax")));
            long total = form.text("cost").isEmpty() && costs != null ? Math.addExact(Math.addExact(costs.getLong("partsCents"), costs.getLong("laborCents")), costs.getLong("taxCents")) : OwnershipNumbers.cents(form.text("cost"));
            JSONArray savedParts = new JSONArray(); for (String part : form.text("partNumbers").split(",")) if (!part.trim().isEmpty()) savedParts.put(part.trim());
            JSONObject value = record(existing).put("itemIds", items).put("date", date.isEmpty() ? JSONObject.NULL : date).put("mileage", mileage == null ? JSONObject.NULL : mileage)
                    .put("costCents", total).put("provider", form.text("provider")).put("notes", form.text("notes")).put("diy", form.checked("diy")).put("costBreakdown", costs == null ? JSONObject.NULL : costs).put("partNumbers", savedParts).put("warranty", form.text("warranty"));
            return command("addService", "updateService", "service", value);
        });
    }
    private void fuelForm(JSONObject existing) throws Exception {
        form.text("at", "Fill-up time (" + formZone + ")", formWhen(editingId.isEmpty() ? System.currentTimeMillis() : existing.getLong("atMs")), false, R.id.garage_time);
        form.number("distance", "Odometer (" + unit() + ")", editingId.isEmpty() ? "" : inputDistance(existing.getLong("distanceMm")), R.id.garage_distance);
        String volume = editingId.isEmpty() ? "" : BigDecimal.valueOf(existing.getLong("volumeMl")).divide(new BigDecimal(metric() ? "1000" : "3785.411784"), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        form.number("volume", "Fuel purchased (" + (metric() ? "litres" : "US gallons") + ")", volume, R.id.garage_volume);
        form.number("cost", "Total paid (" + vehicle.optString("currency") + ")", editingId.isEmpty() ? "" : BigDecimal.valueOf(existing.optLong("costCents"), 2).toPlainString(), R.id.garage_cost);
        form.check("full", "Tank filled completely", existing.optBoolean("full", true)); form.check("missed", "A fill-up was missed since the previous entry", existing.optBoolean("missedFill")); notes(existing);
        if (editingId.isEmpty()) form.check("odometer", "Also save a confirmed odometer reading", false);
        note("Partial fills contribute to the next complete full-to-full cycle. A missed fill starts a new measurement. A saved odometer is an independent historical reading; correct it through History if needed.");
        form.save(() -> {
            JSONObject entry = record(existing).put("atMs", timestamp(form.text("at"))).put("distanceMm", OwnershipNumbers.distance(form.text("distance"), metric()))
                    .put("volumeMl", OwnershipNumbers.volume(form.text("volume"), metric())).put("costCents", OwnershipNumbers.cents(form.text("cost"))).put("full", form.checked("full")).put("missedFill", form.checked("missed")).put("notes", form.text("notes"));
            if (editingId.isEmpty() && form.checked("odometer")) {
                JSONObject reading = new JSONObject().put("id", draftId + "-odometer").put("vehicleId", selected).put("atMs", entry.getLong("atMs")).put("distanceMm", entry.getLong("distanceMm"))
                        .put("confidence", "confirmed").put("source", "manual").put("notes", "Odometer entered with fuel record " + draftId);
                return new JSONObject().put("type", "recordFillUp").put("entry", entry).put("reading", reading);
            }
            return command("addFuel", "updateFuel", "entry", entry);
        });
    }
    private void expenseForm(JSONObject existing) throws Exception {
        form.text("category", "Category (insurance, repairs, parking…)", existing.optString("category"), false, R.id.garage_category);
        form.text("date", "Date (YYYY-MM-DD)", existing.optString("date", LocalDate.now().toString()), false, R.id.garage_date);
        form.number("cost", "Total paid (" + vehicle.optString("currency") + ")", editingId.isEmpty() ? "" : BigDecimal.valueOf(existing.optLong("costCents"), 2).toPlainString(), R.id.garage_cost);
        form.check("financing", "Financing / loan payment", existing.optBoolean("financing")); notes(existing); note("Fuel and service costs are already counted. Add other expenses here.");
        form.save(() -> command("addExpense", "updateExpense", "expense", record(existing).put("date", day(form.text("date"), false)).put("category", form.text("category")).put("costCents", OwnershipNumbers.cents(form.text("cost"))).put("financing", form.checked("financing")).put("notes", form.text("notes"))));
    }
    private void tripForm(JSONObject existing) throws Exception {
        form.text("start", "Trip start (" + formZone + ")", formWhen(editingId.isEmpty() ? System.currentTimeMillis() - 3600000 : existing.getLong("startedMs")), false, R.id.garage_trip_start);
        form.text("end", "Trip end", formWhen(editingId.isEmpty() ? System.currentTimeMillis() : existing.getLong("endedMs")), false, R.id.garage_trip_end);
        form.number("distance", "Completed distance (" + unit() + ")", editingId.isEmpty() ? "" : inputDistance(existing.getLong("distanceMm")), R.id.garage_distance); notes(existing);
        note("Manual trip entry. Completed trips after your latest odometer reading contribute to estimated mileage. Overlaps are rejected. This does not record GPS or read the car's odometer.");
        form.save(() -> command("addTrip", "updateTrip", "trip", record(existing).put("startedMs", timestamp(form.text("start"))).put("endedMs", timestamp(form.text("end"))).put("distanceMm", OwnershipNumbers.distance(form.text("distance"), metric())).put("source", existing.optString("source", "manual")).put("notes", form.text("notes"))));
    }
    private void intervalForm() throws Exception {
        JSONObject rule = rule(itemId), interval = rule.getJSONObject("normal"); boolean disabled = false; JSONArray overrides = state.getJSONArray("overrides");
        boolean owner = !find("customItems", itemId).optString("id").isEmpty();
        for (int i = 0; i < overrides.length(); i++) { JSONObject value = overrides.getJSONObject(i); if (selected.equals(value.getString("vehicleId")) && itemId.equals(value.getString("itemId"))) { interval = value.getJSONObject("interval"); disabled = value.getBoolean("disabled"); } }
        heading(rule.getString("title")); note((owner ? "This is an owner-defined interval. " : "An owner interval overrides the manufacturer interval. ") + "Distances here are whole miles. Leave one threshold blank to use only the other.");
        form.number("miles", "Interval (whole miles)", interval.isNull("miles") ? "" : interval.optString("miles"), View.NO_ID);
        form.number("months", "Interval (whole months)", interval.isNull("months") ? "" : interval.optString("months"), View.NO_ID);
        form.check("disabled", "Disable this maintenance item", disabled);
        JSONObject source = snapshot.getJSONObject("schedule");
        if (owner) { note("Owner-defined interval. " + rule.optString("notes")); button("Edit maintenance item", R.id.garage_edit_item, () -> openForm("custom", itemId, "")); }
        else {
            note("Manufacturer source · " + source.getString("sourcePage") + " · row " + rule.getString("sourceRow") + " · verified " + source.getString("verifiedDate")); note(source.getString("limitations"));
            button("Open manufacturer source", View.NO_ID, () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(source.optString("sourceUrl")))));
        }
        form.save(() -> {
            JSONObject value = new JSONObject().put("vehicleId", selected).put("itemId", itemId).put("disabled", form.checked("disabled"))
                    .put("interval", new JSONObject().put("miles", form.text("miles").isEmpty() ? JSONObject.NULL : Long.parseLong(form.text("miles"))).put("months", form.text("months").isEmpty() ? JSONObject.NULL : Integer.parseInt(form.text("months"))));
            return new JSONObject().put("type", "setOverride").put("value", value);
        });
        button(owner ? "Restore item interval" : "Restore manufacturer interval", R.id.garage_reset_interval, () -> { try { JSONObject command = new JSONObject().put("type", "resetOverride").put("vehicleId", selected).put("itemId", itemId); perform(() -> store.execute(command), true); } catch (Exception failure) { showError(safeError(failure)); } });
        button("Snooze reminders for 7 days", R.id.garage_snooze, () -> snooze(7));
        button("Snooze reminders for 30 days", View.NO_ID, () -> snooze(30));
        button("Clear reminder snooze", View.NO_ID, () -> snooze(0));
        note("Snoozing only affects notifications. The maintenance status and calculation remain visible.");
    }
    private void snooze(int days) {
        try {
            JSONObject command = new JSONObject().put("type", days == 0 ? "clearSnooze" : "snoozeReminder");
            if (days == 0) command.put("vehicleId", selected).put("itemId", itemId);
            else command.put("value", new JSONObject().put("vehicleId", selected).put("itemId", itemId).put("until", LocalDate.now().plusDays(days).toString()));
            perform(() -> store.execute(command), true);
        } catch (Exception error) { showError(safeError(error)); }
    }
    private void customForm(JSONObject existing) throws Exception {
        form.text("title", "Maintenance name", existing.optString("title"), false, R.id.garage_item_title);
        form.text("action", "Action: replace, inspect, renew or service", existing.optString("action", "service"));
        JSONObject interval = existing.optJSONObject("interval");
        form.number("miles", "Interval (whole miles; may be blank)", interval == null || interval.isNull("miles") ? "" : interval.optString("miles"), R.id.garage_item_miles);
        form.number("months", "Interval (whole months; may be blank)", interval == null || interval.isNull("months") ? "" : interval.optString("months"), R.id.garage_item_months);
        notes(existing); note("Choose at least one interval. This is your own maintenance schedule, not a manufacturer requirement. Use it for batteries, wipers, tires, registration, inspections or other work.");
        form.save(() -> {
            JSONObject chosen = new JSONObject().put("miles", form.text("miles").isEmpty() ? JSONObject.NULL : Long.parseLong(form.text("miles")))
                    .put("months", form.text("months").isEmpty() ? JSONObject.NULL : Integer.parseInt(form.text("months")));
            JSONObject item = record(existing).put("title", form.text("title")).put("action", form.text("action").toLowerCase(Locale.ROOT)).put("notes", form.text("notes")).put("interval", chosen);
            return command("addCustomItem", "updateCustomItem", "item", item);
        });
    }
    private void preferencesForm() throws Exception {
        JSONObject prefs = state.getJSONObject("preferences");
        form.number("distance", "Due-soon lead distance (" + unit() + ")", inputDistance(prefs.getLong("dueSoonMm")), R.id.garage_distance);
        form.number("days", "Due-soon lead time (days)", prefs.getString("dueSoonDays"), R.id.garage_lead_days);
        note("These lead times apply to all vehicles and are included in garage backups. They change the Due soon window; the actual service interval remains the same.");
        form.save(() -> new JSONObject().put("type", "setPreferences").put("preferences", new JSONObject().put("dueSoonMm", OwnershipNumbers.distance(form.text("distance"), metric())).put("dueSoonDays", Integer.parseInt(form.text("days")))));
    }
}
