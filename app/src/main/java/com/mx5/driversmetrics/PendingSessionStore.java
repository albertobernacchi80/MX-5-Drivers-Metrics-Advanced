package com.mx5.driversmetrics;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Salva su disco, in modo durevole (nella cartella "files" dell'app, non nella cache che
 * il sistema può svuotare liberamente), i campioni grezzi di una sessione di telemetria
 * appena fermata — PRIMA di qualunque elaborazione di rete (mappa, email).
 *
 * Serve da rete di sicurezza: se il processo dell'app venisse terminato mentre
 * ReportSendService sta ancora lavorando (per esempio perché il quadro dell'auto è stato
 * spento e Android Auto si è disconnesso), al prossimo avvio dell'app PendingReportRetry
 * trova qui i dati e ritenta l'invio da capo, senza aver perso nulla. Il file viene
 * cancellato solo a invio riuscito (o se l'invio automatico è disattivato nelle
 * impostazioni, nel qual caso non c'è comunque nulla da ritentare).
 */
final class PendingSessionStore {

    private static final String DIR_NAME = "pending_reports";

    private PendingSessionStore() {
    }

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), DIR_NAME);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File fileFor(Context context, long sessionStartMs) {
        return new File(dir(context), "session_" + sessionStartMs + ".psr");
    }

    /** Scrittura "tutto o niente" su un file temporaneo poi rinominato, così un crash a
     *  metà scrittura non lascia un file corrotto e illeggibile al riavvio. */
    static void save(Context context, long sessionStartMs, List<TelemetrySample> samples) throws IOException {
        File tmp = new File(dir(context), "session_" + sessionStartMs + ".tmp");
        try (Writer w = new FileWriter(tmp, false)) {
            for (TelemetrySample s : samples) {
                w.write(s.timestampMs + "|" + s.lat + "|" + s.lon + "|" + s.speedKmh + "|"
                        + s.latG + "|" + s.lonG + "|" + s.altitudeM + "|" + s.headingDeg + "|"
                        + s.gpsAccuracyM + "|" + s.distanceKm + "\n");
            }
        }
        File dest = fileFor(context, sessionStartMs);
        if (!tmp.renameTo(dest)) {
            throw new IOException("Impossibile salvare la sessione in sospeso (rename fallita)");
        }
    }

    static List<TelemetrySample> load(Context context, long sessionStartMs) throws IOException {
        File f = fileFor(context, sessionStartMs);
        List<TelemetrySample> out = new ArrayList<>();
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split("\\|", -1);
                if (p.length < 10) continue;
                try {
                    out.add(new TelemetrySample(
                            Long.parseLong(p[0]), parseD(p[1]), parseD(p[2]), parseD(p[3]),
                            parseD(p[4]), parseD(p[5]), parseD(p[6]), parseD(p[7]),
                            parseD(p[8]), parseD(p[9])));
                } catch (NumberFormatException ignored) {
                    // Riga corrotta isolata: si salta, meglio un report con qualche
                    // campione in meno che perderlo tutto per una riga sola.
                }
            }
        }
        return out;
    }

    static void delete(Context context, long sessionStartMs) {
        File f = fileFor(context, sessionStartMs);
        if (f.exists()) f.delete();
    }

    /** ID (sessionStartMs) di tutte le sessioni salvate e mai inviate con successo. */
    static List<Long> listPendingSessionIds(Context context) {
        List<Long> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files == null) return out;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith("session_") && name.endsWith(".psr")) {
                try {
                    out.add(Long.parseLong(name.substring("session_".length(), name.length() - ".psr".length())));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static double parseD(String s) {
        if ("NaN".equals(s)) return Double.NaN;
        return Double.parseDouble(s);
    }
}
