package com.mx5.driversmetrics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

/**
 * Avviso mostrato una volta a ogni avvio, dopo l'avviso di sicurezza e prima della Home:
 * spiega come va posizionato il telefono perché G laterale, G frenata/accelerazione,
 * frenate rilevate e indice di fluidità siano attendibili (velocità, distanza e mappa
 * vengono dal GPS e non ne risentono, ma quei valori sì: derivano dall'accelerometro del
 * telefono, letto assumendo che sia fermo nella vaschetta a sinistra della leva del
 * cambio, accanto alla porta USB-C — la posizione reale usata su questa MX-5, appoggiato
 * piatto e non in piedi — sempre nello stesso verso da una sessione all'altra. In tasca,
 * in mano, o girato diversamente ogni volta, i valori non sono più attendibili né
 * confrontabili tra loro).
 *
 * A differenza di Splash e dell'avviso di sicurezza, qui NON c'è avanzamento automatico:
 * bisogna selezionare "OK" e premerlo con la rotella per proseguire, perché è
 * un'informazione da leggere consapevolmente, non solo intravedere per tre secondi.
 *
 * Usa il disegno diretto sulla Surface (come Splash) invece di un MessageTemplate
 * standard: la sua icona verrebbe compressa nel riquadro a 44dp riservato alle icone dei
 * template, troppo piccolo per uno schema leggibile della consolle.
 */
public final class MountWarningScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int WHITE = Color.parseColor("#F4F4FA");
    private static final int GRAY = Color.parseColor("#8A8A96");
    private static final int PHONE_BODY = Color.parseColor("#1C1C24");
    private static final int PHONE_SCREEN = Color.parseColor("#2E2E38");

    public MountWarningScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    private void proceed() {
        getScreenManager().push(new DashboardScreen(getCarContext()));
        finish();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("OK, ho capito")
                        .setOnClickListener(this::proceed)
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    /** Scrive una riga centrata con il bordo superiore del testo alla quota "yTop" (non
     *  la baseline, che va calcolata dai font metrics): tiene il layout leggibile anche
     *  al variare della risoluzione, componendo dall'alto verso il basso come sulla carta. */
    private static float drawCenteredTop(Canvas canvas, Paint paint, String text, float cx, float yTop) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(text, cx, yTop - fm.ascent, paint);
        return fm.descent - fm.ascent;
    }

    /** Disegna, visto dall'alto, lo schema della consolle reale (vaschetta con il
     *  telefono a sinistra della leva del cambio, porta USB-C) e il testo che spiega
     *  perché conta, componendo dall'alto verso il basso: proporzioni verificate su un
     *  rendering di prova per evitare sovrapposizioni tra gli elementi. */
    private void draw(Canvas canvas, Rect area) {
        float cx = area.centerX();
        float w = area.width();
        float h = area.height();
        float y = area.top + h * 0.09f;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(WHITE);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(h * 0.050f);
        y += drawCenteredTop(canvas, titlePaint, "Prima di partire", cx, y);
        y += h * 0.045f;

        Paint arrowLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowLabel.setColor(GRAY);
        arrowLabel.setTextAlign(Paint.Align.CENTER);
        arrowLabel.setFakeBoldText(true);
        arrowLabel.setTextSize(h * 0.022f);
        y += drawCenteredTop(canvas, arrowLabel, "SENSO DI MARCIA", cx, y);
        y += h * 0.012f;

        // Freccia verticale: indica il senso di marcia, come riferimento per l'orientamento.
        float arrowW = h * 0.05f;
        float headH = h * 0.028f;
        float shaftH = h * 0.03f;
        Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arrowPaint.setColor(GRAY);
        arrowPaint.setStyle(Paint.Style.FILL);
        Path head = new Path();
        head.moveTo(cx, y);
        head.lineTo(cx + arrowW / 2f, y + headH);
        head.lineTo(cx - arrowW / 2f, y + headH);
        head.close();
        canvas.drawPath(head, arrowPaint);
        canvas.drawRect(cx - arrowW * 0.13f, y + headH, cx + arrowW * 0.13f, y + headH + shaftH, arrowPaint);
        y += headH + shaftH + h * 0.02f;

        // Schema della consolle vista dall'alto: vaschetta con il telefono (piatto, non
        // in piedi) a sinistra della manopola del cambio, porta USB-C in alto a destra.
        float consoleW = w * 0.30f;
        float consoleH = h * 0.30f;
        float consoleX0 = cx - consoleW / 2f;
        float consoleY0 = y;
        RectF consoleRect = new RectF(consoleX0, consoleY0, consoleX0 + consoleW, consoleY0 + consoleH);
        Paint consoleStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        consoleStroke.setColor(GRAY);
        consoleStroke.setStyle(Paint.Style.STROKE);
        consoleStroke.setStrokeWidth(2f);
        canvas.drawRoundRect(consoleRect, 14f, 14f, consoleStroke);

        float knobR = consoleH * 0.20f;
        float knobCx = consoleX0 + consoleW * 0.66f;
        float knobCy = consoleY0 + consoleH * 0.58f;
        Paint knobStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        knobStroke.setStyle(Paint.Style.STROKE);
        knobStroke.setStrokeWidth(3f);
        knobStroke.setColor(WHITE);
        canvas.drawCircle(knobCx, knobCy, knobR, knobStroke);
        knobStroke.setStrokeWidth(2f);
        knobStroke.setColor(GRAY);
        canvas.drawCircle(knobCx, knobCy, knobR * 0.55f, knobStroke);

        float phoneW = consoleW * 0.24f;
        float phoneH = consoleH * 0.62f;
        float phoneX0 = consoleX0 + consoleW * 0.14f;
        float phoneY0 = consoleY0 + consoleH * 0.24f;
        RectF phoneRect = new RectF(phoneX0, phoneY0, phoneX0 + phoneW, phoneY0 + phoneH);
        float corner = phoneW * 0.18f;
        Paint phoneBody = new Paint(Paint.ANTI_ALIAS_FLAG);
        phoneBody.setColor(PHONE_BODY);
        canvas.drawRoundRect(phoneRect, corner, corner, phoneBody);
        Paint phoneStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        phoneStroke.setColor(WHITE);
        phoneStroke.setStyle(Paint.Style.STROKE);
        phoneStroke.setStrokeWidth(2f);
        canvas.drawRoundRect(phoneRect, corner, corner, phoneStroke);
        float inset = phoneW * 0.12f;
        RectF screenRect = new RectF(phoneRect.left + inset, phoneRect.top + inset * 1.6f,
                phoneRect.right - inset, phoneRect.bottom - inset * 1.6f);
        Paint screenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        screenPaint.setColor(PHONE_SCREEN);
        canvas.drawRoundRect(screenRect, phoneW * 0.09f, phoneW * 0.09f, screenPaint);

        // Fotocamera sul bordo del telefono più vicino al lato "cruscotto" della
        // consolle (in alto nello schema, come la porta USB-C): indica il verso confermato,
        // quello che il calcolo di G longitudinale in SensorHub assume come riferimento.
        Paint camPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        camPaint.setColor(WHITE);
        canvas.drawCircle(phoneRect.centerX(), phoneRect.top + inset, phoneW * 0.05f, camPaint);

        float usbW = consoleW * 0.16f;
        float usbH = consoleH * 0.10f;
        float usbX0 = consoleX0 + consoleW * 0.60f;
        float usbY0 = consoleY0 + consoleH * 0.06f;
        RectF usbRect = new RectF(usbX0, usbY0, usbX0 + usbW, usbY0 + usbH);
        canvas.drawRoundRect(usbRect, 4f, 4f, consoleStroke);
        Paint usbLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        usbLabel.setColor(GRAY);
        usbLabel.setTextAlign(Paint.Align.CENTER);
        usbLabel.setTextSize(h * 0.017f);
        drawCenteredTop(canvas, usbLabel, "USB-C", usbX0 + usbW / 2f, usbY0 + usbH + h * 0.004f);

        y = consoleY0 + consoleH + h * 0.05f;
        Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        captionPaint.setColor(WHITE);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setTextSize(h * 0.024f);
        y += drawCenteredTop(canvas, captionPaint, "Vaschetta a sinistra del cambio, fotocamera verso il cruscotto", cx, y);
        y += h * 0.05f;

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(WHITE);
        bodyPaint.setTextAlign(Paint.Align.CENTER);
        bodyPaint.setTextSize(h * 0.027f);
        float lineGap = h * 0.040f;
        String[] lines = {
                "Per dati affidabili, appoggia il telefono piatto (non in piedi)",
                "nella vaschetta a sinistra del cambio, schermo verso l'alto",
                "e fotocamera verso il cruscotto, come in figura.",
                "In tasca o in mano, G laterale, frenate e fluidità non sono",
                "attendibili (velocità e percorso restano validi).",
        };
        for (String line : lines) {
            drawCenteredTop(canvas, bodyPaint, line, cx, y);
            y += lineGap;
        }
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        GaugeSurfaceRenderer.getInstance().start(getCarContext(), this::draw);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        GaugeSurfaceRenderer.getInstance().stop();
    }
}
