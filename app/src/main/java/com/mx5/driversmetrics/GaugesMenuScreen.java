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
 * Menu intermedio aperto dalla prima icona della barra in Home: sceglie tra le due
 * schermate di dettaglio con i gauge grandi (Velocità, Accelerazione/Frenata).
 *
 * Introdotto perché l'ActionStrip di DashboardScreen, pur disegnando quattro icone,
 * su alcuni head unit (verificato sulla Mazda MX-5 reale) ne rende selezionabili con
 * la rotella solo due delle quattro definite — le prime due (Velocità, Accelerazione)
 * restavano irraggiungibili. Raggruppando le quattro destinazioni in due soli tasti,
 * ciascuno dei quali apre una ListTemplate con le due voci corrispondenti, il problema
 * non si pone più: le liste non hanno questo limite.
 */
public final class GaugesMenuScreen extends Screen {

    public GaugesMenuScreen(@NonNull CarContext context) {
        super(context);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();

        list.addItem(new Row.Builder()
                .setTitle("Velocità")
                .addText("Velocità attuale e velocità massima di sessione")
                .setOnClickListener(() -> getScreenManager().push(new SpeedScreen(getCarContext())))
                .build());

        list.addItem(new Row.Builder()
                .setTitle("Accelerazione / Frenata")
                .addText("G totale, G laterale, G frenata, G laterale massimo")
                .setOnClickListener(() -> getScreenManager().push(new AccelScreen(getCarContext())))
                .build());

        Header header = new Header.Builder()
                .setTitle("Velocità e Accelerazione")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }
}
