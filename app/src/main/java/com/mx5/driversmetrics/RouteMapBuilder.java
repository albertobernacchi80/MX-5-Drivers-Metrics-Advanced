package com.mx5.driversmetrics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera l'immagine del percorso percorso durante la registrazione, per poterlo
 * correlare visivamente ai dati del CSV (ogni marker numerato sulla mappa
 * corrisponde alla colonna "Marker_mappa" della stessa riga nel CSV).
 *
 * Usa la Geoapify Static Maps API (mappe OpenStreetMap reali) via richiesta POST
 * in JSON, con la chiave configurata in Impostazioni email. Se la chiave non è
 * configurata, o il download fallisce per qualunque motivo, la mappa viene
 * semplicemente omessa dal report, che parte comunque solo con il CSV.
 */
final class RouteMapBuilder {

    private static final String TAG = "RouteMapBuilder";
    private static final String ENDPOINT = "https://maps.geoapify.com/v1/staticmap";
    private static final int MAX_PATH_POINTS = 300;
    private static final int MAX_MARKERS = 34; // etichette 1-9 poi A-Y
    // Non più "private": CsvReportBuilder, XlsxReportBuilder e DrivingReportBuilder la usano
    // tramite markerLabel() per scrivere la stessa identica etichetta che compare sulla mappa
    // (prima solo un numero progressivo 1-34, che dal decimo marker in poi non corrispondeva
    // più alla lettera mostrata sull'immagine).
    static final String MARKER_LABELS = "123456789ABCDEFGHIJKLMNOPQRSTUVWXY";

    private static final String COLOR_PATH = "#FF4D4D";
    private static final String COLOR_START = "#00C853";
    private static final String COLOR_END = "#FF4D4D";
    private static final String COLOR_MARKER = "#FF8A00";

    private RouteMapBuilder() {
    }

    /** Ritorna il file PNG della mappa, oppure null se non c'è una chiave API configurata,
     *  non ci sono punti GPS validi, o il download fallisce. Assegna anche mapMarkerIndex
     *  ai campioni marcati con un numero sulla mappa. */
    static File build(Context context, List<TelemetrySample> samples, String geoapifyApiKey) {
        if (geoapifyApiKey == null || geoapifyApiKey.trim().isEmpty()) return null;

        List<TelemetrySample> withFix = new ArrayList<>();
        for (TelemetrySample s : samples) {
            if (s.hasFix()) withFix.add(s);
        }
        if (withFix.size() < 2) return null;

        try {
            JSONObject body = new JSONObject();
            body.put("style", "osm-bright");
            body.put("width", 800);
            body.put("height", 600);

            JSONArray geometries = new JSONArray();
            JSONObject polyline = new JSONObject();
            polyline.put("type", "polyline");
            polyline.put("linecolor", COLOR_PATH);
            polyline.put("linewidth", 4);
            JSONArray pathValue = new JSONArray();
            for (TelemetrySample s : subsample(withFix, MAX_PATH_POINTS)) {
                pathValue.put(latLonObject(s.lat, s.lon));
            }
            polyline.put("value", pathValue);
            geometries.put(polyline);
            body.put("geometries", geometries);

            JSONArray markers = new JSONArray();
            markers.put(marker(withFix.get(0).lat, withFix.get(0).lon, COLOR_START, "S"));
            markers.put(marker(withFix.get(withFix.size() - 1).lat, withFix.get(withFix.size() - 1).lon, COLOR_END, "F"));

            List<TelemetrySample> markerPoints = subsample(withFix, MAX_MARKERS);
            int labelIdx = 0;
            for (TelemetrySample s : markerPoints) {
                if (labelIdx >= MARKER_LABELS.length()) break;
                String label = String.valueOf(MARKER_LABELS.charAt(labelIdx));
                s.mapMarkerIndex = labelIdx + 1;
                markers.put(marker(s.lat, s.lon, COLOR_MARKER, label));
                labelIdx++;
            }
            body.put("markers", markers);

            String url = ENDPOINT + "?apiKey=" + encode(geoapifyApiKey.trim());
            Bitmap bmp = download(url, body.toString());
            if (bmp == null) return null;

            SimpleDateFormat fmt = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.ITALY);
            File out = new File(context.getCacheDir(), "MX5_percorso_" + fmt.format(new java.util.Date()) + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            return out;
        } catch (Exception e) {
            Log.w(TAG, "Download mappa percorso fallito, il report partirà senza immagine: " + e.getMessage());
            return null;
        }
    }

    /** Etichetta mostrata sulla mappa per il mapMarkerIndex (1-based) assegnato a un campione
     *  in build(): "" se fuori range. Unica fonte di verità, così CSV, Excel e l'analisi
     *  descrittiva citano sempre lo stesso punto che si vede sull'immagine del percorso. */
    static String markerLabel(int mapMarkerIndex) {
        int idx = mapMarkerIndex - 1;
        if (idx < 0 || idx >= MARKER_LABELS.length()) return "";
        return String.valueOf(MARKER_LABELS.charAt(idx));
    }

    private static JSONObject latLonObject(double lat, double lon) throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("lat", lat);
        o.put("lon", lon);
        return o;
    }

    private static JSONObject marker(double lat, double lon, String color, String text) throws org.json.JSONException {
        JSONObject m = new JSONObject();
        m.put("lat", lat);
        m.put("lon", lon);
        m.put("color", color);
        m.put("type", "material");
        m.put("size", "medium");
        m.put("text", text);
        return m;
    }

    private static Bitmap download(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "MX5DriversMetrics-AndroidAuto/1.0");

        byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "Geoapify ha risposto con codice " + code);
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
                return BitmapFactory.decodeByteArray(buffer.toByteArray(), 0, buffer.size());
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Riduce la lista a al più maxPoints elementi, presi a intervalli regolari
     *  (mantiene sempre primo e ultimo). */
    private static List<TelemetrySample> subsample(List<TelemetrySample> in, int maxPoints) {
        if (in.size() <= maxPoints) return in;
        List<TelemetrySample> out = new ArrayList<>();
        double step = (in.size() - 1) / (double) (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            out.add(in.get((int) Math.round(i * step)));
        }
        return out;
    }
}
