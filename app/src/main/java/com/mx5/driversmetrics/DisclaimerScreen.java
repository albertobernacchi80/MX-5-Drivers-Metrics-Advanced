package com.mx5.driversmetrics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;

/**
 * Popup di avviso tra lo Splash e l'avviso sul posizionamento del telefono
 * (MountWarningScreen): resta visibile 3 secondi, poi passa automaticamente
 * e si rimuove dallo stack.
 */
public final class DisclaimerScreen extends Screen implements androidx.lifecycle.DefaultLifecycleObserver {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable goHome = () -> {
        getScreenManager().push(new MountWarningScreen(getCarContext()));
        finish();
    };

    public DisclaimerScreen(@NonNull CarContext context) {
        super(context);
        getLifecycle().addObserver(this);
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new MessageTemplate.Builder(
                "Presta attenzione alla guida, non solo allo schermo.")
                .setIcon(warningIcon())
                .build();
    }

    /** Triangolo di avviso con punto esclamativo, disegnato a Canvas (nessuna risorsa esterna). */
    private CarIcon warningIcon() {
        int w = 240, h = 220;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.parseColor("#FF4D4D"));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(w * 0.055f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        Path tri = new Path();
        tri.moveTo(w * 0.5f, h * 0.08f);
        tri.lineTo(w * 0.92f, h * 0.88f);
        tri.lineTo(w * 0.08f, h * 0.88f);
        tri.close();
        c.drawPath(tri, stroke);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(Color.parseColor("#FF4D4D"));
        fill.setStyle(Paint.Style.FILL);

        RectF bar = new RectF(w * 0.465f, h * 0.34f, w * 0.535f, h * 0.62f);
        c.drawRoundRect(bar, w * 0.03f, w * 0.03f, fill);
        c.drawCircle(w * 0.5f, h * 0.73f, w * 0.037f, fill);

        return new CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build();
    }

    @Override
    public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        handler.postDelayed(goHome, 3000);
    }

    @Override
    public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner) {
        handler.removeCallbacks(goHome);
    }
}
