package com.mx5.driversmetrics;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.car.app.Session;
import androidx.car.app.Screen;

public final class MX5Session extends Session {
    @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
        return new SplashScreen(getCarContext());
    }
}
