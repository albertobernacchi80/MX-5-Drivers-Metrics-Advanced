package com.mx5.driversmetrics;

import android.graphics.Color;

/**
 * Rilevamento del superamento di una nuova soglia di velocità massima, frenata
 * massima o G laterale massimo in sessione, e lampeggio del gauge corrispondente
 * per qualche istante. Il lampeggio resta sempre nel rosso dell'app (nessun altro
 * colore): alterna rosso acceso e il grigio della traccia del gauge.
 */
final class ThresholdColors {

    /** Quanto dura il lampeggio dopo che una soglia è stata superata. */
    static final long FLASH_DURATION_MS = 4000;
    /** Ogni quanto si alterna acceso/spento durante il lampeggio. */
    private static final long BLINK_INTERVAL_MS = 250;

    static final int RED = Color.parseColor("#FF4D4D");
    /** Stesso grigio della traccia disegnata da GaugeIcon, usato come "spento" nel lampeggio. */
    private static final int FLASH_OFF = Color.parseColor("#2A2A3C");

    // Soglie di velocità massima (km/h) che fanno scattare il lampeggio
    static final double[] SPEED_THRESHOLDS = {50, 120, 150, 180, 200};
    // Soglie di frenata massima (|G longitudinale|, valore assoluto)
    static final double[] BRAKE_THRESHOLDS = {0.4, 0.6, 0.8, 1.0};
    // Soglie di G laterale massimo (valore assoluto)
    static final double[] LATERAL_THRESHOLDS = {0.3, 0.5, 0.7, 0.9};

    private ThresholdColors() {
    }

    /** Indice (0-based) dell'ultima soglia superiore o uguale raggiunta da value, -1 se nessuna. */
    static int bandIndex(double value, double[] thresholds) {
        int idx = -1;
        for (int i = 0; i < thresholds.length; i++) {
            if (value >= thresholds[i]) idx = i;
        }
        return idx;
    }

    /** True se, tra oldMax e newMax, si è entrati in una fascia più alta di quella precedente. */
    static boolean crossedNewBand(double oldMax, double newMax, double[] thresholds) {
        return bandIndex(newMax, thresholds) > bandIndex(oldMax, thresholds);
    }

    /** Colore da usare per il gauge: se il lampeggio è attivo alterna rosso/spento,
     *  altrimenti il colore normale del gauge (quello che aveva prima). */
    static int blinkColor(long flashUntilMs, int normalColor) {
        long now = System.currentTimeMillis();
        if (now >= flashUntilMs) return normalColor;
        boolean on = (now / BLINK_INTERVAL_MS) % 2 == 0;
        return on ? RED : FLASH_OFF;
    }
}
