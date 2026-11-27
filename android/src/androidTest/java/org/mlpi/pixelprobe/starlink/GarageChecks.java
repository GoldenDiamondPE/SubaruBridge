package org.mlpi.pixelprobe.starlink;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.mlpi.pixelprobe.R;
import org.mlpi.pixelprobe.ownership.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated private files only. Does not seed, replace or export the owner's real garage. */
final class GarageChecks {
    private static void check(boolean okay, String message) { if (!okay) throw new AssertionError(message); }
    private interface Action { void run() throws Exception; }
    private static void rejected(Action action) throws Exception { try { action.run(); throw new AssertionError("Invalid garage operation succeeded"); } catch (IOException | org.json.JSONException | IllegalArgumentException expected) {} }
    private static JSONObject car(String id) throws Exception { return new JSONObject().put("id", id).put("nickname", "Synthetic QA car").put("make", "Subaru").put("model", "Legacy").put("year", 2017).put("engine", "2.5L").put("transmission", "CVT").put("maintenanceProfile", "subaru-us-2017-legacy-25-cvt"); }
    private static JSONObject cmd(String type, String key, JSONObject record) throws Exception { return new JSONObject().put("type", type).put(key, record); }
    private static JSONObject expense(String id, String car) throws Exception { return new JSONObject().put("id", id).put("vehicleId", car).put("date", "2026-11-01").put("category", "Parking").put("costCents", 100).put("notes", "=QA_FORMULA"); }
    static void run(Instrumentation instrumentation, Context context, Bundle result) throws Exception {
        File folder = new File(context.getCacheDir(), "garage-qa-" + UUID.randomUUID()); check(folder.mkdir(), "Fixture directory unavailable");
        try {
            persistence(folder); ui(instrumentation, context, folder);
            result.putString("garage", "PASS: restart, interrupted/rejected writes, corrupt/future/deep document preservation, backup round-trip/conflict, concurrent saves, escaped exports, unit/time-zone conversion, native forms and read-only analytics");
        } finally {
            GarageActivity.testStore = null;
            context.getSharedPreferences("garage-qa-selection", Context.MODE_PRIVATE).edit().clear().commit();
            File[] files = folder.listFiles(); if (files != null) for (File file : files) check(file.isFile() && file.delete(), "Fixture cleanup failed");
            check(folder.delete(), "Fixture folder cleanup failed");
        }
    }
    private static void persistence(File folder) throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-11-27T12:00:00Z"), ZoneOffset.UTC); File file = new File(folder, "garage.json");
        OwnershipStore store = new OwnershipStore(file, clock); check(store.view().getJSONObject("state").getJSONArray("vehicles").length() == 0, "Fresh store is not empty");
        store.execute(cmd("addVehicle", "vehicle", car("car")));
        check(new OwnershipStore(file, clock).view().getJSONObject("state").getJSONArray("vehicles").length() == 1, "Restart lost the vehicle");
        store.execute(cmd("addExpense", "expense", expense("e", "car"))); byte[] original = Files.readAllBytes(file.toPath());
        rejected(() -> store.execute(cmd("addExpense", "expense", expense("e", "car")))); check(Arrays.equals(original, Files.readAllBytes(file.toPath())), "Rejected duplicate rewrote local data");
        android.util.AtomicFile interrupted = new android.util.AtomicFile(file);
        try (FileOutputStream partial = interrupted.startWrite()) { partial.write("partial interrupted save".getBytes(StandardCharsets.UTF_8)); }
        check(new OwnershipStore(file, clock).view().getJSONObject("state").getJSONArray("expenses").length() == 1 && Arrays.equals(original, Files.readAllBytes(file.toPath())), "Interrupted save did not recover the last committed state");
        File blocked = new File(folder, "blocked"); Files.write(blocked.toPath(), new byte[]{7}); OwnershipStore unwritable = new OwnershipStore(new File(blocked, "garage.json"), clock);
        rejected(() -> unwritable.execute(cmd("addVehicle", "vehicle", car("blocked-car")))); check(Arrays.equals(Files.readAllBytes(blocked.toPath()), new byte[]{7}), "Failed save changed its existing parent");
        JSONObject newer = car("car").put("currency", "CAD"); rejected(() -> store.execute(cmd("updateVehicle", "vehicle", newer))); check(Arrays.equals(original, Files.readAllBytes(file.toPath())), "Currency relabel changed costs");
        byte[] backup = store.backup(); OwnershipStore restored = new OwnershipStore(new File(folder, "restored.json"), clock);
        restored.merge(backup); long revision = restored.view().getJSONObject("state").getLong("revision"); restored.merge(backup);
        check(restored.view().getJSONObject("state").getLong("revision") == revision, "Repeated backup created duplicates");
        JSONObject conflict = OwnershipStore.parse(backup); conflict.getJSONObject("garage").getJSONArray("vehicles").getJSONObject(0).put("nickname", "Conflicting name");
        rejected(() -> store.merge(conflict.toString().getBytes(StandardCharsets.UTF_8))); check(Arrays.equals(original, Files.readAllBytes(file.toPath())), "Conflicting backup partially applied");
        rejected(() -> store.merge((new String(backup, StandardCharsets.UTF_8) + " trailing").getBytes(StandardCharsets.UTF_8)));
        rejected(() -> OwnershipStore.parse(("[".repeat(1000) + "0" + "]".repeat(1000)).getBytes(StandardCharsets.UTF_8)));
        rejected(() -> OwnershipStore.parse("{'not':'json'}".getBytes(StandardCharsets.UTF_8)));
        check(OwnershipStore.parse(("\ufeff" + new String(backup, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)).getString("format").equals("org.subarubridge.garage"), "UTF-8 BOM compatibility failed");
        JSONObject future = OwnershipStore.parse(backup); future.getJSONObject("garage").put("schemaVersion", 2); rejected(() -> store.merge(future.toString().getBytes(StandardCharsets.UTF_8)));
        File bad = new File(folder, "bad.json"); byte[] invalid = new byte[]{(byte)0xff, 1, 2}; Files.write(bad.toPath(), invalid);
        OwnershipStore broken = new OwnershipStore(bad, clock); rejected(() -> broken.view()); rejected(() -> broken.execute(cmd("addVehicle", "vehicle", car("new")))); check(Arrays.equals(invalid, Files.readAllBytes(bad.toPath())), "Corrupt data overwritten");
        byte[] schema = "{\"schemaVersion\":2,\"revision\":0}".getBytes(StandardCharsets.UTF_8); Files.write(bad.toPath(), schema); rejected(() -> broken.view()); check(Arrays.equals(schema, Files.readAllBytes(bad.toPath())), "Future schema overwritten");
        ExecutorService workers = Executors.newFixedThreadPool(4); List<Future<?>> jobs = new ArrayList<>();
        try { for (int i = 0; i < 16; i++) { final int index = i; jobs.add(workers.submit(() -> { new OwnershipStore(file, clock).execute(cmd("addExpense", "expense", expense("parallel-" + index, "car"))); return null; })); } for (Future<?> job : jobs) job.get(15, TimeUnit.SECONDS); }
        finally { workers.shutdownNow(); }
        JSONObject snapshot = store.view(); check(snapshot.getJSONObject("state").getJSONArray("expenses").length() == 17, "Concurrent save lost a record");
        check(snapshot.getJSONArray("summaries").getJSONObject(0).getJSONObject("costs").getLong("otherOperatingCents") == 1700, "Cost total wrong after concurrent saves");
        String csv = OwnershipExport.render(snapshot, "car", "csv"); check(csv.contains("'=QA_FORMULA") && csv.contains("cost_hundredths"), "Spreadsheet formula escaping or units missing");
        JSONObject service = new JSONObject().put("id", "service").put("vehicleId", "car").put("itemIds", new JSONArray().put("engine-oil")).put("date", "2026-11-01").put("provider", "<script>alert(1)</script>").put("notes", "&<>");
        store.execute(cmd("addService", "service", service)); String report = OwnershipExport.render(store.view(), "car", "report"); check(!report.contains("<script>") && report.contains("&lt;script&gt;") && report.contains("Mileage unknown"), "Offline report escaping or uncertainty failed");
        check(GarageReminders.counts(store.view())[0] == 0, "Unknown history generated a reminder");
        JSONObject overdue = new JSONObject(service.toString()).put("date", "2026-01-01"); store.execute(cmd("updateService", "service", overdue)); check(GarageReminders.counts(store.view())[0] == 1, "Time-based overdue item did not reach reminder summary");
        store.execute(cmd("snoozeReminder", "value", new JSONObject().put("vehicleId", "car").put("itemId", "engine-oil").put("until", "2026-12-04"))); check(GarageReminders.counts(store.view())[0] == 0, "Snoozed item generated a reminder");
        store.execute(new JSONObject().put("type", "clearSnooze").put("vehicleId", "car").put("itemId", "engine-oil")); check(GarageReminders.counts(store.view())[0] == 1, "Cleared snooze still hid a reminder");
        store.execute(cmd("updateVehicle", "vehicle", car("car").put("archived", true))); check(GarageReminders.counts(store.view())[0] == 0, "Archived vehicle generated reminders");
        check(OwnershipNumbers.distance("1", false) == 1609344 && OwnershipNumbers.distance("1", true) == 1000000 && OwnershipNumbers.volume("1", false) == 3785 && OwnershipNumbers.cents("0.29") == 29, "Unit/money conversion wrong");
        Locale originalLocale = Locale.getDefault();
        try { Locale.setDefault(Locale.JAPAN); check(OwnershipNumbers.money(7450, "USD").contains("74.50"), "Phone locale rounded away recorded cents"); }
        finally { Locale.setDefault(originalLocale); }
        rejected(() -> OwnershipNumbers.cents("0.001")); rejected(() -> OwnershipNumbers.distance("NaN", false)); rejected(() -> OwnershipNumbers.distance("-1", false));
        ZoneId zone = ZoneId.of("America/New_York");
        rejected(() -> OwnershipTime.parse("2026-03-08 02:30", zone)); rejected(() -> OwnershipTime.parse("2026-11-01 01:30", zone));
        long first = OwnershipTime.parse("2026-11-01 01:30 -04:00", zone), second = OwnershipTime.parse("2026-11-01 01:30 -05:00", zone);
        check(second - first == 3600000 && OwnershipTime.parse(OwnershipTime.format(second, zone), zone) == second, "Repeated-hour time was silently changed");
        rejected(() -> OwnershipTime.parse("2026-11-01 01:30 -06:00", zone));
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            java.lang.reflect.Constructor<OwnershipStore> constructor = OwnershipStore.class.getDeclaredConstructor(File.class, Clock.class, boolean.class); constructor.setAccessible(true);
            OwnershipStore travelling = constructor.newInstance(new File(folder, "timezone.json"), Clock.fixed(Instant.parse("2026-11-27T00:30:00Z"), ZoneOffset.UTC), true);
            travelling.execute(cmd("addVehicle", "vehicle", car("clock-car")));
            JSONObject visit = new JSONObject().put("id", "clock-service").put("vehicleId", "clock-car").put("itemIds", new JSONArray().put("engine-oil")).put("date", "2026-11-27");
            rejected(() -> travelling.execute(cmd("addService", "service", visit)));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
            travelling.execute(cmd("addService", "service", visit));
            check(travelling.view().getJSONObject("state").getJSONArray("services").length() == 1, "Existing store retained an outdated phone time zone");
        } finally { TimeZone.setDefault(originalZone); }
    }
    private static View await(Activity screen, Instrumentation instrumentation, int id) throws Exception {
        final View[] found = new View[1]; long until = System.currentTimeMillis() + 12000;
        while (System.currentTimeMillis() < until) {
            instrumentation.runOnMainSync(() -> found[0] = screen.findViewById(id)); if (found[0] != null) return found[0]; Thread.sleep(60);
        }
        throw new AssertionError("Garage screen missing view " + id);
    }
    private static void click(Activity screen, Instrumentation instrumentation, int id) throws Exception { await(screen, instrumentation, id); instrumentation.runOnMainSync(() -> check(screen.findViewById(id).performClick(), "Control did not accept tap")); }
    private static void type(Activity screen, Instrumentation instrumentation, int id, String text) throws Exception { await(screen, instrumentation, id); instrumentation.runOnMainSync(() -> ((EditText)screen.findViewById(id)).setText(text)); }
    private static void ui(Instrumentation instrumentation, Context context, File folder) throws Exception {
        GarageActivity.testStore = new OwnershipStore(new File(folder, "ui.json"), Clock.systemDefaultZone());
        Activity screen = instrumentation.startActivitySync(new Intent(context, GarageActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            click(screen, instrumentation, R.id.garage_add_vehicle); type(screen, instrumentation, R.id.garage_vehicle_name, "Synthetic Garage QA"); click(screen, instrumentation, R.id.garage_save);
            await(screen, instrumentation, R.id.garage_log_odometer); click(screen, instrumentation, R.id.garage_log_odometer); type(screen, instrumentation, R.id.garage_distance, "81000"); click(screen, instrumentation, R.id.garage_save);
            await(screen, instrumentation, R.id.garage_mileage);
            instrumentation.runOnMainSync(() -> check(((TextView)screen.findViewById(R.id.garage_mileage)).getText().toString().equals("81000 mi"), "Saved odometer did not reach native overview"));
            click(screen, instrumentation, R.id.garage_maintenance); click(screen, instrumentation, R.id.garage_history); click(screen, instrumentation, R.id.garage_more);
            await(screen, instrumentation, R.id.garage_csv); check(GarageActivity.testStore.view().getJSONObject("state").getJSONArray("readings").length() == 1, "UI save did not persist");
            click(screen, instrumentation, R.id.garage_maintenance); click(screen, instrumentation, R.id.garage_add_item); type(screen, instrumentation, R.id.garage_item_title, "Synthetic registration"); type(screen, instrumentation, R.id.garage_item_months, "12"); click(screen, instrumentation, R.id.garage_save);
            await(screen, instrumentation, R.id.garage_add_item); check(GarageActivity.testStore.view().getJSONObject("state").getJSONArray("customItems").length() == 1, "Custom maintenance form did not persist");
            long revision = GarageActivity.testStore.view().getJSONObject("state").getLong("revision");
            click(screen, instrumentation, R.id.garage_more); click(screen, instrumentation, R.id.garage_analytics); await(screen, instrumentation, R.id.garage_chart_mode);
            check(GarageActivity.testStore.view().getJSONObject("state").getLong("revision") == revision, "Analytics changed saved history");
        } finally { instrumentation.runOnMainSync(screen::finish); GarageActivity.testStore = null; }
    }
}
