package com.mx5.driversmetrics;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.Locale;

/**
 * Schermata Telemetria: "Avvia registrazione" campiona velocità, G,
 * posizione GPS e gli altri indicatori una volta al secondo; "Arresta registrazione"
 * chiude la sessione e genera in background il report CSV + immagine del percorso,
 * inviati via email secondo i parametri configurati in Impostazioni email sul telefono.
 */
public final class RecordingScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelemetryRecorder.Status lastStatus = null;
    // Controlla ogni 2 secondi se lo STATO (Pronta / In attesa GPS / Registrazione in
    // corso / Elaborazione / Inviato / Errore) è cambiato, e invalida solo in quel caso.
    // In precedenza si invalidava anche solo perché la durata era avanzata di un secondo:
    // durante una registrazione attiva la durata cambia quasi ad ogni giro di controllo,
    // quindi il template veniva ricreato in continuazione per tutta la sessione. Ogni
    // invalidate() sostituisce il template con uno nuovo, e se capita mentre l'host sta
    // ancora elaborando una rotazione/pressione della rotellina il click va perso (un
    // tocco invece è un evento singolo immediato, meno esposto a questa corsa critica) —
    // su un head unit reale (Mazda MX-5) questo rendeva "Arresta registrazione" e la
    // freccia Indietro praticamente inutilizzabili con la rotellina per tutta la durata
    // della registrazione, mentre col touch funzionavano sempre. Non invalidando più per
    // il solo passare del tempo, durante la registrazione il template non viene più
    // ricreato da solo: "Durata registrazione" e "Campioni registrati" mostrano quindi il
    // valore di quando la schermata è stata aperta (o riaperta) e vengono aggiornati alla
    // fine, non in tempo reale — vedi la riga "Nota" mostrata sotto ai due contatori in
    // onGetTemplate(). Indietro e Arresta registrazione con la rotellina sono così
    // affidabili quanto a riposo, dove già funzionavano.
    private static final long TICK_MS = 2000;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            TelemetryRecorder rec = TelemetryRecorder.getInstance();
            TelemetryRecorder.Status status = rec.getStatus();
            if (status != lastStatus) {
                lastStatus = status;
                invalidate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    public RecordingScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        TelemetryRecorder rec = TelemetryRecorder.getInstance();
        TelemetryRecorder.Status status = rec.getStatus();

        ItemList.Builder list = new ItemList.Builder();

        Row.Builder toggle = new Row.Builder();
        switch (status) {
            case RECORDING:
                toggle.setTitle("■ Arresta registrazione")
                        .addText("La registrazione verrà chiusa e il report inviato via email")
                        .setOnClickListener(() -> {
                            rec.stopAndSend(getCarContext());
                            invalidate();
                        });
                break;
            case WAITING_GPS:
                toggle.setTitle("In attesa del GPS…")
                        .addText("La registrazione parte appena il telefono aggancia la posizione (max qualche secondo)");
                break;
            case PROCESSING:
                toggle.setTitle("Elaborazione in corso…")
                        .addText("Generazione CSV, mappa percorso e invio email");
                break;
            default:
                toggle.setTitle("▶ Avvia registrazione")
                        .addText("Campiona velocità, G e posizione GPS una volta al secondo")
                        .setOnClickListener(() -> {
                            rec.start();
                            invalidate();
                        });
                break;
        }
        list.addItem(toggle.build());

        list.addItem(new Row.Builder()
                .setTitle("Stato")
                .addText(statusLabel(status, rec.getStatusMessage()))
                .build());

        if (status == TelemetryRecorder.Status.RECORDING || status == TelemetryRecorder.Status.PROCESSING
                || rec.getSampleCount() > 0) {
            list.addItem(new Row.Builder()
                    .setTitle("Durata registrazione")
                    .addText(formatElapsed(rec.getElapsedMs()))
                    .build());
            list.addItem(new Row.Builder()
                    .setTitle("Campioni registrati")
                    .addText(String.valueOf(rec.getSampleCount()))
                    .build());
            if (status == TelemetryRecorder.Status.RECORDING) {
                list.addItem(new Row.Builder()
                        .setTitle("Nota")
                        .addText("Durata e campioni non si aggiornano in tempo reale: mostrano il valore "
                                + "di quando hai aperto questa schermata e vengono aggiornati a fine registrazione.")
                        .build());
            }
        }

        Header header = new Header.Builder()
                .setTitle("Telemetria")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }

    private String statusLabel(TelemetryRecorder.Status status, String message) {
        switch (status) {
            case RECORDING: return "Registrazione in corso";
            case WAITING_GPS: return "In attesa del segnale GPS…";
            case PROCESSING: return "Generazione report e invio email…";
            case SENT: return message.isEmpty() ? "Report inviato" : message;
            case ERROR: return message.isEmpty() ? "Errore durante l'invio" : message;
            default: return "Pronta";
        }
    }

    private String formatElapsed(long elapsedMs) {
        long totalSec = Math.max(0, elapsedMs / 1000);
        long hh = totalSec / 3600, mm = (totalSec % 3600) / 60, ss = totalSec % 60;
        return hh > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hh, mm, ss)
                : String.format(Locale.ROOT, "%d:%02d", mm, ss);
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        SensorHub.ensureStarted(getCarContext());
        // Inizializza la "ultimo stato visto" allo stato reale corrente, non a un
        // valore-sentinella: altrimenti il primissimo giro di tick, pochi istanti dopo
        // l'apertura della schermata, troverebbe sempre "qualcosa di cambiato" (anche se
        // non è cambiato nulla) e invaliderebbe comunque — proprio la finestra in cui un
        // click di rotellina fatto subito dopo l'ingresso nella schermata (su "Avvia
        // registrazione" o sulla freccia Indietro) rischia di andare perso. Da qui in poi
        // si invalida solo quando lo stato è davvero diverso da quando la schermata è
        // comparsa (vedi il commento su tick sopra per il perché non si guarda più anche
        // la durata).
        TelemetryRecorder rec = TelemetryRecorder.getInstance();
        lastStatus = rec.getStatus();
        handler.post(tick);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        handler.removeCallbacksAndMessages(null);
    }
}
