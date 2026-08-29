package com.mx5.driversmetrics;

import android.app.Application;

/**
 * Punto unico di ingresso del processo dell'app, sia quando parte da Android Auto sia dal
 * telefono (Impostazioni email): qui si controlla se sono rimaste sessioni di telemetria
 * generate ma non inviate — per esempio perché il quadro dell'auto è stato spento prima
 * che l'invio finisse — e si riprova a inviarle in automatico, vedi PendingReportRetry.
 */
public final class Mx5Application extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PendingReportRetry.retryAllPending(this);
    }
}
