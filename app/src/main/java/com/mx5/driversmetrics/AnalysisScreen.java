package com.mx5.driversmetrics;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Template;

import java.util.ArrayList;
import java.util.List;

/**
 * Analisi di fine sessione: distanza, velocità media, fluidità, G medi, frenate,
 * più i pulsanti Azzera/Spiegazioni/Crediti.
 *
 * Sono 12 voci in tutto: alcuni host Android Auto (compreso almeno un'unità reale
 * testata) impongono un limite di elementi per un GridTemplate più basso di 12 e
 * mandano in crash l'app se lo si supera ("host exception" all'apertura). Per
 * restare compatibili con qualsiasi limite, la lista viene paginata leggendo il
 * limite reale da ConstraintManager (con una soglia di sicurezza di riserva se il
 * servizio non è disponibile), e le pagine oltre la prima sono raggiungibili con
 * la voce "Altri dati" in fondo alla pagina corrente.
 */
public final class AnalysisScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int RED = Color.parseColor("#FF4D4D");
    private static final int WHITE = Color.parseColor("#F4F4FA");
    /** Usato solo se ConstraintManager non è disponibile: valore minimo garantito da tutti gli host. */
    private static final int FALLBACK_GRID_LIMIT = 6;

    private final int page;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastSignature = null;
    // Come in RecordingScreen: si controlla ogni 3 secondi (non più ogni 500ms, sempre) e si
    // invalida solo se i valori mostrati sono davvero cambiati rispetto all'ultima volta —
    // su un head unit reale (Mazda MX-5) invalidare il template mentre l'utente sta
    // selezionando o premendo con la rotellina un pulsante (verificato su "Azzera sessione"
    // e sulla freccia Indietro) ne fa perdere il click: ogni invalidate() sostituisce il
    // template con uno nuovo, e se capita proprio mentre l'host sta ancora elaborando la
    // rotellina l'evento va perso. "Tempo sessione" cambia comunque una volta al secondo, ma
    // gli altri valori restano spesso fermi (specie ad auto ferma): non ricostruire il
    // template quando davvero non cambia nulla riduce di molto le occasioni di collisione.
    private static final long TICK_MS = 3000;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            DrivingState s = SensorHub.state;
            long elapsedSec = (System.currentTimeMillis() - s.startMs) / 1000;
            String signature = elapsedSec + "|" + Math.round(s.distanceKm * 100) + "|"
                    + Math.round(s.avgSpeedKmh) + "|" + Math.round(s.maxSpeedKmh) + "|"
                    + Math.round(Double.isNaN(s.fluidity) ? 0 : s.fluidity) + "|" + s.brakeCount
                    + "|" + Math.round(s.avgLonG * 100) + "|" + Math.round(s.maxLatG * 100)
                    + "|" + Math.round(s.avgLatG * 100);
            if (!signature.equals(lastSignature)) {
                lastSignature = signature;
                invalidate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    public AnalysisScreen(@NonNull CarContext context) {
        this(context, 0);
    }

    private AnalysisScreen(@NonNull CarContext context, int page) {
        super(context);
        this.page = page;
        getLifecycle().addObserver(this);
    }

    private interface Entry {
        GridItem build();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        DrivingState s = SensorHub.state;
        double fluidityVal = Double.isNaN(s.fluidity) ? 0 : s.fluidity;
        long elapsedMs = System.currentTimeMillis() - s.startMs;

        List<Entry> entries = new ArrayList<>();
        entries.add(() -> new GridItem.Builder()
                .setTitle("Tempo sessione")
                .setImage(GaugeIcon.buildLinearTime(300, elapsedMs, WHITE), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Distanza percorsa")
                .setImage(GaugeIcon.buildLinear(300, s.distanceKm, Math.max(10, s.distanceKm), "km", WHITE, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Velocità media")
                .setImage(GaugeIcon.buildLinear(300, s.avgSpeedKmh, 220, "km/h", WHITE, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Velocità massima")
                .setImage(GaugeIcon.buildLinear(300, s.maxSpeedKmh, 220, "km/h", WHITE, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Indice di fluidità")
                .setImage(GaugeIcon.buildLinear(300, fluidityVal, 100, "/100", RED, true), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Frenate rilevate")
                .setImage(GaugeIcon.buildLinear(300, s.brakeCount, Math.max(10, s.brakeCount), "", RED, true), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("G long. medio")
                .setImage(GaugeIcon.buildLinear(300, s.avgLonG, 1.3, "G", WHITE, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("G laterale massimo")
                .setImage(GaugeIcon.buildLinear(300, s.maxLatG, 1.3, "G", RED, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("G laterale medio")
                .setImage(GaugeIcon.buildLinear(300, s.avgLatG, 1.3, "G", WHITE, false), GridItem.IMAGE_TYPE_LARGE)
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Azzera sessione")
                .setImage(GaugeIcon.buildAction(300, GaugeIcon.ActionGlyph.RESET), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener(() -> {
                    SensorHub.resetSession();
                    invalidate();
                })
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Spiegazioni")
                .setImage(GaugeIcon.buildAction(300, GaugeIcon.ActionGlyph.INFO), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener(() -> getScreenManager().push(new GaugeGuideScreen(getCarContext())))
                .build());
        entries.add(() -> new GridItem.Builder()
                .setTitle("Crediti")
                .setImage(GaugeIcon.buildAction(300, GaugeIcon.ActionGlyph.CREDITS), GridItem.IMAGE_TYPE_LARGE)
                .setOnClickListener(() -> getScreenManager().push(new CreditsScreen(getCarContext())))
                .build());

        int limit = gridContentLimit();

        ItemList.Builder list = new ItemList.Builder();
        int start = pageStartIndex(entries.size(), limit, page);

        boolean hasMore;
        int shown = 0;
        int i = start;
        int capacity = limit;
        for (; i < entries.size(); i++) {
            boolean isLastSlot = shown == capacity - 1;
            boolean moreAfterThis = i < entries.size() - 1;
            if (isLastSlot && moreAfterThis) {
                break; // lascia l'ultimo slot per "Altri dati"
            }
            list.addItem(entries.get(i).build());
            shown++;
        }
        hasMore = i < entries.size();
        if (hasMore) {
            int nextPage = page + 1;
            list.addItem(new GridItem.Builder()
                    .setTitle("Altri dati")
                    .setImage(GaugeIcon.buildAction(300, GaugeIcon.ActionGlyph.NEXT), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener(() -> getScreenManager().push(new AnalysisScreen(getCarContext(), nextPage)))
                    .build());
        }

        Header.Builder headerBuilder = new Header.Builder()
                .setTitle(page == 0 ? "Analisi" : "Analisi - altri dati")
                .setStartHeaderAction(Action.BACK);

        return new GridTemplate.Builder()
                .setHeader(headerBuilder.build())
                .setSingleList(list.build())
                .build();
    }

    /** Indice del primo elemento mostrato in questa pagina, tenendo conto che ogni
     *  pagina con un seguito riserva uno slot a "Altri dati" (capacità utile = limit - 1). */
    private static int pageStartIndex(int totalEntries, int limit, int page) {
        int perPage = Math.max(1, limit - 1);
        int idx = 0;
        for (int p = 0; p < page; p++) {
            int remaining = totalEntries - idx;
            if (remaining <= limit) {
                // l'ultima pagina possibile: non dovremmo mai arrivarci con page maggiore, ma per sicurezza fermiamoci.
                return idx;
            }
            idx += perPage;
        }
        return idx;
    }

    /** Legge il numero massimo di elementi che l'host corrente accetta in un GridTemplate.
     *  Se il servizio non è disponibile usa una soglia di sicurezza minima. */
    private int gridContentLimit() {
        try {
            ConstraintManager cm = getCarContext().getCarService(ConstraintManager.class);
            if (cm != null) {
                int l = cm.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID);
                if (l > 0) return l;
            }
        } catch (Exception ignored) {
            // host senza questo servizio: usa il fallback prudente.
        }
        return FALLBACK_GRID_LIMIT;
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        SensorHub.ensureStarted(getCarContext());
        handler.post(tick);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        handler.removeCallbacksAndMessages(null);
    }
}
