package com.mx5.driversmetrics;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.OnRequestPermissionsListener;

import java.util.List;

/**
 * Sensori e GPS condivisi tra tutte le schermate dell'app, così i dati continuano
 * ad arrivare anche passando da Home a Velocità/Accelerazione/Analisi e viceversa,
 * senza dover riavviare i sensori a ogni cambio schermata.
 */
public final class SensorHub implements SensorEventListener, LocationListener {

    public static final DrivingState state = new DrivingState();
    private static final SensorHub INSTANCE = new SensorHub();

    // Non più "private": DrivingReportBuilder la usa per contare le frenate decise nel
    // report descrittivo allegato via email, con la stessa identica soglia di qui — un'unica
    // fonte di verità, invece di duplicare il numero 0.35 in un secondo file.
    static final double BRAKE_THRESHOLD = 0.35;

    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor linear, accelerometer;
    private boolean running;
    private boolean permissionRequestInFlight;
    private float gravityX, gravityY, gravityZ = 9.80665f;

    // per calcolo distanza/velocità media
    private Double lastLat, lastLon;

    // per calcolo fluidità (jerk) e medie G
    private double lastGTot = 0;
    private long lastMotionNanos = 0;
    private double jerkSum = 0;
    private long jerkCount = 0;
    private boolean wasBraking = false;

    private SensorHub() {
    }

    public static void ensureStarted(CarContext carContext) {
        INSTANCE.ensureSensorsIfPermitted(carContext);
    }

    /** Azzera tutte le statistiche di sessione (massimi, medie, distanza, ecc). */
    public static void resetSession() {
        state.reset();
        INSTANCE.lastLat = null;
        INSTANCE.lastLon = null;
        INSTANCE.lastGTot = 0;
        INSTANCE.lastMotionNanos = 0;
        INSTANCE.jerkSum = 0;
        INSTANCE.jerkCount = 0;
        INSTANCE.wasBraking = false;
    }

    private boolean permissionsOk(CarContext carContext) {
        return carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureSensorsIfPermitted(CarContext carContext) {
        if (permissionsOk(carContext)) {
            start(carContext);
        } else if (!permissionRequestInFlight) {
            permissionRequestInFlight = true;
            carContext.requestPermissions(
                    List.of(Manifest.permission.ACCESS_FINE_LOCATION),
                    new OnRequestPermissionsListener() {
                        @Override
                        public void onRequestPermissionsResult(
                                @NonNull List<String> granted,
                                @NonNull List<String> rejected) {
                            permissionRequestInFlight = false;
                            if (permissionsOk(carContext)) start(carContext);
                        }
                    });
        }
    }

    private void start(CarContext carContext) {
        if (running) return;
        running = true;

        sensorManager = (SensorManager) carContext.getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) carContext.getSystemService(Context.LOCATION_SERVICE);

        linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (linear != null) {
            sensorManager.registerListener(this, linear, SensorManager.SENSOR_DELAY_GAME);
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 500, 0.5f, this);
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        state.motionOk = true;
        float ax, ay;

        if (e.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            ax = e.values[0];
            ay = e.values[1];
        } else {
            gravityX = .85f * gravityX + .15f * e.values[0];
            gravityY = .85f * gravityY + .15f * e.values[1];
            gravityZ = .85f * gravityZ + .15f * e.values[2];
            ax = e.values[0] - gravityX;
            ay = e.values[1] - gravityY;
        }

        // Il telefono sta appoggiato PIATTO (schermo verso l'alto, non in piedi) nella
        // vaschetta a sinistra della leva del cambio, con il bordo della fotocamera
        // rivolto verso il cruscotto — cioè nel senso di marcia (posizione reale
        // confermata, la stessa richiamata nell'avviso "Prima di partire" e nel manuale,
        // sezione 15). In questa posizione l'asse Y del telefono (dal basso verso la
        // fotocamera) è allineato con l'asse longitudinale dell'auto: accelerando ay
        // sale, frenando scende sotto zero, coerente con isBraking più sotto. L'asse X
        // (i due bordi lunghi del telefono) è allineato con l'asse laterale: qui il
        // segno non conta, perché laterale viene sempre usato in valore assoluto.
        // Non serve nessuna correzione tra i due assi, a differenza di un supporto da
        // cruscotto con il telefono in piedi, dove sarebbe invece l'asse Z (uscente
        // dallo schermo) a captare l'accelerazione longitudinale.
        double lateral = ax;
        double longitudinal = ay;

        state.latG = .75 * state.latG + .25 * lateral / 9.80665;
        state.lonG = .75 * state.lonG + .25 * longitudinal / 9.80665;

        double prevMaxLatG = state.maxLatG;
        double prevMaxBrakeG = state.maxBrakeG;
        state.maxLatG = Math.max(state.maxLatG, Math.abs(state.latG));
        state.maxBrakeG = Math.max(state.maxBrakeG, -state.lonG);
        state.maxAccelG = Math.max(state.maxAccelG, state.lonG);

        if (ThresholdColors.crossedNewBand(prevMaxBrakeG, state.maxBrakeG, ThresholdColors.BRAKE_THRESHOLDS)) {
            state.brakeFlashUntilMs = System.currentTimeMillis() + ThresholdColors.FLASH_DURATION_MS;
        }
        if (ThresholdColors.crossedNewBand(prevMaxLatG, state.maxLatG, ThresholdColors.LATERAL_THRESHOLDS)) {
            state.lateralFlashUntilMs = System.currentTimeMillis() + ThresholdColors.FLASH_DURATION_MS;
        }

        // --- indice di fluidità (basato sul jerk, variazione di G nel tempo) ---
        double gTotNow = Math.hypot(state.latG, state.lonG);
        long now = SystemClock.elapsedRealtimeNanos();
        if (lastMotionNanos != 0) {
            double dt = (now - lastMotionNanos) / 1_000_000_000.0;
            if (dt > 0) {
                jerkSum += Math.abs(gTotNow - lastGTot) / dt;
                jerkCount++;
                double avgJerk = jerkSum / jerkCount;
                state.fluidity = Math.max(0, Math.min(100, 100 - avgJerk * 40));
            }
        }
        lastGTot = gTotNow;
        lastMotionNanos = now;

        // --- medie G di sessione ---
        state.samples++;
        state.latSum += Math.abs(state.latG);
        state.lonSum += Math.abs(state.lonG);
        state.avgLatG = state.latSum / state.samples;
        state.avgLonG = state.lonSum / state.samples;

        // --- conteggio frenate (soglia con debounce) ---
        boolean isBraking = state.lonG < -BRAKE_THRESHOLD;
        if (isBraking && !wasBraking) {
            state.brakeCount++;
        }
        wasBraking = isBraking;
    }

    @Override
    public void onLocationChanged(@NonNull Location l) {
        state.gpsOk = true;
        state.lat = l.getLatitude();
        state.lon = l.getLongitude();
        state.speedKmh = Math.max(0, l.getSpeed() * 3.6);
        double prevMaxSpeed = state.maxSpeedKmh;
        state.maxSpeedKmh = Math.max(state.maxSpeedKmh, state.speedKmh);
        if (ThresholdColors.crossedNewBand(prevMaxSpeed, state.maxSpeedKmh, ThresholdColors.SPEED_THRESHOLDS)) {
            state.speedFlashUntilMs = System.currentTimeMillis() + ThresholdColors.FLASH_DURATION_MS;
        }
        state.gpsAccuracyM = l.getAccuracy();
        if (l.hasAltitude()) state.altitudeM = l.getAltitude();
        if (l.hasBearing()) state.headingDeg = l.getBearing();

        if (lastLat != null && l.getAccuracy() <= 30) {
            double d = haversineKm(lastLat, lastLon, l.getLatitude(), l.getLongitude());
            if (d < 1) state.distanceKm += d; // scarta salti GPS anomali
        }
        lastLat = l.getLatitude();
        lastLon = l.getLongitude();

        double elapsedH = (System.currentTimeMillis() - state.startMs) / 3_600_000.0;
        state.avgSpeedKmh = elapsedH > 0 ? state.distanceKm / elapsedH : 0;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public void onProviderEnabled(@NonNull String provider) {}
    @Override public void onProviderDisabled(@NonNull String provider) { state.gpsOk = false; }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
}
