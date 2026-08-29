package com.mx5.driversmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Parametri email configurabili dall'utente in EmailSettingsActivity: destinatari,
 * mittente e parametri SMTP per l'invio automatico del report a fine registrazione.
 * Salvati in SharedPreferences cifrate (contengono la password SMTP); se la cifratura
 * non fosse disponibile sul dispositivo si passa a SharedPreferences normali, per non
 * bloccare l'app.
 */
final class AppSettings {

    private static final String TAG = "AppSettings";
    private static final String PREFS_NAME = "mx5_email_settings";

    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SECURITY = "smtp_security"; // STARTTLS | SSL | NESSUNA
    private static final String KEY_USERNAME = "smtp_username";
    private static final String KEY_PASSWORD = "smtp_password";
    private static final String KEY_FROM = "from_email";
    private static final String KEY_TO = "to_emails"; // separati da virgola
    private static final String KEY_AUTO_SEND = "auto_send_enabled";
    private static final String KEY_GEOAPIFY_KEY = "geoapify_api_key";

    private final SharedPreferences prefs;

    AppSettings(Context context) {
        prefs = openPrefs(context.getApplicationContext());
    }

    private static SharedPreferences openPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context, PREFS_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "SharedPreferences cifrate non disponibili, uso preferenze normali: " + e.getMessage());
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    String getSmtpHost() {
        return prefs.getString(KEY_SMTP_HOST, "");
    }

    int getSmtpPort() {
        return prefs.getInt(KEY_SMTP_PORT, 587);
    }

    /** STARTTLS, SSL oppure NESSUNA. */
    String getSecurity() {
        return prefs.getString(KEY_SECURITY, "STARTTLS");
    }

    String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    String getFromEmail() {
        return prefs.getString(KEY_FROM, "");
    }

    String getToEmailsRaw() {
        return prefs.getString(KEY_TO, "");
    }

    List<String> getToEmails() {
        List<String> out = new ArrayList<>();
        for (String part : getToEmailsRaw().split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    boolean isAutoSendEnabled() {
        return prefs.getBoolean(KEY_AUTO_SEND, true);
    }

    /** Chiave API di Geoapify (maps.geoapify.com), usata per generare l'immagine
     *  del percorso. Se vuota, il report parte comunque ma senza mappa allegata. */
    String getGeoapifyApiKey() {
        return prefs.getString(KEY_GEOAPIFY_KEY, "");
    }

    boolean isConfigured() {
        return !getSmtpHost().isEmpty() && !getFromEmail().isEmpty() && !getToEmails().isEmpty();
    }

    void save(String smtpHost, int smtpPort, String security, String username, String password,
              String fromEmail, String toEmailsRaw, boolean autoSend, String geoapifyApiKey) {
        prefs.edit()
                .putString(KEY_SMTP_HOST, smtpHost)
                .putInt(KEY_SMTP_PORT, smtpPort)
                .putString(KEY_SECURITY, security)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_FROM, fromEmail)
                .putString(KEY_TO, toEmailsRaw)
                .putBoolean(KEY_AUTO_SEND, autoSend)
                .putString(KEY_GEOAPIFY_KEY, geoapifyApiKey)
                .apply();
    }
}
