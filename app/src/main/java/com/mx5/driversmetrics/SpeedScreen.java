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

/** Schermata dedicata alla velocità, a schermo intero: 2 riquadri grandi affiancati
 *  (attuale e massima), ciascuno grande metà schermo, disegnati sulla Surface invece che
 *  come icone di griglia limitate a 44dp. L'unica interazione (tornare indietro) passa
 *  dall'ActionStrip, selezionabile con la rotella come un normale pulsante di lista. */
public final class SpeedScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int RED = Color.parseColor("#FF4D4D");

    public SpeedScreen(@NonNull CarContext context) {
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
        int speedColor = ThresholdColors.blinkColor(s.speedFlashUntilMs, RED);

        float left = visibleArea.left, top = visibleArea.top;
        float w = visibleArea.width() / 2f, h = visibleArea.height();

        RectF currentBox = new RectF(left, top, left + w, top + h);
        RectF maxBox = new RectF(left + w, top, left + 2 * w, top + h);

        GaugeIcon.drawTile(canvas, currentBox, "Velocità attuale", s.speedKmh, 220, "km/h", speedColor, false);
        GaugeIcon.drawTile(canvas, maxBox, "Velocità massima", s.maxSpeedKmh, 220, "km/h", speedColor, false);
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
