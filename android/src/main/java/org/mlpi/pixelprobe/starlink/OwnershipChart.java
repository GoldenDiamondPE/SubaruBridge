package org.mlpi.pixelprobe.starlink;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.mlpi.pixelprobe.ownership.OwnershipNumbers;

/** A compact native chart; the same data is available as readable monthly details. */
final class OwnershipChart extends View {
    private final long[] values;
    private final String[] labels;
    private final PhonePalette palette;
    private final boolean costs, metric;
    private final String currency;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    OwnershipChart(Context context, long[] values, String[] labels, PhonePalette palette, boolean costs, boolean metric, String currency) {
        super(context); this.values = values.clone(); this.labels = labels.clone(); this.palette = palette; this.costs = costs; this.metric = metric; this.currency = currency;
        StringBuilder description = new StringBuilder(costs ? "Recorded operating costs by month. " : "Recorded trip distance by completion month. ");
        for (int i = 0; i < values.length; i++) description.append(labels[i]).append(": ").append(amount(values[i])).append(". ");
        setContentDescription(description.toString()); setFocusable(true);
    }
    private String amount(long value) { return costs ? OwnershipNumbers.money(value, currency) : OwnershipNumbers.distance(value, metric) + (metric ? " km" : " mi"); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float left = dp(4), right = getWidth() - dp(4), top = dp(34), bottom = getHeight() - dp(34);
        long maximum = 0; for (long value : values) maximum = Math.max(maximum, value);
        paint.setTextSize(dp(12)); paint.setColor(palette.muted); paint.setTextAlign(Paint.Align.LEFT); canvas.drawText("Max " + amount(maximum), left, dp(18), paint);
        paint.setColor(palette.border); paint.setStrokeWidth(dp(1)); canvas.drawLine(left, bottom, right, bottom, paint);
        float step = (right - left) / values.length;
        for (int i = 0; i < values.length; i++) {
            float height = maximum == 0 ? 0 : (float)((double)values[i] / maximum * (bottom - top));
            paint.setColor(palette.accent); canvas.drawRoundRect(left + i * step + dp(3), bottom - height, left + (i + 1) * step - dp(3), bottom, dp(3), dp(3), paint);
            paint.setColor(palette.muted); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(labels[i].substring(0, labels[i].offsetByCodePoints(0, 1)), left + (i + .5f) * step, bottom + dp(21), paint);
        }
    }
}
