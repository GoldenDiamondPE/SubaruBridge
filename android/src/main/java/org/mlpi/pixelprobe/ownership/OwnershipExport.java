package org.mlpi.pixelprobe.ownership;

import org.json.*;
import java.time.Instant;
import java.util.*;

/** Explicit owner exports only. Values are escaped for spreadsheets and offline HTML. */
public final class OwnershipExport {
    private OwnershipExport() {}
    public static String render(JSONObject snapshot, String vehicleId, String kind) throws Exception {
        JSONObject state = snapshot.getJSONObject("state"), vehicle = null;
        JSONArray vehicles = state.getJSONArray("vehicles");
        for (int i = 0; i < vehicles.length(); i++) if (vehicleId.equals(vehicles.getJSONObject(i).getString("id"))) vehicle = vehicles.getJSONObject(i);
        if (vehicle == null) throw new IllegalArgumentException("Select a vehicle before exporting history");
        if (kind.equals("csv")) return csv(state, vehicle);
        if (!kind.equals("report")) throw new IllegalArgumentException("Unsupported export format");
        return report(snapshot, vehicle);
    }
    private static String cell(String value) {
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@\t\r\n".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private static void line(StringBuilder out, String... cells) { for (int i = 0; i < cells.length; i++) { if (i > 0) out.append(','); out.append(cell(cells[i])); } out.append("\r\n"); }
    private static String value(JSONObject record, String key) { return record.isNull(key) ? "" : record.optString(key); }
    private static String instant(JSONObject record, String key) { return record.isNull(key) ? "" : Instant.ofEpochMilli(record.optLong(key)).toString(); }
    private static String csv(JSONObject state, JSONObject vehicle) throws Exception {
        StringBuilder out = new StringBuilder();
        line(out, "record_type", "record_id", "vehicle_id", "date_or_start_utc", "end_utc", "odometer_mm", "service_low_mm", "service_high_mm", "trip_distance_mm", "fuel_ml", "cost_hundredths", "currency", "source", "confidence", "items_or_category", "full_tank", "missed_fill", "supersedes", "provider", "notes", "parts_hundredths", "labor_hundredths", "tax_hundredths", "diy", "part_numbers", "warranty");
        for (String collection : new String[]{"readings", "services", "fuel", "expenses", "trips"}) {
            JSONArray records = state.getJSONArray(collection);
            for (int i = 0; i < records.length(); i++) {
                JSONObject r = records.getJSONObject(i); if (!vehicle.getString("id").equals(r.getString("vehicleId"))) continue;
                JSONObject mileage = r.optJSONObject("mileage"), costs = r.optJSONObject("costBreakdown");
                line(out, collection, r.getString("id"), r.getString("vehicleId"), r.has("atMs") ? instant(r, "atMs") : r.has("startedMs") ? instant(r, "startedMs") : value(r, "date"), instant(r, "endedMs"),
                        collection.equals("trips") ? "" : value(r, "distanceMm"), mileage == null ? "" : value(mileage, "lowMm"), mileage == null ? "" : value(mileage, "highMm"), collection.equals("trips") ? value(r, "distanceMm") : "",
                        value(r, "volumeMl"), value(r, "costCents"), vehicle.getString("currency"), value(r, "source"), value(r, "confidence"), r.has("itemIds") ? r.getJSONArray("itemIds").toString() : value(r, "category"),
                        value(r, "full"), value(r, "missedFill"), value(r, "supersedes"), value(r, "provider"), value(r, "notes"), costs == null ? "" : value(costs, "partsCents"), costs == null ? "" : value(costs, "laborCents"), costs == null ? "" : value(costs, "taxCents"), value(r, "diy"), value(r, "partNumbers"), value(r, "warranty"));
            }
        }
        return out.toString();
    }
    public static String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private static String report(JSONObject snapshot, JSONObject vehicle) throws Exception {
        JSONObject state = snapshot.getJSONObject("state"); boolean metric = vehicle.getString("units").equals("metric"); String unit = metric ? " km" : " mi";
        Map<String, String> titles = new HashMap<>(); JSONArray rules = snapshot.getJSONObject("schedule").getJSONArray("rules");
        for (int i = 0; i < rules.length(); i++) titles.put(rules.getJSONObject(i).getString("id"), rules.getJSONObject(i).getString("title"));
        JSONArray custom = state.getJSONArray("customItems"); for (int i = 0; i < custom.length(); i++) titles.put(custom.getJSONObject(i).getString("id"), custom.getJSONObject(i).getString("title") + " (owner-defined)");
        StringBuilder out = new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>SubaruBridge service history</title><style>body{font:16px system-ui,sans-serif;margin:36px;line-height:1.5;color:#161616}h1{font-size:26px}article{border-top:1px solid #ccc;padding:16px 0;break-inside:avoid}p{white-space:pre-wrap}small{color:#555}@media print{body{margin:0}@page{margin:18mm}}</style><body><h1>Service history</h1><h2>");
        out.append(html(vehicle.getString("nickname"))).append("</h2><p>").append(html(vehicle.getInt("year") + " " + vehicle.getString("make") + " " + vehicle.getString("model"))).append("</p><small>Owner-entered records. Missing and approximate details are retained. This report does not certify vehicle condition.</small>");
        List<JSONObject> records = new ArrayList<>(); JSONArray services = state.getJSONArray("services");
        for (int i = 0; i < services.length(); i++) if (vehicle.getString("id").equals(services.getJSONObject(i).getString("vehicleId"))) records.add(services.getJSONObject(i));
        records.sort(Comparator.comparing((JSONObject r) -> value(r, "date")).reversed());
        for (JSONObject r : records) {
            List<String> items = new ArrayList<>(); JSONArray ids = r.getJSONArray("itemIds"); for (int i = 0; i < ids.length(); i++) items.add(titles.getOrDefault(ids.getString(i), ids.getString(i)));
            out.append("<article><strong>").append(html(String.join(", ", items))).append("</strong><p>").append(html(r.isNull("date") ? "Date unknown" : r.getString("date")));
            JSONObject range = r.optJSONObject("mileage");
            if (range != null) out.append(" · ").append(html(OwnershipNumbers.distance(range.getLong("lowMm"), metric) + (range.getLong("highMm") == range.getLong("lowMm") ? "" : " – " + OwnershipNumbers.distance(range.getLong("highMm"), metric) + " (approximate range)") + unit));
            else out.append(" · Mileage unknown");
            out.append("</p><p>").append(html(OwnershipNumbers.money(r.getLong("costCents"), vehicle.getString("currency")))).append("</p><p>").append(html(r.getString("provider"))).append("</p><p>").append(html(r.getString("notes"))).append("</p>");
            if (r.optBoolean("diy")) out.append("<p>DIY service</p>");
            JSONObject costs = r.optJSONObject("costBreakdown"); if (costs != null) out.append("<p>").append(html("Parts: " + OwnershipNumbers.money(costs.getLong("partsCents"), vehicle.getString("currency")) + " · Labor: " + OwnershipNumbers.money(costs.getLong("laborCents"), vehicle.getString("currency")) + " · Tax: " + OwnershipNumbers.money(costs.getLong("taxCents"), vehicle.getString("currency")))).append("</p>");
            JSONArray parts = r.optJSONArray("partNumbers"); if (parts != null && parts.length() > 0) out.append("<p>").append(html("Parts: " + parts)).append("</p>");
            out.append("<p>").append(html(r.optString("warranty"))).append("</p></article>");
        }
        if (records.isEmpty()) out.append("<p>No services recorded.</p>");
        return out.append("</body></html>").toString();
    }
}
