package com.mx5.driversmetrics;

public final class DrivingState {
    public double speedKmh = 0, maxSpeedKmh = 0;
    public double latG = 0, lonG = 0, maxLatG = 0, maxBrakeG = 0, maxAccelG = 0;
    public double altitudeM = Double.NaN, headingDeg = Double.NaN, gpsAccuracyM = Double.NaN;
    public double distanceKm = 0, avgSpeedKmh = 0, verticalG = 0, rollRate = 0, yawRate = 0;
    public double fluidity = Double.NaN, avgLatG = 0, avgLonG = 0;
    public int brakeCount = 0;
    public boolean gpsOk = false, motionOk = false;
    public long startMs = System.currentTimeMillis();
    public long samples = 0;
    public double latSum = 0, lonSum = 0;

    /** Ultima posizione nota, usata per la registrazione dati e la mappa del percorso. */
    public double lat = Double.NaN, lon = Double.NaN;

    /** Scadenza del lampeggio soglia per velocità massima, frenata massima e G laterale massimo:
     *  quando System.currentTimeMillis() < *FlashUntilMs il gauge deve lampeggiare in rosso
     *  (vedi ThresholdColors.blinkColor). */
    public long speedFlashUntilMs = 0, brakeFlashUntilMs = 0, lateralFlashUntilMs = 0;

    public void reset() {
        speedKmh=maxSpeedKmh=latG=lonG=maxLatG=maxBrakeG=maxAccelG=distanceKm=avgSpeedKmh=verticalG=rollRate=yawRate=avgLatG=avgLonG=0;
        altitudeM=headingDeg=gpsAccuracyM=fluidity=Double.NaN; brakeCount=0; samples=0; latSum=lonSum=0;
        startMs=System.currentTimeMillis();
        speedFlashUntilMs=brakeFlashUntilMs=lateralFlashUntilMs=0;
    }
}
