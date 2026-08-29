package com.mx5.driversmetrics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import androidx.car.app.model.CarIcon;
import androidx.core.graphics.drawable.IconCompat;

import java.util.Locale;

/**
 * Genera una mini-icona ad arco (stile gauge) come bitmap pre-renderizzata,
 * da usare dentro GridItem. Non usa SurfaceCallback: è semplice disegno
 * offline su un Bitmap, compatibile con qualsiasi categoria Android Auto.
 */
final class GaugeIcon {

    private static final int TRACK = Color.parseColor("#2A2A3C");
    private static final int WHITE = Color.parseColor("#F4F4FA");
    private static final int RED = Color.parseColor("#FF4D4D");
    private static final int DIM = Color.parseColor("#8888A0");

    private GaugeIcon() {
    }

    /** Variante per durate: il valore è formattato come mm:ss (o h:mm:ss oltre l'ora)
     *  invece che come numero decimale. La barra si riempie fino a 60 minuti. */
    static CarIcon buildLinearTime(int size, long elapsedMs, int accentColor) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        float cx = size / 2f;
        long totalSec = Math.max(0, elapsedMs / 1000);
        long hh = totalSec / 3600;
        long mm = (totalSec % 3600) / 60;
        long ss = totalSec % 60;
        String valueText = hh > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hh, mm, ss)
                : String.format(Locale.ROOT, "%d:%02d", mm, ss);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(WHITE);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        txt.setTextSize(size * (hh > 0 ? 0.26f : 0.34f));
        c.drawText(valueText, cx, size * 0.38f, txt);

        float barW = size * 0.86f, barH = size * 0.14f;
        float barX = (size - barW) / 2f, barY = size * 0.52f;
        RectF track = new RectF(barX, barY, barX + barW, barY + barH);

        Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(TRACK);
        c.drawRoundRect(track, barH / 2f, barH / 2f, trackPaint);

        double frac = Math.max(0, Math.min(1, elapsedMs / (60.0 * 60 * 1000)));
        if (frac > 0.02) {
            RectF fillRect = new RectF(barX, barY, barX + (float) (barW * frac), barY + barH);
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(accentColor);
            c.drawRoundRect(fillRect, barH / 2f, barH / 2f, fillPaint);
        }

        Paint unitTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitTxt.setColor(DIM);
        unitTxt.setTextAlign(Paint.Align.CENTER);
        unitTxt.setTextSize(size * 0.13f);
        c.drawText("durata", cx, barY + barH + size * 0.18f, unitTxt);

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }

    static CarIcon build(int size, double value, double max, String unit, int accentColor) {
        return build(size, value, max, unit, accentColor, false);
    }

    /** Variante a barra lineare orizzontale invece dell'anello, stesso linguaggio grafico. */
    static CarIcon buildLinear(int size, double value, double max, String unit, int accentColor, boolean forceInt) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        float cx = size / 2f;

        int numberColor = (accentColor == WHITE) ? RED : WHITE;

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(numberColor);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        txt.setTextSize(size * 0.34f);
        String valueText = forceInt
                ? String.format(Locale.ROOT, "%.0f", value)
                : (value >= 10
                        ? String.format(Locale.ROOT, "%.0f", value)
                        : String.format(Locale.ROOT, "%.2f", value));
        c.drawText(valueText, cx, size * 0.38f, txt);

        float barW = size * 0.86f, barH = size * 0.14f;
        float barX = (size - barW) / 2f, barY = size * 0.52f;
        RectF track = new RectF(barX, barY, barX + barW, barY + barH);

        Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(TRACK);
        c.drawRoundRect(track, barH / 2f, barH / 2f, trackPaint);

        double frac = Math.max(0, Math.min(1, value / max));
        if (frac > 0.02) {
            RectF fillRect = new RectF(barX, barY, barX + (float) (barW * frac), barY + barH);
            Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setColor(accentColor);
            c.drawRoundRect(fillRect, barH / 2f, barH / 2f, fillPaint);
        }

        Paint unitTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitTxt.setColor(DIM);
        unitTxt.setTextAlign(Paint.Align.CENTER);
        unitTxt.setTextSize(size * 0.13f);
        c.drawText(unit, cx, barY + barH + size * 0.18f, unitTxt);

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }

    static CarIcon build(int size, double value, double max, String unit, int accentColor, boolean forceInt) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        // Anello e testo dimensionati per riempire il più possibile il riquadro:
        // l'host decide quanto grande mostrare l'icona, ma a parità di riquadro un
        // disegno che usa più "inchiostro" del proprio bitmap risulta più leggibile.
        float cx = size / 2f, cy = size / 2f, r = size * 0.42f;
        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);

        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(size * 0.10f);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(TRACK);
        c.drawArc(oval, 135, 270, false, track);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(size * 0.10f);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setColor(accentColor);
        double frac = Math.max(0, Math.min(1, value / max));
        c.drawArc(oval, 135, (float) (270 * frac), false, fill);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(WHITE);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        boolean bigFont = forceInt || value >= 10;
        txt.setTextSize(size * (bigFont ? 0.30f : 0.26f));
        String valueText = forceInt
                ? String.format(Locale.ROOT, "%.0f", value)
                : (value >= 10
                        ? String.format(Locale.ROOT, "%.0f", value)
                        : String.format(Locale.ROOT, "%.2f", value));
        c.drawText(valueText, cx, cy + size * 0.08f, txt);

        Paint unitTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitTxt.setColor(DIM);
        unitTxt.setTextAlign(Paint.Align.CENTER);
        unitTxt.setTextSize(size * 0.13f);
        c.drawText(unit, cx, cy + size * 0.24f, unitTxt);

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }

    /** Disegna un gauge ad anello grande direttamente su un'area qualsiasi di un Canvas
     *  (usato per la Surface personalizzata: qui non c'è un riquadro fisso di 44dp imposto
     *  dai template, quindi il gauge può occupare metà o un quarto di schermo). Le
     *  proporzioni interne (anello, testo, unità) sono le stesse di build(), solo scalate
     *  sull'area disponibile invece che su un bitmap quadrato fisso. */
    static void drawTile(Canvas c, RectF box, String label, double value, double max, String unit,
                          int accentColor, boolean forceInt) {
        float pad = Math.min(box.width(), box.height()) * 0.04f;
        RectF inner = new RectF(box.left + pad, box.top + pad, box.right - pad, box.bottom - pad);
        if (inner.width() <= 0 || inner.height() <= 0) return;

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(DIM);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        float labelSize = Math.min(inner.width(), inner.height()) * 0.078f;
        labelPaint.setTextSize(labelSize);
        float labelY = inner.top + labelSize;
        c.drawText(label.toUpperCase(Locale.ROOT), inner.centerX(), labelY, labelPaint);

        float availTop = labelY + labelSize * 0.7f;
        float availH = Math.max(1f, inner.bottom - availTop);
        float ringSize = Math.min(inner.width(), availH);
        float cx = inner.centerX();
        float cy = availTop + availH / 2f;
        float r = ringSize * 0.42f;

        RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);

        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(ringSize * 0.10f);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(TRACK);
        c.drawArc(oval, 135, 270, false, track);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(ringSize * 0.10f);
        fill.setStrokeCap(Paint.Cap.ROUND);
        fill.setColor(accentColor);
        double frac = Math.max(0, Math.min(1, value / max));
        c.drawArc(oval, 135, (float) (270 * frac), false, fill);

        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setColor(WHITE);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        boolean bigFont = forceInt || value >= 10;
        txt.setTextSize(ringSize * (bigFont ? 0.30f : 0.26f));
        String valueText = forceInt
                ? String.format(Locale.ROOT, "%.0f", value)
                : (value >= 10
                        ? String.format(Locale.ROOT, "%.0f", value)
                        : String.format(Locale.ROOT, "%.2f", value));
        c.drawText(valueText, cx, cy + ringSize * 0.08f, txt);

        Paint unitTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitTxt.setColor(DIM);
        unitTxt.setTextAlign(Paint.Align.CENTER);
        unitTxt.setTextSize(ringSize * 0.13f);
        c.drawText(unit, cx, cy + ringSize * 0.24f, unitTxt);
    }

    /** Simbolo per le voci "azione" della griglia (Azzera, Spiegazioni, Crediti, Altri dati).
     *  IMPORTANTE: GridItem.Builder.build() lancia IllegalStateException se non è in stato
     *  "loading" e non ha un'immagine impostata — ogni GridItem, azioni comprese, deve
     *  averne una. */
    enum ActionGlyph { RESET, INFO, CREDITS, NEXT }

    static CarIcon buildAction(int size, ActionGlyph glyph) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        float cx = size / 2f, cy = size / 2f;

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size * 0.075f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(WHITE);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(WHITE);

        switch (glyph) {
            case RESET: {
                float r = size * 0.30f;
                RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
                c.drawArc(oval, -50, 280, false, stroke);
                double rad = Math.toRadians(-50);
                float tipX = (float) (cx + r * Math.cos(rad));
                float tipY = (float) (cy + r * Math.sin(rad));
                float aw = size * 0.10f;
                Path arrow = new Path();
                arrow.moveTo(tipX + aw * 0.9f, tipY - aw * 0.5f);
                arrow.lineTo(tipX - aw * 0.2f, tipY - aw * 0.9f);
                arrow.lineTo(tipX - aw * 0.5f, tipY + aw * 0.3f);
                arrow.close();
                c.drawPath(arrow, fill);
                break;
            }
            case INFO: {
                float r = size * 0.34f;
                c.drawCircle(cx, cy, r, stroke);
                c.drawCircle(cx, cy - r * 0.42f, size * 0.05f, fill);
                Paint bar = new Paint(stroke);
                bar.setStrokeWidth(size * 0.07f);
                c.drawLine(cx, cy - r * 0.02f, cx, cy + r * 0.5f, bar);
                break;
            }
            case CREDITS: {
                float w = size * 0.50f, h = size * 0.62f;
                RectF rect = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
                Paint rectStroke = new Paint(stroke);
                rectStroke.setStrokeWidth(size * 0.05f);
                c.drawRoundRect(rect, size * 0.05f, size * 0.05f, rectStroke);
                Paint line = new Paint(stroke);
                line.setStrokeWidth(size * 0.045f);
                float lx1 = cx - w * 0.30f, lx2 = cx + w * 0.30f;
                c.drawLine(lx1, cy - h * 0.16f, lx2, cy - h * 0.16f, line);
                c.drawLine(lx1, cy + h * 0.02f, lx2 - w * 0.15f, cy + h * 0.02f, line);
                c.drawLine(lx1, cy + h * 0.24f, lx2 - w * 0.25f, cy + h * 0.24f, line);
                break;
            }
            case NEXT: {
                Paint chevron = new Paint(stroke);
                chevron.setStrokeWidth(size * 0.10f);
                float w = size * 0.22f;
                Path path = new Path();
                path.moveTo(cx - w * 0.3f, cy - size * 0.22f);
                path.lineTo(cx + w * 0.5f, cy);
                path.lineTo(cx - w * 0.3f, cy + size * 0.22f);
                c.drawPath(path, chevron);
                break;
            }
        }

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }

    /** Icone statiche (non legate a un valore in tempo reale) per le due icone dell'ActionStrip
     *  in Home, che aprono i menu GaugesMenuScreen / DataMenuScreen: rappresentano la categoria
     *  (gauge di velocità/accelerazione, analisi/telemetria), non l'ultimo valore letto — che
     *  invece si vede, grande, sulla Surface sotto. */
    enum NavGlyph { SPEED, ANALYSIS }

    static CarIcon buildNavIcon(int size, NavGlyph glyph) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        float cx = size / 2f, cy = size / 2f;

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size * 0.10f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(WHITE);

        switch (glyph) {
            case SPEED: {
                float r = size * 0.34f;
                RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
                c.drawArc(oval, 135, 270, false, stroke);
                Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
                needle.setStyle(Paint.Style.STROKE);
                needle.setStrokeWidth(size * 0.07f);
                needle.setStrokeCap(Paint.Cap.ROUND);
                needle.setColor(RED);
                double rad = Math.toRadians(45);
                c.drawLine(cx, cy, (float) (cx + r * 0.62f * Math.cos(rad)), (float) (cy + r * 0.62f * Math.sin(rad)), needle);
                break;
            }
            case ANALYSIS: {
                float barW = size * 0.14f, gap = size * 0.08f;
                float[] heights = {0.34f, 0.62f, 0.46f};
                float baseline = size * 0.68f;
                float maxH = size * 0.5f;
                float totalW = heights.length * barW + (heights.length - 1) * gap;
                float x = (size - totalW) / 2f;
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(WHITE);
                for (float h : heights) {
                    float top = baseline - maxH * h;
                    c.drawRoundRect(x, top, x + barW, baseline, barW * 0.25f, barW * 0.25f, fill);
                    x += barW + gap;
                }
                break;
            }
        }

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }
}
