package com.mx5.driversmetrics;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.constraints.ConstraintManager;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import java.util.ArrayList;
import java.util.List;

/**
 * Spiegazione, dal punto di vista dell'analisi di guida, di cosa dice ciascun gauge.
 *
 * Sono 12 voci in tutto: esattamente come le 12 GridItem di AnalysisScreen, per cui è già
 * stato verificato su un'unità reale (Mazda MX-5) che l'host può imporre un limite di
 * elementi più basso di 12 e comportarsi in modo anomalo se lo si supera. Lì il limite
 * riguardava un GridTemplate; qui è un ListTemplate, ma il principio è lo stesso e non c'è
 * motivo di credere che gli host si comportino meglio con le liste. Per restare compatibili
 * con qualsiasi limite, la lista viene paginata leggendo il limite reale da ConstraintManager
 * (con una soglia di sicurezza di riserva se il servizio non è disponibile), e le pagine
 * oltre la prima sono raggiungibili con la voce "Altri..." in fondo alla pagina corrente —
 * stesso schema di AnalysisScreen.
 */
public final class GaugeGuideScreen extends Screen {

    /** Usato solo se ConstraintManager non è disponibile: valore minimo garantito da tutti gli host. */
    private static final int FALLBACK_LIST_LIMIT = 6;

    private final int page;

    public GaugeGuideScreen(@NonNull CarContext context) {
        this(context, 0);
    }

    private GaugeGuideScreen(@NonNull CarContext context, int page) {
        super(context);
        this.page = page;
    }

    private interface Entry {
        Row build();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        List<Entry> entries = new ArrayList<>();

        entries.add(() -> row("Velocità",
                "La stessa frenata pesa diversamente a 60 o a 150 km/h: la velocità dà il contesto per leggere gli altri numeri."));
        entries.add(() -> row("Velocità massima",
                "Il punto più alto toccato in sessione. Confrontato tra uscite diverse, ti dice se stai prendendo più confidenza o sei più prudente."));
        entries.add(() -> row("G laterale",
                "Quanto carichi l'auto in curva. Se oscilla molto nella stessa curva ripetuta più volte, stai correggendo la traiettoria invece di tenerla pulita."));
        entries.add(() -> row("G frenata",
                "Decelerazione pura, isolata dall'accelerazione. Un salto secco indica una frenata tardiva; una salita progressiva è una frenata ben dosata."));
        entries.add(() -> row("G laterale massimo",
                "Il valore più alto toccato in curva. Se resta lontano dal tuo limite conosciuto hai margine, se lo tocchi spesso sei vicino all'aderenza massima."));
        entries.add(() -> row("G totale",
                "Sale quando freni e giri insieme, il momento in cui lo pneumatico è più carico. Utile per capire se rilasci il freno troppo tardi in ingresso curva."));
        entries.add(() -> row("Distanza percorsa",
                "Quanta strada hai coperto. Serve a dare senso agli altri numeri, ad esempio confrontare le frenate tra una sessione lunga e una breve."));
        entries.add(() -> row("Velocità media",
                "La media di tutta la sessione, non solo i picchi: dice se hai guidato con ritmo costante o a strappi tra tratti veloci e lenti."));
        entries.add(() -> row("Indice di fluidità",
                "Una guida fluida usa sterzo e pedali in modo progressivo, non a scatti. L'indice si abbassa sugli input bruschi, anche quelli che non noti mentre guidi: utile per capire se migliori sessione dopo sessione."));
        entries.add(() -> row("Frenate rilevate",
                "Conta solo le frenate decise, con decelerazione di almeno 0,35g: una frenata dolce o normale non fa scattare il conteggio, quindi è normale restare a zero in una guida tranquilla. Confrontato tra sessioni, dice se anticipi meglio i punti di frenata."));
        entries.add(() -> row("Lampeggio soglia",
                "Quando un nuovo massimo di sessione supera una soglia, il gauge lampeggia in rosso per qualche istante. Velocità: 50, 120, 150, 180, 200 km/h. Frenata: 0,4g, 0,6g, 0,8g, 1,0g. G laterale: 0,3g, 0,5g, 0,7g, 0,9g."));
        entries.add(() -> row("Telemetria",
                "Avvia registrazione salva un campione al secondo (velocità, G, posizione GPS) fino a quando premi Arresta registrazione. Alla fine viene generato un report CSV con l'immagine del percorso e, se configurato in Impostazioni email sul telefono, inviato automaticamente via email."));

        int limit = listContentLimit();

        ItemList.Builder list = new ItemList.Builder();
        int start = pageStartIndex(entries.size(), limit, page);

        int shown = 0;
        int i = start;
        int capacity = limit;
        for (; i < entries.size(); i++) {
            boolean isLastSlot = shown == capacity - 1;
            boolean moreAfterThis = i < entries.size() - 1;
            if (isLastSlot && moreAfterThis) {
                break; // lascia l'ultimo slot per "Altri..."
            }
            list.addItem(entries.get(i).build());
            shown++;
        }
        boolean hasMore = i < entries.size();
        if (hasMore) {
            int nextPage = page + 1;
            list.addItem(new Row.Builder()
                    .setTitle("Altri...")
                    .setOnClickListener(() -> getScreenManager().push(new GaugeGuideScreen(getCarContext(), nextPage)))
                    .build());
        }

        Header header = new Header.Builder()
                .setTitle(page == 0 ? "Guida agli indicatori" : "Guida agli indicatori - altro")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }

    /** Indice del primo elemento mostrato in questa pagina, tenendo conto che ogni
     *  pagina con un seguito riserva uno slot a "Altri...". Stesso schema di AnalysisScreen. */
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

    /** Legge il numero massimo di elementi che l'host corrente accetta in un ListTemplate.
     *  Se il servizio non è disponibile usa una soglia di sicurezza minima. */
    private int listContentLimit() {
        try {
            ConstraintManager cm = getCarContext().getCarService(ConstraintManager.class);
            if (cm != null) {
                int l = cm.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST);
                if (l > 0) return l;
            }
        } catch (Exception ignored) {
            // host senza questo servizio: usa il fallback prudente.
        }
        return FALLBACK_LIST_LIMIT;
    }

    private Row row(String title, String explanation) {
        return new Row.Builder().setTitle(title).addText(explanation).build();
    }
}
