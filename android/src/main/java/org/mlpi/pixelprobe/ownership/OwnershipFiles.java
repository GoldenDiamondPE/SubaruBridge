package org.mlpi.pixelprobe.ownership;

import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.security.*;
import java.util.*;

/** Private bounded receipt files. File names on disk are content hashes, never imported paths. */
public final class OwnershipFiles {
    public static final int MAX_FILE = 16 * 1024 * 1024, MAX_COUNT = 256;
    public static final long MAX_TOTAL = 256L * 1024 * 1024;
    private OwnershipFiles() {}
    public static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[16384]; long total = 0; int count;
        while ((count = input.read(buffer)) != -1) {
            total += count; if (total > limit) throw new IOException("File exceeds the supported size");
            output.write(buffer, 0, count);
        }
        return total;
    }
    public static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[16384]; int n; while ((n = in.read(b)) != -1) digest.update(b, 0, n); }
        StringBuilder text = new StringBuilder(); for (byte b : digest.digest()) text.append(String.format(Locale.ROOT, "%02x", b & 255)); return text.toString();
    }
    public static File blob(File directory, String hash) throws IOException {
        if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid attachment hash");
        File file = new File(directory, hash);
        if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile())) throw new IOException("Attachment path is outside private storage");
        return file;
    }
    public static String inspect(File file) throws Exception {
        if (file.length() == 0 || file.length() > MAX_FILE) throw new IOException("Choose a nonempty receipt smaller than 16 MiB");
        byte[] header = new byte[12]; try (InputStream in = new FileInputStream(file)) { if (in.read(header) < 5) throw new IOException("Receipt file is incomplete"); }
        if (new String(header, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
            try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer pdf = new PdfRenderer(fd)) {
                if (pdf.getPageCount() == 0 || pdf.getPageCount() > 500) throw new IOException("PDF receipts must have 1–500 pages");
            } catch (Exception e) { throw new IOException("This PDF cannot be opened. Choose a readable, unlocked PDF.", e); }
            return "application/pdf";
        }
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getPath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || (long)options.outWidth * options.outHeight > 100_000_000L
                || !Arrays.asList("image/jpeg", "image/png", "image/webp").contains(options.outMimeType)) throw new IOException("Choose a JPEG, PNG, WebP photo or an unlocked PDF");
        return options.outMimeType;
    }
    public static void removeTree(File directory) throws IOException {
        if (!directory.exists()) return;
        // Never follow symlinked staging directories.
        // Android's /data/user/0 parent may legitimately alias /data/data. Reject a
        // symlink at the selected leaf, rather than rejecting that OS-owned alias.
        File expected = new File(directory.getParentFile().getCanonicalFile(), directory.getName());
        if (!directory.getCanonicalFile().equals(expected)) throw new IOException("Unexpected staging path");
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) { if (child.isDirectory()) removeTree(child); else if (!child.delete()) throw new IOException("Could not remove temporary file"); }
        if (!directory.delete()) throw new IOException("Could not remove temporary folder");
    }
}
