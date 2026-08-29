package com.mx5.driversmetrics;

/** Un singolo campione di telemetria registrato durante una sessione di registrazione dati. */
final class TelemetrySample {
    final long timestampMs;
    final double lat, lon;
    final double speedKmh;
    final double latG, lonG;
    final double altitudeM, headingDeg, gpsAccuracyM;
    final double distanceKm;
    /** Numero del marker mostrato sull'immagine della mappa per questo campione, 0 se nessuno. */
    int mapMarkerIndex;

    TelemetrySample(long timestampMs, double lat, double lon, double speedKmh,
                     double latG, double lonG, double altitudeM, double headingDeg,
                     double gpsAccuracyM, double distanceKm) {
        this.timestampMs = timestampMs;
        this.lat = lat;
        this.lon = lon;
        this.speedKmh = speedKmh;
        this.latG = latG;
        this.lonG = lonG;
        this.altitudeM = altitudeM;
        this.headingDeg = headingDeg;
        this.gpsAccuracyM = gpsAccuracyM;
        this.distanceKm = distanceKm;
    }

    boolean hasFix() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }
}
