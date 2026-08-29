package com.mx5.driversmetrics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Genera il resoconto descrittivo in linguaggio naturale di una sessione registrata —
 * una descrizione dello stile di guida (percorso, ritmo, frenate, accelerazioni, curve)
 * pensata per essere letta dal pilota, non un elenco di dati tecnici. Generato
 * automaticamente a ogni invio email, senza dover fare nulla: il testo finisce nel corpo
 * del messaggio stesso (vedi EmailSender), non in un file separato.
 *
 * Riconosce automaticamente tre situazioni, sulla sola base dei dati registrati (mai per
 * nome del luogo):
 * 1) Percorso troppo breve (sotto 1 km): sotto questa soglia i dati non bastano per
 *    distinguere uno stile di guida reale da un semplice spostamento (uscita dal
 *    parcheggio, una manovra), quindi il resoconto lo dice esplicitamente invece di
 *    inventare una descrizione poco significativa.
 * 2) Circuito: se il percorso GPS torna più volte vicino allo stesso punto di partenza
 *    (almeno due passaggi, con riscontro sul punto più lontano del primo giro), è
 *    trattato come giri ripetuti su un tracciato chiuso — testo dedicato con tempi giro,
 *    regolarità, tendenza nel tempo e consigli per migliorare (qui, e SOLO qui, ha senso
 *    parlare di spingere di più e limare il cronometro).
 * 3) Guida su strada aperta (la maggioranza dei casi): il percorso viene classificato in
 *    base ai dati (soste, velocità massima, banda di velocità dominante) come a carattere
 *    urbano, misto o extraurbano — non un giro, un tragitto: "giro/giri" nel testo è
 *    riservato ai soli passaggi di un circuito rilevato, per non creare confusione con un
 *    vero giro di pista.
 *
 * Non sostituisce CSV ed Excel (restano i dati grezzi, campione per campione, per chi
 * vuole i numeri): questo testo usa le stesse soglie e definizioni del resto dell'app
 * (frenata "decisa" = 0,35g, coerente con SensorHub e con la schermata Analisi) solo per
 * classificare lo stile di guida in frasi descrittive, riducendo al minimo i valori
 * numerici — restano solo quelli utili a inquadrare il tragitto (distanza, durata,
 * velocità massima) e i momenti salienti citati esplicitamente. Dove possibile, i momenti
 * citati riportano anche la lettera/numero del punto sulla mappa del percorso più vicino
 * (stessa etichetta di CSV, Excel e dell'immagine — vedi RouteMapBuilder.markerLabel), non
 * solo l'orario.
 *
 * Il testo descrive solo cosa è successo (dati), mai perché si stava guidando: non c'è
 * modo di saperlo dai dati, quindi niente ipotesi sullo scopo o sull'umore di chi guida.
 */
final class DrivingReportBuilder {

    private DrivingReportBuilder() {
    }

    /** Sotto questa distanza totale non si genera l'analisi descrittiva: troppo pochi dati
     *  per distinguere uno stile di guida reale da un semplice spostamento. */
    private static final double MIN_DISTANCE_KM = 1.0;

    /** Soglia informale, solo descrittiva, per segnalare un rallentamento "percepibile" anche
     *  quando non raggiunge la soglia di "frenata decisa" dell'app (0,35g, vedi SensorHub). */
    private static final double MILD_DECEL_THRESHOLD = 0.15;
    private static final double MILD_ACCEL_THRESHOLD = 0.15;
    private static final double CURVE_EVENT_THRESHOLD = 0.15;
    private static final double STRONG_LATERAL_THRESHOLD = 0.25;
    /** Sotto questa velocità un campione conta come "fermo" (semafori, code, soste). */
    private static final double STOPPED_KMH = 3.0;
    /** Una sosta conta nel resoconto solo se dura almeno questo tempo. */
    private static final int MIN_STOP_SAMPLES = 5;

    /** Raggio (in metri) oltre il quale un punto è considerato "lontano dal via", e raggio
     *  entro cui un ritorno vicino al via conta come passaggio/fine giro. Vedi detectCircuit. */
    private static final double CIRCUIT_FAR_M = 125.0;
    private static final double CIRCUIT_NEAR_M = 50.0;

    static String buildText(List<TelemetrySample> samples, long sessionStartMs) {
        StringBuilder header = new StringBuilder();
        header.append("MX-5 Driver Metrics Advanced - Analisi descrittiva della guida\n");
        header.append("================================================================\n\n");

        if (samples.isEmpty()) {
            return header.append("Nessun campione registrato in questa sessione.\n").toString();
        }

        double distanceTot = 0;
        for (TelemetrySample s : samples) distanceTot = Math.max(distanceTot, s.distanceKm);
        if (distanceTot < MIN_DISTANCE_KM) {
            return header.append("Percorso troppo breve (").append(dec(distanceTot)).append(" km) per "
                    + "un'analisi descrittiva attendibile: sotto 1 km i dati non bastano per distinguere "
                    + "uno stile di guida reale da un semplice spostamento (uscita dal parcheggio, una "
                    + "manovra, un breve tratto). Restano comunque validi i dati grezzi in allegato (CSV "
                    + "ed Excel) e, se generata, la mappa del percorso.\n").toString();
        }

        SimpleDateFormat headerFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);
        header.append("Sessione del ").append(headerFmt.format(new Date(sessionStartMs)))
                .append(", durata ").append(formatDuration(sessionDurationSec(samples, sessionStartMs))).append(".\n\n");

        CircuitInfo circuit = detectCircuit(samples);
        String body = circuit != null
                ? buildCircuitReport(samples, sessionStartMs, circuit)
                : buildNormalReport(samples, sessionStartMs);
        return (header.toString() + body).trim();
    }

    private static long sessionDurationSec(List<TelemetrySample> samples, long sessionStartMs) {
        long endMs = samples.get(samples.size() - 1).timestampMs;
        return Math.max(1, (endMs - sessionStartMs) / 1000);
    }

    // ---------------------------------------------------------------- riconoscimento circuito

    /** Ritorna null se il percorso non sembra un circuito, altrimenti le posizioni con fix GPS
     *  valido e gli indici (in quella lista) di ogni ritorno vicino al punto di partenza — la
     *  fine di ogni giro. Il riconoscimento è puramente geometrico (nessun nome di luogo): il
     *  percorso deve allontanarsi di oltre CIRCUIT_FAR_M dal punto di partenza e poi tornarci
     *  entro CIRCUIT_NEAR_M almeno due volte; per escludere un falso positivo (es. un
     *  parcheggio in cui si passa due volte vicino allo stesso punto per caso), il secondo
     *  giro deve ripassare vicino al punto più lontano toccato nel primo giro — a meno che i
     *  passaggi non siano già almeno tre, riscontro più solido da solo. */
    private static CircuitInfo detectCircuit(List<TelemetrySample> samples) {
        List<TelemetrySample> fix = new ArrayList<>();
        for (TelemetrySample s : samples) if (s.hasFix()) fix.add(s);
        if (fix.size() < 40) return null;

        int refN = Math.min(3, fix.size());
        double refLat = 0, refLon = 0;
        for (int i = 0; i < refN; i++) {
            refLat += fix.get(i).lat;
            refLon += fix.get(i).lon;
        }
        refLat /= refN;
        refLon /= refN;

        boolean away = false;
        List<Integer> passages = new ArrayList<>();
        for (int i = 0; i < fix.size(); i++) {
            double d = haversineMeters(fix.get(i).lat, fix.get(i).lon, refLat, refLon);
            if (!away && d > CIRCUIT_FAR_M) {
                away = true;
            } else if (away && d <= CIRCUIT_NEAR_M) {
                passages.add(i);
                away = false;
            }
        }
        if (passages.size() < 2) return null;

        // apice del primo giro: punto più lontano dal via prima del primo passaggio
        int firstPassage = passages.get(0);
        int apexIdx = 0;
        double apexD = -1;
        for (int i = 0; i <= firstPassage; i++) {
            double d = haversineMeters(fix.get(i).lat, fix.get(i).lon, refLat, refLon);
            if (d > apexD) {
                apexD = d;
                apexIdx = i;
            }
        }
        TelemetrySample apex = fix.get(apexIdx);

        // conferma: nel secondo giro si ripassa vicino allo stesso apice?
        int secondPassage = passages.get(1);
        boolean confirmed = false;
        for (int i = firstPassage; i <= secondPassage; i++) {
            if (haversineMeters(fix.get(i).lat, fix.get(i).lon, apex.lat, apex.lon) <= CIRCUIT_NEAR_M * 1.6) {
                confirmed = true;
                break;
            }
        }
        if (!confirmed && passages.size() < 3) return null;

        return new CircuitInfo(fix, passages);
    }

    private static final class CircuitInfo {
        final List<TelemetrySample> fix;
        final List<Integer> passages;

        CircuitInfo(List<TelemetrySample> fix, List<Integer> passages) {
            this.fix = fix;
            this.passages = passages;
        }
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // ---------------------------------------------------------------- statistiche comuni

    private static final class Stats {
        int n;
        double maxSpeed;
        int maxSpeedIdx = -1;
        double distanceTot;
        int movingCount, stoppedCount;
        double movingSum;
        int[] band = new int[4];
        double maxLatG, maxBrakeG, maxAccelG;
        int peakBrakeIdx = -1, peakAccelIdx = -1, peakLatIdx = -1;
        int strongLateral;
        int decisiveBrakes, mildDecels, mildAccels;
        List<Double> curveEvents = new ArrayList<>();
        double altMin = Double.NaN, altMax = Double.NaN;
        int stopCount;
    }

    private static Stats computeStats(List<TelemetrySample> samples) {
        Stats st = new Stats();
        int n = samples.size();
        st.n = n;
        boolean wasBraking = false, wasMildDecel = false, wasMildAccel = false;
        boolean inCurve = false;
        double curvePeak = 0;
        int stopRunStart = -1;

        for (int i = 0; i < n; i++) {
            TelemetrySample s = samples.get(i);
            if (s.speedKmh > st.maxSpeed) {
                st.maxSpeed = s.speedKmh;
                st.maxSpeedIdx = i;
            }
            st.distanceTot = Math.max(st.distanceTot, s.distanceKm);

            boolean stopped = s.speedKmh <= STOPPED_KMH;
            if (stopped) {
                st.stoppedCount++;
                if (stopRunStart < 0) stopRunStart = i;
            } else {
                st.movingCount++;
                st.movingSum += s.speedKmh;
                if (stopRunStart >= 0) {
                    if (i - stopRunStart >= MIN_STOP_SAMPLES) st.stopCount++;
                    stopRunStart = -1;
                }
                if (s.speedKmh <= 20) st.band[0]++;
                else if (s.speedKmh <= 40) st.band[1]++;
                else if (s.speedKmh <= 60) st.band[2]++;
                else st.band[3]++;
            }

            if (Math.abs(s.latG) > st.maxLatG) {
                st.maxLatG = Math.abs(s.latG);
                st.peakLatIdx = i;
            }
            if (Math.abs(s.latG) > STRONG_LATERAL_THRESHOLD) st.strongLateral++;

            boolean isCurve = Math.abs(s.latG) > CURVE_EVENT_THRESHOLD;
            if (isCurve) {
                inCurve = true;
                curvePeak = Math.max(curvePeak, Math.abs(s.latG));
            } else if (inCurve) {
                st.curveEvents.add(curvePeak);
                inCurve = false;
                curvePeak = 0;
            }

            double brakeG = Math.max(0, -s.lonG);
            if (brakeG > st.maxBrakeG) {
                st.maxBrakeG = brakeG;
                st.peakBrakeIdx = i;
            }
            if (s.lonG > st.maxAccelG) {
                st.maxAccelG = s.lonG;
                st.peakAccelIdx = i;
            }

            boolean isBraking = s.lonG < -SensorHub.BRAKE_THRESHOLD;
            if (isBraking && !wasBraking) st.decisiveBrakes++;
            wasBraking = isBraking;

            boolean isMildDecel = s.lonG < -MILD_DECEL_THRESHOLD;
            if (isMildDecel && !wasMildDecel) st.mildDecels++;
            wasMildDecel = isMildDecel;

            boolean isMildAccel = s.lonG > MILD_ACCEL_THRESHOLD;
            if (isMildAccel && !wasMildAccel) st.mildAccels++;
            wasMildAccel = isMildAccel;

            if (!Double.isNaN(s.altitudeM)) {
                st.altMin = Double.isNaN(st.altMin) ? s.altitudeM : Math.min(st.altMin, s.altitudeM);
                st.altMax = Double.isNaN(st.altMax) ? s.altitudeM : Math.max(st.altMax, s.altitudeM);
            }
        }
        if (stopRunStart >= 0 && n - stopRunStart >= MIN_STOP_SAMPLES) st.stopCount++;
        if (inCurve) st.curveEvents.add(curvePeak);
        return st;
    }

    // ---------------------------------------------------------------- resoconto su strada aperta

    private static String buildNormalReport(List<TelemetrySample> samples, long sessionStartMs) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.ITALY);
        Stats st = computeStats(samples);
        long durationSec = sessionDurationSec(samples, sessionStartMs);
        double avgSpeedMoving = st.movingCount > 0 ? st.movingSum / st.movingCount : 0;
        double stoppedPct = 100.0 * st.stoppedCount / st.n;

        StringBuilder sb = new StringBuilder();

        // ---- carattere del percorso (urbano / misto / extraurbano)
        int urbanScore = 0;
        if (stoppedPct >= 25) urbanScore += 2;
        else if (stoppedPct >= 15) urbanScore += 1;
        if (st.maxSpeed < 60) urbanScore += 2;
        else if (st.maxSpeed < 90) urbanScore += 1;
        else urbanScore -= 1;
        int dominantBand = 0;
        if (st.movingCount > 0) {
            for (int i = 1; i < 4; i++) if (st.band[i] > st.band[dominantBand]) dominantBand = i;
        }
        if (dominantBand <= 1) urbanScore += 1;
        else if (dominantBand == 3) urbanScore -= 1;

        String character;
        if (urbanScore >= 3) {
            character = "Carattere spiccatamente urbano: incroci, semafori e traffico hanno scandito il "
                    + "ritmo più della strada libera davanti.";
        } else if (urbanScore >= 1) {
            character = "Percorso piuttosto misto, tra tratti di città e qualche strada più scorrevole.";
        } else if (urbanScore <= -2) {
            character = "Percorso scorrevole e prevalentemente extraurbano, con pochi rallentamenti a "
                    + "spezzare il ritmo.";
        } else {
            character = "Percorso misto, con tratti più lenti alternati ad altri più scorrevoli.";
        }

        sb.append("Il percorso. ").append(character).append(" In ").append(formatDuration(durationSec))
                .append(" hai percorso ").append(dec(st.distanceTot)).append(" km, toccando una velocità "
                        + "massima di ").append(dec1(st.maxSpeed)).append(" km/h");
        if (st.maxSpeedIdx >= 0) sb.append(markerPhrase(samples, st.maxSpeedIdx));
        sb.append(", con un'andatura media di ").append(dec1(avgSpeedMoving))
                .append(" km/h quando l'auto era in movimento.\n\n");

        String stopText;
        if (stoppedPct < 10) {
            stopText = "Le soste sono state praticamente assenti: la marcia è rimasta quasi sempre continua.";
        } else if (st.stopCount > 0) {
            stopText = "Ci sono state " + st.stopCount + (st.stopCount == 1 ? " sosta" : " soste")
                    + " di una certa durata lungo il tragitto, oltre a qualche rallentamento più breve.";
        } else {
            stopText = "Solo brevi rallentamenti, nessuna vera sosta prolungata.";
        }
        sb.append(stopText).append("\n\n");

        // ---- ritmo di guida
        if (st.movingCount > 0) {
            String[] labels = {
                    "lento, sotto i 20 km/h", "tra 20 e 40 km/h", "tra 40 e 60 km/h", "più veloce, sopra i 60 km/h"
            };
            Integer[] order = {0, 1, 2, 3};
            java.util.Arrays.sort(order, (a, b) -> st.band[b] - st.band[a]);
            int dom = order[0], sec = order[1];
            sb.append("Il ritmo di guida. Per la maggior parte del tempo il passo è stato ").append(labels[dom]);
            if (st.band[sec] >= st.band[dom] * 0.5 && st.band[sec] > 0) {
                sb.append(", alternato a tratti ").append(labels[sec]);
            }
            sb.append(". ").append(st.band[dom] >= st.movingCount * 0.6
                    ? "Un'andatura piuttosto omogenea, senza grandi sbalzi di velocità."
                    : "Un'andatura piuttosto variabile, con cambi di passo frequenti lungo il percorso.");
            sb.append("\n\n");
        }

        // ---- frenate
        sb.append("Le frenate. ").append(brakeStyleText(st.decisiveBrakes, st.mildDecels)).append(".");
        if (st.peakBrakeIdx >= 0 && st.maxBrakeG > 0.15) {
            TelemetrySample s = samples.get(st.peakBrakeIdx);
            sb.append(" Il momento più marcato è arrivato alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakBrakeIdx)).append(".");
        }
        sb.append("\n\n");

        // ---- accelerazioni
        sb.append("Le accelerazioni. In generale ").append(accelStyleText(st.maxAccelG)).append(".");
        if (st.mildAccels > 0) {
            sb.append(" In tutto si contano circa ").append(st.mildAccels)
                    .append(" ripartenze un po' più decise nel corso della sessione, ")
                    .append(consistencyTextCount(st.mildAccels, durationSec)).append(".");
        }
        if (st.peakAccelIdx >= 0 && st.maxAccelG > 0.15) {
            TelemetrySample s = samples.get(st.peakAccelIdx);
            sb.append(" La più energica è stata alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakAccelIdx)).append(".");
        }
        sb.append("\n\n");

        // ---- curve
        String curveDesc = curveStyleText(st.maxLatG, false);
        sb.append("Le curve. ").append(Character.toUpperCase(curveDesc.charAt(0))).append(curveDesc.substring(1)).append(".");
        int nCurves = st.curveEvents.size();
        if (nCurves > 0) {
            sb.append(" In tutto hai affrontato circa ").append(nCurves).append(" curve con un carico laterale percepibile");
            String cons = consistencyTextValues(st.curveEvents);
            if (!cons.isEmpty()) sb.append(", ").append(cons);
            sb.append(".");
        }
        if (st.peakLatIdx >= 0) {
            TelemetrySample s = samples.get(st.peakLatIdx);
            sb.append(" Il punto con più carico laterale è arrivato alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakLatIdx)).append(", comunque un valore lontano dal limite dell'auto.\n\n");
        } else {
            sb.append("\n\n");
        }

        // ---- dislivello (solo se rilevante)
        boolean hasAlt = !Double.isNaN(st.altMin) && !Double.isNaN(st.altMax);
        if (hasAlt && (st.altMax - st.altMin) > 15) {
            sb.append("Da segnalare anche un percorso non del tutto pianeggiante, con circa ")
                    .append(Math.round(st.altMax - st.altMin))
                    .append(" metri di dislivello tra il punto più basso e quello più alto.\n\n");
        }

        // ---- stile di guida (sintesi): fuori da un circuito il default è descrivere la
        // guida come tranquilla, serve un comportamento chiaramente sopra la norma per
        // spostare il giudizio verso uno stile "più deciso".
        int sporty = (st.decisiveBrakes >= 3 ? 2 : (st.decisiveBrakes >= 1 ? 1 : 0))
                + (st.maxAccelG >= 0.45 ? 1 : 0) + (st.maxLatG >= 0.6 ? 1 : 0);
        String sintesi;
        if (sporty >= 3) {
            sintesi = "Nel complesso una guida più decisa e dinamica del solito: qualche frenata energica, "
                    + "ripartenze vivaci e curve prese con più ritmo. Resta comunque una guida su strada, "
                    + "lontana dai carichi che si vedono in pista.";
        } else if (sporty >= 1) {
            sintesi = "Nel complesso una guida tranquilla con qualche momento più vivace qua e là, ma senza "
                    + "mai esagerare: il margine rispetto ai limiti dell'auto è rimasto ampio per quasi tutta "
                    + "la sessione.";
        } else {
            sintesi = "Nel complesso una guida tranquilla e regolare: frenate morbide, accelerazioni "
                    + "progressive e ampio margine in curva per tutta la sessione.";
        }
        sb.append("Stile di guida. ").append(sintesi);

        return sb.toString();
    }

    // ---------------------------------------------------------------- resoconto circuito

    private static String buildCircuitReport(List<TelemetrySample> samples, long sessionStartMs, CircuitInfo circuit) {
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.ITALY);
        Stats st = computeStats(samples);
        long durationSec = sessionDurationSec(samples, sessionStartMs);

        List<Integer> lapBounds = new ArrayList<>();
        lapBounds.add(0);
        lapBounds.addAll(circuit.passages);
        List<Double> lapDurations = new ArrayList<>();
        for (int i = 0; i < lapBounds.size() - 1; i++) {
            long t0 = circuit.fix.get(lapBounds.get(i)).timestampMs;
            long t1 = circuit.fix.get(lapBounds.get(i + 1)).timestampMs;
            lapDurations.add((t1 - t0) / 1000.0);
        }
        int lapCount = lapDurations.size();
        double best = Double.MAX_VALUE, worst = -Double.MAX_VALUE, sum = 0;
        for (double d : lapDurations) {
            best = Math.min(best, d);
            worst = Math.max(worst, d);
            sum += d;
        }
        double avg = sum / lapCount;
        double variance = 0;
        for (double d : lapDurations) variance += (d - avg) * (d - avg);
        variance /= lapCount;
        double cv = avg > 0 ? Math.sqrt(variance) / avg : 0;

        String regolarita;
        if (cv < 0.12) regolarita = "con tempi giro molto costanti, uno dopo l'altro";
        else if (cv < 0.3) regolarita = "con tempi giro abbastanza regolari";
        else regolarita = "con tempi giro piuttosto variabili da un passaggio all'altro";

        String trend = "";
        if (lapCount >= 3) {
            int half = lapCount / 2;
            double fa = average(lapDurations.subList(0, half));
            double sa = average(lapDurations.subList(half, lapCount));
            if (sa < fa * 0.92) {
                trend = " Il ritmo è migliorato col passare dei giri, con i tempi finali più veloci di quelli "
                        + "iniziali — buon segno di feeling crescente con l'auto.";
            } else if (sa > fa * 1.08) {
                trend = " Il ritmo è leggermente calato nei giri finali, forse per gestione delle gomme o per "
                        + "un po' di stanchezza.";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Il tracciato. Il percorso ripassa più volte dagli stessi punti della mappa, in sequenza: "
                + "i dati indicano giri ripetuti sullo stesso tracciato, non un semplice spostamento. In "
                + "totale ").append(dec(st.distanceTot)).append(" km percorsi in ").append(formatDuration(durationSec))
                .append(", per una velocità massima di ").append(dec1(st.maxSpeed)).append(" km/h");
        if (st.maxSpeedIdx >= 0) sb.append(markerPhrase(samples, st.maxSpeedIdx));
        sb.append(".\n\n");

        sb.append("I giri. Su ").append(lapCount).append(" giri completi, il più veloce è stato di ")
                .append(fmtLap(best)).append(", il più lento di ").append(fmtLap(worst)).append(", ")
                .append(regolarita).append(".").append(trend).append("\n\n");

        sb.append("Le frenate. ").append(brakeStyleText(st.decisiveBrakes, st.mildDecels)).append(".");
        if (st.peakBrakeIdx >= 0 && st.maxBrakeG > 0.15) {
            TelemetrySample s = samples.get(st.peakBrakeIdx);
            sb.append(" La staccata più decisa è arrivata alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakBrakeIdx))
                    .append(" — vale la pena rivedere quel punto per capire se c'è margine per frenare più tardi.");
        }
        sb.append("\n\n");

        sb.append("Le accelerazioni. In uscita dalle curve ").append(accelStyleText(st.maxAccelG)).append(".");
        if (st.peakAccelIdx >= 0 && st.maxAccelG > 0.15) {
            TelemetrySample s = samples.get(st.peakAccelIdx);
            sb.append(" La ripartenza più energica è stata alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakAccelIdx)).append(".");
        }
        sb.append("\n\n");

        sb.append("Le curve. Il tuo stile in curva mostra ").append(curveStyleText(st.maxLatG, true));
        if (st.peakLatIdx >= 0) {
            TelemetrySample s = samples.get(st.peakLatIdx);
            sb.append(". Il punto di carico laterale più alto è arrivato alle ").append(timeFmt.format(new Date(s.timestampMs)))
                    .append(", a ").append(dec1(s.speedKmh)).append(" km/h")
                    .append(markerPhrase(samples, st.peakLatIdx));
        }
        sb.append(st.maxLatG < 0.5
                ? ", probabilmente il punto del tracciato dove c'è più margine per guadagnare in velocità di percorrenza.\n\n"
                : ".\n\n");

        int sporty = st.decisiveBrakes + (st.maxAccelG >= 0.35 ? 1 : 0) + (st.maxLatG >= 0.5 ? 1 : 0);
        String sintesi = sporty >= 2
                ? "Nel complesso una guida sportiva e concentrata sulla prestazione, con frenate decise, "
                        + "ripartenze vivaci e un buon carico in curva — l'obiettivo era chiaramente spingere."
                : "Nel complesso un ritmo controllato più che al limite: margine ancora da sfruttare, "
                        + "soprattutto in frenata e in curva, per chi vuole scendere nei tempi.";
        sb.append("Stile di guida. ").append(sintesi).append("\n\n");

        // ---- consigli per migliorare (solo in modalità circuito)
        List<String> tips = new ArrayList<>();
        if (st.maxBrakeG < 0.5) {
            String brakeWhere = st.peakBrakeIdx >= 0 ? markerPhrase(samples, st.peakBrakeIdx) : "";
            tips.add("Le frenate restano piuttosto morbide (fino a circa " + dec(st.maxBrakeG) + "g): "
                    + "un'auto come la MX-5 su gomme sportive può reggere frenate via via più forti e più "
                    + "tardive. Prova a spostare il punto di frenata un po' più avanti nella frenata più "
                    + "impegnativa" + brakeWhere + ", di qualche metro alla volta.");
        }
        if (st.maxLatG < 0.6) {
            String latWhere = st.peakLatIdx >= 0 ? markerPhrase(samples, st.peakLatIdx) : "";
            tips.add("Il carico laterale massimo registrato (" + dec(st.maxLatG) + "g) è ancora lontano dal "
                    + "limite di aderenza dell'auto: nella curva con più carico" + latWhere + " c'è probabilmente "
                    + "margine per entrare con qualche km/h in più, mantenendo comunque una traiettoria pulita.");
        }
        if (cv >= 0.25) {
            tips.add("I tempi giro sono piuttosto variabili: prima ancora di cercare il giro veloce, punta "
                    + "a ripetere lo stesso ritmo giro dopo giro — di solito è quello il modo più veloce per "
                    + "poi trovare margine reale, invece di un singolo giro isolato più rapido.");
        } else if (lapCount >= 3) {
            tips.add("Il ritmo è già molto ripetibile: con una base così solida puoi permetterti di provare "
                    + "a spingere un po' di più su un giro alla volta, per capire dove si trova il vero limite "
                    + "prima di consolidare il nuovo ritmo.");
        }
        if (!tips.isEmpty()) {
            sb.append("Consigli per migliorare. ").append(String.join(" ", tips));
        }

        return sb.toString().trim();
    }

    private static double average(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return values.isEmpty() ? 0 : sum / values.size();
    }

    // ---------------------------------------------------------------- frasi descrittive condivise

    private static String brakeStyleText(int decisiveBrakes, int mildDecels) {
        if (decisiveBrakes == 0 && mildDecels <= 3) {
            return "Hai frenato sempre con leggerezza e largo anticipo: nessun rallentamento brusco in "
                    + "tutta la sessione, segno di un'ottima lettura della strada";
        } else if (decisiveBrakes == 0) {
            return "Non ci sono state vere frenate brusche, ma si contano circa " + mildDecels
                    + " rallentamenti più marcati distribuiti nel percorso: uno stile che anticipa gli "
                    + "ostacoli dosando il freno piuttosto che tirarlo all'ultimo";
        } else if (decisiveBrakes <= 2) {
            return "La frenata è stata perlopiù morbida, con " + decisiveBrakes
                    + " episodi in cui il rallentamento è stato più energico del solito";
        } else {
            return "Si contano " + decisiveBrakes + " frenate piuttosto energiche: un ritmo sostenuto, o "
                    + "forse qualche imprevisto sulla strada che ha richiesto reazioni più decise";
        }
    }

    private static String accelStyleText(double maxAccelG) {
        if (maxAccelG < 0.2) {
            return "le ripartenze sono state sempre progressive e misurate, senza mai strappare";
        } else if (maxAccelG < 0.35) {
            return "le accelerazioni sono state morbide, con qualche ripartenza più vivace";
        } else {
            return "diverse ripartenze sono state piuttosto decise, con un uso più marcato dell'acceleratore";
        }
    }

    private static String curveStyleText(double maxLatG, boolean track) {
        if (track) {
            if (maxLatG < 0.5) return "un approccio ancora prudente, con parecchio margine rispetto al limite di aderenza";
            if (maxLatG < 0.75) return "un buon impegno, mantenendo comunque un margine di sicurezza";
            return "un approccio piuttosto sportivo, con carichi laterali importanti";
        }
        if (maxLatG < 0.35) return "una guida rilassata, sempre con ampio margine rispetto al limite di aderenza";
        if (maxLatG < 0.55) return "una guida tranquilla, con solo qualche curva presa con un filo più di ritmo, comunque lontano dal limite";
        return "una guida un po' più vivace del solito in un paio di curve, pur restando entro un margine di sicurezza";
    }

    /** Descrive quanto sono omogenei i picchi di carico laterale delle curve affrontate,
     *  calcolato dal coefficiente di variazione — solo dati, nessuna ipotesi sull'intenzione. */
    private static String consistencyTextValues(List<Double> values) {
        if (values.size() < 2) return "";
        double avg = average(values);
        if (avg == 0) return "";
        double variance = 0;
        for (double v : values) variance += (v - avg) * (v - avg);
        variance /= values.size();
        double cv = Math.sqrt(variance) / avg;
        if (cv < 0.25) return "gestite in modo piuttosto uniforme, senza differenze marcate da una all'altra";
        if (cv < 0.5) return "con un certo grado di variabilità da una curva all'altra";
        return "piuttosto diverse tra loro, alcune affrontate con più decisione di altre";
    }

    /** Descrive la spaziatura media tra eventi (qui le ripartenze più decise) nel tempo. */
    private static String consistencyTextCount(int count, long durationSec) {
        if (count <= 0) return "";
        double avgInterval = (double) durationSec / count;
        if (avgInterval > 90) return "distribuite con un buon distacco le une dalle altre";
        if (avgInterval > 30) return "distribuite in modo abbastanza regolare lungo il percorso";
        return "piuttosto ravvicinate tra loro";
    }

    /** Frase da accodare a un momento citato nel resoconto (es. il picco di frenata), con la
     *  lettera/numero del punto della mappa più vicino — "" se in questa sessione non è stata
     *  generata nessuna mappa (nessun campione ha un marker assegnato, tipicamente perché la
     *  chiave Geoapify non è configurata). Cerca il campione con marker più vicino a idx per
     *  indice, espandendo il raggio di ricerca finché non ne trova uno o esaurisce la lista. */
    private static String markerPhrase(List<TelemetrySample> samples, int idx) {
        int n = samples.size();
        for (int radius = 0; radius < n; radius++) {
            int left = idx - radius, right = idx + radius;
            boolean leftOk = left >= 0 && left < n && samples.get(left).mapMarkerIndex > 0;
            boolean rightOk = right != left && right >= 0 && right < n && samples.get(right).mapMarkerIndex > 0;
            if (leftOk || rightOk) {
                int hitIdx = leftOk ? left : right;
                String label = RouteMapBuilder.markerLabel(samples.get(hitIdx).mapMarkerIndex);
                if (label.isEmpty()) return "";
                return radius == 0 ? (" (punto " + label + " sulla mappa)") : (" (vicino al punto " + label + " sulla mappa)");
            }
        }
        return "";
    }

    private static String dec(double v) {
        return String.format(Locale.ITALY, "%.2f", v);
    }

    private static String dec1(double v) {
        return String.format(Locale.ITALY, "%.1f", v);
    }

    private static String fmtLap(double totalSec) {
        long rounded = Math.round(totalSec);
        long mm = rounded / 60, ss = rounded % 60;
        return String.format(Locale.ROOT, "%d:%02d", mm, ss);
    }

    private static String formatDuration(long totalSec) {
        long hh = totalSec / 3600, mm = (totalSec % 3600) / 60, ss = totalSec % 60;
        return hh > 0
                ? String.format(Locale.ROOT, "%d ore %d min %d sec", hh, mm, ss)
                : (mm > 0
                        ? String.format(Locale.ROOT, "%d min %d sec", mm, ss)
                        : String.format(Locale.ROOT, "%d sec", ss));
    }
}
