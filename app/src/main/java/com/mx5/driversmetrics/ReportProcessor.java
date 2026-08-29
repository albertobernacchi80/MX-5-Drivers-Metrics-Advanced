package com.mx5.driversmetrics;

import android.content.Context;

import java.io.File;
import java.util.List;

/**
 * Logica condivisa che genera il report completo di una sessione (mappa, CSV, Excel,
 * testo descrittivo) e lo invia via email se l'invio automatico è attivo. Usata sia
 * dall'invio "a caldo" appena fermata una registrazione, sia da un ritentativo di una
 * sessione rimasta in sospeso (vedi ReportSendService e PendingReportRetry) — stessa
 * identica logica in entrambi i casi, non due copie da tenere allineate.
 */
final class ReportProcessor {

    private ReportProcessor() {
    }

    /** Ritorna un messaggio di stato leggibile in caso di successo; lancia un'eccezione
     *  se l'invio email fallisce (rete, credenziali SMTP, ecc.) — il fallimento della sola
     *  mappa non conta come errore: RouteMapBuilder ritorna semplicemente null e il report
     *  parte comunque senza immagine del percorso. */
    static String process(Context appContext, List<TelemetrySample> samples, long sessionStartMs) throws Exception {
        AppSettings settings = new AppSettings(appContext);
        // La mappa va generata prima del CSV: assegna i numeri di marker che poi
        // finiscono nella colonna "Marker_mappa" del CSV.
        File map = RouteMapBuilder.build(appContext, samples, settings.getGeoapifyApiKey());
        File csv = CsvReportBuilder.build(appContext, samples, sessionStartMs);
        File xlsx = XlsxReportBuilder.build(appContext, samples, sessionStartMs);
        // Testo, non file: finisce direttamente nel corpo dell'email (vedi EmailSender),
        // non in un allegato separato.
        String reportText = DrivingReportBuilder.buildText(samples, sessionStartMs);

        if (settings.isAutoSendEnabled()) {
            EmailSender.send(settings, csv, xlsx, map, reportText, sessionStartMs);
            return "Report inviato via email (" + samples.size() + " campioni).";
        } else {
            return "Report generato, invio automatico disattivato nelle impostazioni.";
        }
    }
}
