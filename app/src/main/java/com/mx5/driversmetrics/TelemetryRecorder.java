package com.mx5.driversmetrics;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra un campione di telemetria al secondo mentre la registrazione è attiva
 * (premuto "Avvia registrazione"). Quando viene fermata, salva subito i campioni grezzi
 * su disco (PendingSessionStore) e passa la mano a ReportSendService — un servizio in
 * primo piano che genera CSV/Excel/mappa/testo e invia l'email, protetto dal sistema
 * molto meglio di un semplice thread in background: se il quadro dell'auto viene spento
 * (e Android Auto si disconnette) mentre l'invio è ancora in corso, il servizio ha il
 * tempo di finire; se anche il processo venisse comunque terminato, il prossimo avvio
 * dell'app ritenta da solo grazie ai dati già salvati (vedi PendingReportRetry).
 */
final class TelemetryRecorder {

    enum Status { IDLE, WAITING_GPS, RECORDING, PROCESSING, SENT, ERROR }

    private static final TelemetryRecorder INSTANCE = new TelemetryRecorder();
    private static final long SAMPLE_INTERVAL_MS = 1000;
    /** Attesa massima di un fix GPS prima di iniziare comunque a registrare. */
    private static final long GPS_WAIT_TIMEOUT_MS = 8000;
    private static final long GPS_POLL_INTERVAL_MS = 500;

    static TelemetryRecorder getInstance() {
        return INSTANCE;
    }

    private final List<TelemetrySample> samples = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile Status status = Status.IDLE;
    private volatile String statusMessage = "";
    private long startMs;
    private long gpsWaitStartMs;
    private double distanceAtStartKm;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (status == Status.RECORDING) {
                DrivingState s = SensorHub.state;
                samples.add(new TelemetrySample(
                        System.currentTimeMillis(), s.lat, s.lon, s.speedKmh,
                        s.latG, s.lonG, s.altitudeM, s.headingDeg, s.gpsAccuracyM,
                        Math.max(0, s.distanceKm - distanceAtStartKm)));
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        }
    };

    /** In attesa di un fix GPS prima di iniziare davvero a registrare (vedi start()):
     *  controlla ogni GPS_POLL_INTERVAL_MS se il GPS ha agganciato la posizione, per
     *  qualche secondo in più prima di partire comunque, così il primo campione (e la
     *  mappa del percorso) hanno più probabilità di avere una posizione valida. */
    private final Runnable gpsWaitPoll = new Runnable() {
        @Override
        public void run() {
            if (status != Status.WAITING_GPS) return;
            DrivingState s = SensorHub.state;
            boolean hasFix = s.gpsOk && !Double.isNaN(s.lat) && !Double.isNaN(s.lon);
            boolean timedOut = System.currentTimeMillis() - gpsWaitStartMs >= GPS_WAIT_TIMEOUT_MS;
            if (hasFix || timedOut) {
                beginRecordingNow();
            } else {
                handler.postDelayed(this, GPS_POLL_INTERVAL_MS);
            }
        }
    };

    private TelemetryRecorder() {
    }

    boolean isRecording() {
        return status == Status.RECORDING;
    }

    Status getStatus() {
        return status;
    }

    String getStatusMessage() {
        return statusMessage;
    }

    int getSampleCount() {
        return samples.size();
    }

    long getElapsedMs() {
        return (status == Status.IDLE || status == Status.WAITING_GPS) ? 0 : System.currentTimeMillis() - startMs;
    }

    /** Premuto "Avvia registrazione": attende un fix GPS (max qualche secondo) prima
     *  di iniziare davvero a campionare, vedi gpsWaitPoll. */
    void start() {
        if (status == Status.RECORDING || status == Status.WAITING_GPS) return;
        samples.clear();
        statusMessage = "";
        gpsWaitStartMs = System.currentTimeMillis();
        status = Status.WAITING_GPS;
        handler.post(gpsWaitPoll);
    }

    private void beginRecordingNow() {
        startMs = System.currentTimeMillis();
        distanceAtStartKm = SensorHub.state.distanceKm;
        status = Status.RECORDING;
        handler.post(sampler);
    }

    /** Ferma la registrazione: salva subito i campioni su disco (rete di sicurezza) e
     *  passa la mano a ReportSendService per la generazione del report e l'invio email. */
    void stopAndSend(Context context) {
        if (status != Status.RECORDING) return;
        handler.removeCallbacksAndMessages(null);
        status = Status.PROCESSING;

        Context appContext = context.getApplicationContext();
        List<TelemetrySample> snapshot = new ArrayList<>(samples);
        long sessionStartMs = startMs;

        // Salvataggio su disco PRIMA di qualunque elaborazione di rete: se da qui in poi
        // il processo dell'app venisse terminato (es. quadro spento, Android Auto
        // disconnesso), il prossimo avvio dell'app trova comunque questi dati e ritenta
        // l'invio da solo — vedi PendingSessionStore e PendingReportRetry. Anche se questo
        // salvataggio fallisse, si tenta comunque l'invio immediato sotto: non è un
        // motivo per rinunciare al report.
        try {
            PendingSessionStore.save(appContext, sessionStartMs, snapshot);
        } catch (Exception e) {
            statusMessage = "Attenzione: salvataggio di sicurezza non riuscito.";
        }

        // Il lavoro vero (mappa, CSV, Excel, invio email) gira in ReportSendService, un
        // servizio in primo piano: molto più protetto dal sistema di un semplice thread
        // in background legato al processo dell'app. L'esito arriva qui in modo asincrono
        // tramite onProcessingResult(...).
        ReportSendService.start(appContext, sessionStartMs);
    }

    /** Chiamato da ReportSendService quando ha finito di elaborare una sessione (successo
     *  o errore). Aggiorna lo stato mostrato in RecordingScreen solo se si riferisce alla
     *  sessione attualmente "in elaborazione" agli occhi dell'utente: un ritentativo di
     *  una sessione vecchia, fatto all'avvio dell'app, non deve toccare lo stato di una
     *  registrazione nuova eventualmente già in corso. */
    void onProcessingResult(long sessionStartMs, boolean success, String message) {
        if (status == Status.PROCESSING && this.startMs == sessionStartMs) {
            statusMessage = message;
            status = success ? Status.SENT : Status.ERROR;
        }
    }

    /** Da chiamare dopo che l'esito è stato mostrato, per poter avviare una nuova registrazione. */
    void acknowledge() {
        if (status == Status.SENT || status == Status.ERROR) {
            status = Status.IDLE;
            statusMessage = "";
        }
    }
}
