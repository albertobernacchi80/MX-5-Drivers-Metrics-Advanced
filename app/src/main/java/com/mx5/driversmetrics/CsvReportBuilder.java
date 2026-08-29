package com.mx5.driversmetrics;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Genera il report CSV di una registrazione: un blocco di riepilogo (durata,
 * distanza, massimi di sessione) seguito dalla tabella dei campioni, un rigo
 * al secondo. Delimitatore ';' e virgola come separatore decimale per
 * compatibilità diretta con Excel in italiano.
 */
final class CsvReportBuilder {

    private CsvReportBuilder() {
    }

    static File build(Context context, List<TelemetrySample> samples, long sessionStartMs) throws IOException {
        // dd-MM-yyyy (niente "/": non è un carattere valido nei nomi file) invece di dd/MM/yyyy.
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.ITALY);
        String fileName = "Metriche guida MX-5-" + dateFmt.format(new Date(sessionStartMs)) + ".csv";
        File out = new File(context.getCacheDir(), fileName);

        double maxSpeed = 0, maxLatG = 0, maxBrakeG = 0, distanceTot = 0;
        for (TelemetrySample s : samples) {
            maxSpeed = Math.max(maxSpeed, s.speedKmh);
            maxLatG = Math.max(maxLatG, Math.abs(s.latG));
            maxBrakeG = Math.max(maxBrakeG, Math.max(0, -s.lonG));
            distanceTot = Math.max(distanceTot, s.distanceKm);
        }
        long endMs = samples.isEmpty() ? sessionStartMs : samples.get(samples.size() - 1).timestampMs;
        long durationSec = Math.max(0, (endMs - sessionStartMs) / 1000);

        SimpleDateFormat headerFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY);
        SimpleDateFormat rowFmt = new SimpleDateFormat("HH:mm:ss", Locale.ITALY);

        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write("MX-5 Driver Metrics Advanced - Report registrazione\n");
            w.write("Inizio;" + headerFmt.format(new Date(sessionStartMs)) + "\n");
            w.write("Fine;" + headerFmt.format(new Date(endMs)) + "\n");
            w.write("Durata;" + formatDuration(durationSec) + "\n");
            w.write("Campioni;" + samples.size() + "\n");
            w.write("Distanza percorsa km;" + dec(distanceTot) + "\n");
            w.write("Velocita massima km/h;" + dec(maxSpeed) + "\n");
            w.write("G laterale massimo;" + dec(maxLatG) + "\n");
            w.write("G frenata massimo;" + dec(maxBrakeG) + "\n");
            w.write("\n");
            w.write("Indice;Ora;Latitudine;Longitudine;Velocita_kmh;G_laterale;G_longitudinale;"
                    + "Altitudine_m;Rotta_gradi;Precisione_gps_m;Distanza_progressiva_km;Marker_mappa\n");

            int i = 1;
            for (TelemetrySample s : samples) {
                w.write(i + ";");
                w.write(rowFmt.format(new Date(s.timestampMs)) + ";");
                w.write((s.hasFix() ? dec6(s.lat) : "") + ";");
                w.write((s.hasFix() ? dec6(s.lon) : "") + ";");
                w.write(dec(s.speedKmh) + ";");
                w.write(dec(s.latG) + ";");
                w.write(dec(s.lonG) + ";");
                w.write((Double.isNaN(s.altitudeM) ? "" : dec(s.altitudeM)) + ";");
                w.write((Double.isNaN(s.headingDeg) ? "" : dec(s.headingDeg)) + ";");
                w.write((Double.isNaN(s.gpsAccuracyM) ? "" : dec(s.gpsAccuracyM)) + ";");
                w.write(dec(s.distanceKm) + ";");
                w.write((s.mapMarkerIndex > 0 ? RouteMapBuilder.markerLabel(s.mapMarkerIndex) : "") + "\n");
                i++;
            }
        }
        return out;
    }

    private static String dec(double v) {
        return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
    }

    private static String dec6(double v) {
        return String.format(Locale.ROOT, "%.6f", v).replace('.', ',');
    }

    private static String formatDuration(long totalSec) {
        long hh = totalSec / 3600, mm = (totalSec % 3600) / 60, ss = totalSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hh, mm, ss);
    }
}
