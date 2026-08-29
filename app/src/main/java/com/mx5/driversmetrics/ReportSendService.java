package com.mx5.driversmetrics;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servizio in primo piano che genera e invia il report di una sessione di telemetria.
 *
 * Esiste per una ragione precisa: prima, questo lavoro girava su un semplice thread in
 * background legato al processo dell'app. Spegnendo il quadro dell'auto (e quindi
 * disconnettendo Android Auto) il sistema può terminare il processo del telefono pochi
 * istanti dopo, anche prima che l'invio email sia completato — perdendo il report. Un
 * servizio in primo piano, con la sua notifica, ha una priorità molto più alta e ha il
 * tempo di finire il lavoro prima di essere terminato.
 *
 * Non si affida alla lista in memoria di TelemetryRecorder: legge sempre i campioni dal
 * file salvato da PendingSessionStore, così funziona identico sia per l'invio "a caldo"
 * appena fermata la registrazione, sia per un ritentativo di una sessione rimasta a metà
 * da un avvio precedente dell'app (vedi PendingReportRetry) — anche se nel frattempo il
 * processo è stato riavviato da zero e quella lista in memoria non esiste più.
 */
public final class ReportSendService extends Service {

    private static final String TAG = "ReportSendService";
    private static final String CHANNEL_ID = "mx5_report_send";
    private static final int NOTIF_ID = 501;
    private static final String EXTRA_SESSION_START_MS = "session_start_ms";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    static void start(Context context, long sessionStartMs) {
        Intent intent = new Intent(context, ReportSendService.class);
        intent.putExtra(EXTRA_SESSION_START_MS, sessionStartMs);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannelIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long sessionStartMs = intent != null ? intent.getLongExtra(EXTRA_SESSION_START_MS, -1) : -1;
        // Va chiamato entro pochi istanti dall'avvio del servizio, prima di qualunque
        // altra cosa: è quello che dà al servizio la priorità "in primo piano".
        startForeground(NOTIF_ID, buildNotification("Invio del report di guida in corso..."));

        if (sessionStartMs <= 0) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        executor.execute(() -> {
            Context appContext = getApplicationContext();
            boolean success = false;
            String message;
            try {
                List<TelemetrySample> samples = PendingSessionStore.load(appContext, sessionStartMs);
                if (samples.isEmpty()) {
                    message = "Nessun dato da inviare per questa sessione.";
                } else {
                    message = ReportProcessor.process(appContext, samples, sessionStartMs);
                    success = true;
                }
                // Cancella i dati salvati solo a lavoro riuscito (invio email riuscito, o
                // invio automatico disattivato apposta): altrimenti restano per il
                // prossimo ritentativo automatico, vedi PendingReportRetry.
                PendingSessionStore.delete(appContext, sessionStartMs);
            } catch (Exception e) {
                success = false;
                message = "Errore invio: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                Log.w(TAG, "Invio report fallito, si riproverà al prossimo avvio dell'app: " + message);
            }
            TelemetryRecorder.getInstance().onProcessingResult(sessionStartMs, success, message);
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
        });

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MX-5 Driver Metrics Advanced")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "Invio report", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Notifica mostrata mentre il report di una sessione di guida "
                        + "viene generato e inviato via email.");
                nm.createNotificationChannel(channel);
            }
        }
    }
}
