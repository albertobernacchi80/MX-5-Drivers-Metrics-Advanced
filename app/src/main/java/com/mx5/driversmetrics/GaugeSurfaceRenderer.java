package com.mx5.driversmetrics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

/**
 * Gestisce l'unica Surface di disegno personalizzato che l'host mette a disposizione alle
 * schermate NavigationTemplate, e la ridisegna periodicamente. Serve per mostrare i gauge
 * molto più grandi di quanto permettano le icone dei template standard: Android Auto limita
 * quelle icone a 44dp per specifica di design, indipendentemente dalla risoluzione delle
 * immagini fornite dall'app (verificato sulla documentazione ufficiale) — l'unico modo per
 * avere gauge davvero grandi è disegnarli direttamente su questa Surface.
 *
 * Il disegno sulla Surface NON è cliccabile né selezionabile con la rotella (il comportamento
 * della rotella su una Surface personalizzata non è documentato dalla libreria, quindi non ci
 * si affida per l'interazione). Ogni interazione reale (cambiare schermata, tornare indietro)
 * passa invece dall'ActionStrip del NavigationTemplate, un elemento standard gestito
 * dall'host esattamente come le voci di una lista o di una griglia — quindi selezionabile con
 * la rotella allo stesso modo.
 *
 * Singleton perché l'host fornisce una sola Surface per l'intera app: quando si passa da una
 * schermata all'altra, la schermata che diventa visibile sostituisce solo il "drawCallback"
 * (cosa disegnare), non la registrazione della Surface stessa.
 */
final class GaugeSurfaceRenderer {

    interface DrawCallback {
        void draw(Canvas canvas, Rect visibleArea);
    }

    private static final GaugeSurfaceRenderer INSTANCE = new GaugeSurfaceRenderer();
    private static final long TICK_MS = 300;
    private static final int BACKGROUND = Color.parseColor("#09090F");

    static GaugeSurfaceRenderer getInstance() {
        return INSTANCE;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile Surface surface;
    private volatile Rect visibleArea;
    private volatile DrawCallback drawCallback;
    private boolean registered = false;
    // Incrementato SOLO da start(): ogni Tick programmato porta con sé l'epoca di quel
    // momento e si auto-invalida confrontandola con quella corrente, invece di affidarsi a
    // handler.removeCallbacks(). Necessario perché, quando si passa da una schermata
    // all'altra (es. Home -> Accelerazione), non è garantito che stop() della schermata che
    // se ne va venga eseguito PRIMA di start() della schermata successiva — nel lifecycle
    // delle Screen, come in quello delle Activity, può capitare il contrario. Per questo
    // stop() (sotto) non tocca più l'epoca: se lo facesse, un vecchio stop() eseguito DOPO
    // il nuovo start() invaliderebbe anche il tick appena programmato dalla nuova
    // schermata, congelando il ridisegno periodico dei gauge fino alla transizione
    // successiva. Lasciare che il tick della schermata precedente continui a girare finché
    // non viene superato dal prossimo start() è innocuo: render() non fa nulla se la
    // Surface non è più valida, cosa che l'host segnala comunque tramite onSurfaceDestroyed.
    private int epoch = 0;

    private final class Tick implements Runnable {
        private final int myEpoch;

        Tick(int myEpoch) {
            this.myEpoch = myEpoch;
        }

        @Override
        public void run() {
            if (myEpoch != epoch) {
                return; // schermata già cambiata nel frattempo: non ridisegnare né riprogrammare.
            }
            render();
            handler.postDelayed(this, TICK_MS);
        }
    }

    private final SurfaceCallback surfaceCallback = new SurfaceCallback() {
        @Override
        public void onSurfaceAvailable(@NonNull SurfaceContainer surfaceContainer) {
            surface = surfaceContainer.getSurface();
            render();
        }

        @Override
        public void onVisibleAreaChanged(@NonNull Rect visible) {
            visibleArea = visible;
            render();
        }

        @Override
        public void onStableAreaChanged(@NonNull Rect stableArea) {
            render();
        }

        @Override
        public void onSurfaceDestroyed(@NonNull SurfaceContainer surfaceContainer) {
            surface = null;
        }
    };

    private GaugeSurfaceRenderer() {
    }

    /** Da chiamare in onStart() di ogni schermata che disegna sulla Surface: registra (la
     *  prima volta) il callback della Surface presso l'host e imposta cosa disegnare. */
    void start(@NonNull CarContext carContext, @NonNull DrawCallback callback) {
        this.drawCallback = callback;
        if (!registered) {
            carContext.getCarService(AppManager.class).setSurfaceCallback(surfaceCallback);
            registered = true;
        }
        epoch++;
        handler.post(new Tick(epoch));
    }

    /** Da chiamare in onStop(). Non tocca più l'epoca (vedi commento sul campo epoch):
     *  il ridisegno periodico si ferma da solo al prossimo giro se nel frattempo nessuna
     *  nuova schermata ha chiamato start(), perché la Surface nel frattempo non è più
     *  valida (l'host la distrugge quando non serve più) e render() diventa un no-op. */
    void stop() {
        // Intenzionalmente vuoto.
    }

    private void render() {
        Surface s = surface;
        DrawCallback cb = drawCallback;
        if (s == null || !s.isValid() || cb == null) {
            return;
        }
        Canvas canvas;
        try {
            canvas = s.lockCanvas(null);
        } catch (Exception e) {
            return; // Surface non pronta in questo istante: si riprova al prossimo tick.
        }
        try {
            canvas.drawColor(BACKGROUND);
            Rect area = visibleArea != null ? visibleArea : new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
            cb.draw(canvas, area);
        } finally {
            s.unlockCanvasAndPost(canvas);
        }
    }
}
