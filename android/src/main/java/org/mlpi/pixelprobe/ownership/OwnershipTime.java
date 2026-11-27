package org.mlpi.pixelprobe.ownership;

import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;

/** Phone-side civil-time conversion. Never silently normalize a missing or repeated hour. */
public final class OwnershipTime {
    private OwnershipTime() {}
    private static final DateTimeFormatter FORMAT = new DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd().optionalStart().appendLiteral(' ').appendOffsetId().optionalEnd()
            .toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    public static String format(long millis, ZoneId zone) { return FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone)); }
    public static long parse(String text, ZoneId zone) {
        TemporalAccessor parsed;
        try { parsed = FORMAT.parse(text.trim()); }
        catch (DateTimeException error) { throw new IllegalArgumentException("Use YYYY-MM-DD HH:mm or YYYY-MM-DD HH:mm:ss, optionally followed by an offset such as -04:00"); }
        LocalDateTime local = LocalDateTime.from(parsed); List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) throw new IllegalArgumentException("That local time does not exist because the clocks moved forward. Choose a valid time.");
        ZoneOffset chosen = parsed.query(TemporalQueries.offset());
        if (chosen == null) {
            if (offsets.size() != 1) throw new IllegalArgumentException("That local time occurs twice. Add its UTC offset, such as -04:00 or -05:00.");
            chosen = offsets.get(0);
        }
        if (!offsets.contains(chosen)) throw new IllegalArgumentException("The UTC offset does not match the entry's displayed time zone on that date");
        return local.atOffset(chosen).toInstant().toEpochMilli();
    }
}
