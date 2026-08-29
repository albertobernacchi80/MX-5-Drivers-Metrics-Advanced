package com.mx5.driversmetrics;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Ritenta l'invio di sessioni rimaste "a metà": dati salvati da PendingSessionStore ma mai
 * inviati con successo, tipicamente perché il processo dell'app è stato terminato dal
 * sistema prima che ReportSendService finisse il lavoro (per esempio: quadro dell'auto
 * spento, Android Auto disconnesso, mentre l'email era ancora in invio).
 *
 * Chiamato a ogni avvio del processo dell'app (vedi Mx5Application), sia che l'app riparta
 * da Android Auto sia dalle Impostazioni email sul telefono: l'utente non deve fare nulla,
 * la prossima volta che apre l'app il report rimasto in sospeso riparte da solo.
 */
final class PendingReportRetry {

    private static final String TAG = "PendingReportRetry";

    private PendingReportRetry() {
    }

    static void retryAllPending(Context context) {
        try {
            List<Long> pending = PendingSessionStore.listPendingSessionIds(context);
            for (long sessionStartMs : pending) {
                Log.i(TAG, "Sessione rimasta in sospeso, riprovo l'invio: " + sessionStartMs);
                ReportSendService.start(context, sessionStartMs);
            }
        } catch (Exception e) {
            Log.w(TAG, "Controllo sessioni in sospeso fallito: " + e.getMessage());
        }
    }
}
