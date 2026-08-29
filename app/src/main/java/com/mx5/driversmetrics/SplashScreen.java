package com.mx5.driversmetrics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

/**
 * Schermata di avvio a schermo intero: silhouette reale della MX-5 e titolo ("Mx-5" in
 * rosso, il resto in bianco) disegnati grandi direttamente sulla Surface, con la stessa
 * tecnica usata per i gauge di Home/Velocità/Accelerazione. Prima usava un MessageTemplate
 * standard, la cui icona e il cui testo hanno una dimensione fissata dall'host senza nessun
 * parametro che l'app possa controllare — lo stesso tipo di limite del riquadro a 44dp delle
 * icone dei template, qui applicato anche al testo: per questo risultava minuscola.
 *
 * Dopo una breve pausa passa automaticamente alla Home e si rimuove dallo stack, così il
 * tasto indietro dalla Home non torna qui. Nell'ActionStrip c'è comunque un pulsante "Salta",
 * selezionabile con la rotella, per chi non vuole aspettare la pausa.
 */
public final class SplashScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private static final int RED = Color.parseColor("#FF4D4D");
    private static final int WHITE = Color.parseColor("#F4F4FA");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goHome = this::goToHome;
    private Bitmap silhouette;
    // Sia il timer automatico (1,6s) sia il pulsante "Salta" chiamano goToHome(): se
    // l'utente preme "Salta" con la rotella proprio a ridosso dello scadere del timer,
    // entrambi potrebbero arrivare quasi insieme sulla stessa coda di eventi ed eseguire
    // goToHome() due volte, con un doppio push di DisclaimerScreen. Questo flag rende
    // l'operazione idempotente: solo la prima chiamata ha effetto.
    private boolean navigated = false;

    public SplashScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    private void goToHome() {
        if (navigated) return;
        navigated = true;
        getScreenManager().push(new DisclaimerScreen(getCarContext()));
        finish();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("Salta")
                        .setOnClickListener(this::goToHome)
                        .build())
                .build();

        return new NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build();
    }

    /** Disegna silhouette + titolo centrati, il più grandi possibile nell'area disponibile. */
    private void draw(Canvas canvas, Rect visibleArea) {
        if (silhouette == null) {
            silhouette = BitmapFactory.decodeResource(getCarContext().getResources(), R.drawable.car_silhouette);
        }

        float cx = visibleArea.centerX();
        float cy = visibleArea.centerY();
        float areaW = visibleArea.width();
        float areaH = visibleArea.height();

        if (silhouette != null) {
            float maxW = areaW * 0.78f;
            float maxH = areaH * 0.42f;
            float scale = Math.min(maxW / silhouette.getWidth(), maxH / silhouette.getHeight());
            float w = silhouette.getWidth() * scale;
            float h = silhouette.getHeight() * scale;
            float offsetY = areaH * 0.06f;
            RectF dst = new RectF(cx - w / 2f, cy - h / 2f - offsetY, cx + w / 2f, cy + h / 2f - offsetY);
            Paint imgPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(silhouette, null, dst, imgPaint);
        }

        String mx5 = "MX-5";
        String rest = " Driver Metrics Advanced";
        float titleY = cy + areaH * 0.22f;
        float titleSize = Math.min(areaW, areaH) * 0.075f;

        Paint mx5Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mx5Paint.setColor(RED);
        mx5Paint.setFakeBoldText(true);
        mx5Paint.setTextSize(titleSize);

        Paint restPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        restPaint.setColor(WHITE);
        restPaint.setFakeBoldText(true);
        restPaint.setTextSize(titleSize);

        float mx5W = mx5Paint.measureText(mx5);
        float restW = restPaint.measureText(rest);
        float startX = cx - (mx5W + restW) / 2f;

        canvas.drawText(mx5, startX, titleY, mx5Paint);
        canvas.drawText(rest, startX + mx5W, titleY, restPaint);
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        GaugeSurfaceRenderer.getInstance().start(getCarContext(), this::draw);
        handler.postDelayed(goHome, 1600);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        handler.removeCallbacks(goHome);
        GaugeSurfaceRenderer.getInstance().stop();
    }
}
