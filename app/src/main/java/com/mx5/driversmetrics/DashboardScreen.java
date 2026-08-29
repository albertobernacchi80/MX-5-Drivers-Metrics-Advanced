package com.mx5.driversmetrics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

/** Schermata Home a schermo intero: solo i due gauge grandi (Velocità, Accelerazione/Frenata)
 *  disegnati direttamente sulla Surface dell'app tramite GaugeSurfaceRenderer, invece delle
 *  piccole icone da 44dp di un GridTemplate (limite di design fisso di Android Auto, non
 *  risolvibile aumentando la risoluzione dei bitmap). In fondo, una semplice scritta di stato
 *  "TELEMETRIA ON/OFF" con pallino colorato. I vecchi riquadri "Registrazione dati" e "Analisi"
 *  sono stati tolti: erano solo disegni, non cliccabili né selezionabili con la rotella, e le
 *  stesse due destinazioni sono già raggiungibili dall'ActionStrip qui sotto — tenerli duplicava
 *  inutilmente l'icona di Analisi (per la sessione) e quella di Registrazione (per lo stato di
 *  telemetria, che resta invece come semplice indicazione testuale). Il disegno sulla Surface
 *  è solo decorativo/informativo: non gestisce click o rotella.
 *
 *  L'interazione reale (cambiare schermata) passa dall'ActionStrip qui sotto: è un elemento
 *  standard gestito dall'host, quindi selezionabile con la rotella esattamente come una voce
 *  di lista o griglia, anche sugli head unit senza touchscreen (il problema originale
 *  riscontrato sulla Mazda MX-5, dove il pulsante "Analisi" non era raggiungibile). Prima
 *  l'ActionStrip aveva 4 icone dirette (una per riquadro): su alcuni head unit reali (verificato
 *  sulla stessa Mazda MX-5) un NavigationTemplate senza contenuti di navigazione reali rende
 *  selezionabili con la rotella solo 2 azioni su 4 disegnate — le prime due (Velocità,
 *  Accelerazione) restavano irraggiungibili, mentre le altre due (Registrazione, Analisi)
 *  funzionavano. Ora l'ActionStrip ha solo 2 icone, ciascuna delle quali apre un menu
 *  (GaugesMenuScreen / DataMenuScreen, ListTemplate — nessun limite analogo) con le due
 *  destinazioni corrispondenti, invece di andarci direttamente. */
public final class DashboardScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int RED = Color.parseColor("#FF4D4D");
    private static final int WHITE = Color.parseColor("#F4F4FA");
    private static final int DIM = Color.parseColor("#8888A0");
    private static final int OFF_DOT = Color.parseColor("#464654");

    public DashboardScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        // Due sole icone (invece delle quattro di prima): su alcuni head unit reali
        // (verificato sulla Mazda MX-5) l'ActionStrip di un NavigationTemplate senza
        // contenuti di navigazione reali rende selezionabili con la rotella solo due
        // azioni su quattro, anche se tutte e quattro vengono disegnate — le prime due
        // (Velocità, Accelerazione) restavano irraggiungibili. Ogni icona apre invece
        // un menu (ListTemplate, senza questo limite) con le due destinazioni originali.
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setIcon(GaugeIcon.buildNavIcon(120, GaugeIcon.NavGlyph.SPEED))
                        .setOnClickListener(() -> getScreenManager().push(new GaugesMenuScreen(getCarContext())))
                        .build())
                .addAction(new Action.Builder()
                        .setIcon(GaugeIcon.buildNavIcon(120, GaugeIcon.NavGlyph.ANALYSIS))
                        .setOnClickListener(() -> getScreenManager().push(new DataMenuScreen(getCarContext())))
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    /** Disegna i due gauge grandi (Velocità, Accelerazione) affiancati, più una scritta di
     *  stato "TELEMETRIA ON/OFF" in una fascia in fondo: richiamato periodicamente da
     *  GaugeSurfaceRenderer, legge i valori correnti a ogni chiamata. */
    private void draw(Canvas canvas, Rect visibleArea) {
        DrivingState s = SensorHub.state;
        int speedColor = ThresholdColors.blinkColor(s.speedFlashUntilMs, RED);
        boolean recording = TelemetryRecorder.getInstance().isRecording();

        float left = visibleArea.left, top = visibleArea.top;
        float fullW = visibleArea.width(), fullH = visibleArea.height();
        float footerH = Math.min(fullH * 0.16f, 92f * (fullH / 720f));
        float gaugesH = fullH - footerH;
        float w = fullW / 2f;

        RectF speedBox = new RectF(left, top, left + w, top + gaugesH);
        RectF accelBox = new RectF(left + w, top, left + fullW, top + gaugesH);

        GaugeIcon.drawTile(canvas, speedBox, "Velocità", s.speedKmh, 220, "km/h", speedColor, false);
        GaugeIcon.drawTile(canvas, accelBox, "Accelerazione", Math.hypot(s.latG, s.lonG), 1.3, "G", WHITE, false);

        Paint separator = new Paint(Paint.ANTI_ALIAS_FLAG);
        separator.setColor(Color.parseColor("#18181E"));
        separator.setStrokeWidth(2f);
        canvas.drawLine(left, top + gaugesH, left + fullW, top + gaugesH, separator);

        float footerCy = top + gaugesH + footerH / 2f;
        String statusText = recording ? "TELEMETRIA ON" : "TELEMETRIA OFF";
        int dotColor = recording ? RED : OFF_DOT;
        int textColor = recording ? WHITE : DIM;
        float dotR = Math.min(footerH * 0.22f, 9f * (fullH / 720f));

        Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusPaint.setColor(textColor);
        statusPaint.setFakeBoldText(true);
        statusPaint.setTextSize(footerH * 0.36f);
        statusPaint.setTextAlign(Paint.Align.LEFT);
        float textW = statusPaint.measureText(statusText);
        float gap = footerH * 0.18f;
        float totalW = dotR * 2 + gap + textW;
        float startX = left + fullW / 2f - totalW / 2f;
        float dotCx = startX + dotR;

        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(dotColor);
        canvas.drawCircle(dotCx, footerCy, dotR, dotPaint);

        Paint.FontMetrics fm = statusPaint.getFontMetrics();
        float textBaseline = footerCy - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(statusText, startX + dotR * 2 + gap, textBaseline, statusPaint);
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        SensorHub.ensureStarted(getCarContext());
        GaugeSurfaceRenderer.getInstance().start(getCarContext(), this::draw);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        GaugeSurfaceRenderer.getInstance().stop();
    }
}
