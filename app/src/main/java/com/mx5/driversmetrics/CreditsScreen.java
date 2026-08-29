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

/** Crediti e note legali dell'applicazione. */
public final class CreditsScreen extends Screen {

    public CreditsScreen(@NonNull CarContext context) {
        super(context);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ItemList.Builder list = new ItemList.Builder();

        list.addItem(row("MX-5 Driver Metrics Advanced",
                "Applicazione sviluppata da Alberto Bernacchi."));

        list.addItem(row("Copyright",
                "© 2026 Alberto Bernacchi. Tutti i diritti riservati. "
                        + "È vietata la riproduzione, distribuzione o modifica non autorizzata "
                        + "di questa applicazione o di sue parti."));

        list.addItem(row("Uso previsto",
                "Applicazione per uso personale, non destinata alla distribuzione o vendita a terzi."));

        list.addItem(row("Marchi di terze parti",
                "Applicazione indipendente, non affiliata, sponsorizzata o approvata da Mazda Motor Corporation. "
                        + "\"Mazda\" e \"MX-5\" sono marchi registrati dei rispettivi proprietari, "
                        + "citati qui solo a scopo descrittivo."));

        list.addItem(row("Nota sui dati",
                "I valori mostrati (velocità, accelerazione, frenata) derivano dai sensori del telefono "
                        + "e dal GPS: sono indicativi, non misurazioni certificate. "
                        + "Non fare affidamento su questi dati durante la guida in condizioni "
                        + "che richiedano piena attenzione."));

        Header header = new Header.Builder()
                .setTitle("Crediti")
                .setStartHeaderAction(Action.BACK)
                .build();

        return new ListTemplate.Builder()
                .setHeader(header)
                .setSingleList(list.build())
                .build();
    }

    private Row row(String title, String text) {
        return new Row.Builder().setTitle(title).addText(text).build();
    }
}
