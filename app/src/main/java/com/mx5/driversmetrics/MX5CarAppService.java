package com.mx5.driversmetrics;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.validation.HostValidator;
import androidx.car.app.Session;

public final class MX5CarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        // Safe for local Android Auto testing. Before release, replace with the
        // official host allow-list strategy recommended by Android for Cars.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull @Override public Session onCreateSession() {
        return new MX5Session();
    }
}
