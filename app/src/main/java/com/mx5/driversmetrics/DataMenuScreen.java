package com.mx5.driversmetrics;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

/**
 * Menu intermedio aperto dalla seconda icona della barra in Home: sceglie tra Analisi
 * (riepilogo della sessione) e Telemetria (avvio/arresto della registrazione dati).
 *
 * Stesso motivo di GaugesMenuScreen: raggruppa in una ListTemplate le destinazioni che
 * l'ActionStrip di DashboardScreen non riesce a rendere tutte selezionabili con la
 * rotella su alcuni head unit.
 */
public final class DataMenuScreen extends Screen {

    public DataMenuScreen(@NonNull CarContext context) {
        super(context);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();

        list.addItem(new Row.Builder()
                .setTitle("Analisi")
                .addText("Riepilogo della sessione di guida in corso")
                .setOnClickListener(() -> getScreenManager().push(new AnalysisScreen(getCarContext())))
                .build());

        list.addItem(new Row.Builder()
                .setTitle("Telemetria")
                .addText("Avvia o arresta la registrazione di velocità, G e posizione GPS")
                .setOnClickListener(() -> getScreenManager().push(new RecordingScreen(getCarContext())))
                .build());

        Header header = new Header.Builder()
                .setTitle("Analisi e Telemetria")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }
}
