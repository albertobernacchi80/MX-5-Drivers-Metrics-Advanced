package com.mx5.driversmetrics;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

/** Schermata dedicata ad accelerazione/frenata, a schermo intero: 4 riquadri grandi in
 *  griglia 2x2 (G totale, G laterale, G frenata, G laterale massimo), disegnati sulla
 *  Surface invece che come icone di griglia limitate a 44dp. L'unica interazione (tornare
 *  indietro) passa dall'ActionStrip, selezionabile con la rotella come un normale pulsante
 *  di lista. */
public final class AccelScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int RED = Color.parseColor("#FF4D4D");
    private static final int WHITE = Color.parseColor("#F4F4FA");

    public AccelScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("Indietro")
                        .setOnClickListener(() -> getScreenManager().pop())
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    private void draw(Canvas canvas, Rect visibleArea) {
        DrivingState s = SensorHub.state;
        int lateralColor = ThresholdColors.blinkColor(s.lateralFlashUntilMs, WHITE);
        int lateralMaxColor = ThresholdColors.blinkColor(s.lateralFlashUntilMs, RED);
        int brakeColor = ThresholdColors.blinkColor(s.brakeFlashUntilMs, WHITE);

        float left = visibleArea.left, top = visibleArea.top;
        float w = visibleArea.width() / 2f, h = visibleArea.height() / 2f;

        RectF totalBox = new RectF(left, top, left + w, top + h);
        RectF lateralBox = new RectF(left + w, top, left + 2 * w, top + h);
        RectF brakeBox = new RectF(left, top + h, left + w, top + 2 * h);
        RectF lateralMaxBox = new RectF(left + w, top + h, left + 2 * w, top + 2 * h);

        GaugeIcon.drawTile(canvas, totalBox, "G totale", Math.hypot(s.latG, s.lonG), 1.3, "G", RED, false);
        GaugeIcon.drawTile(canvas, lateralBox, "G laterale", Math.abs(s.latG), 1.3, "G", lateralColor, false);
        GaugeIcon.drawTile(canvas, brakeBox, "G frenata", Math.max(0, -s.lonG), 1.3, "G", brakeColor, false);
        GaugeIcon.drawTile(canvas, lateralMaxBox, "G laterale massimo", s.maxLatG, 1.3, "G", lateralMaxColor, false);
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
