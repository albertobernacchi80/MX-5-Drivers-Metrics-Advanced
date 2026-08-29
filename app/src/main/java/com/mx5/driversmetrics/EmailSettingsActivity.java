package com.mx5.driversmetrics;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Attività normale sul telefono (non è pensata per essere usata guidando):
 * qui si configurano gli indirizzi email e i parametri SMTP usati per l'invio
 * automatico del report a fine registrazione. Si apre dall'icona dell'app
 * sulla schermata Home del telefono, non da Android Auto.
 */
public final class EmailSettingsActivity extends AppCompatActivity {

    private static final String[] SECURITY_OPTIONS = {"STARTTLS", "SSL", "NESSUNA"};

    private EditText etTo, etFrom, etHost, etPort, etUsername, etPassword, etGeoapifyKey;
    private Spinner spinnerSecurity;
    private CheckBox checkAutoSend;
    private TextView textStatus;
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_settings);

        settings = new AppSettings(this);

        etTo = findViewById(R.id.et_to);
        etFrom = findViewById(R.id.et_from);
        etHost = findViewById(R.id.et_smtp_host);
        etPort = findViewById(R.id.et_smtp_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etGeoapifyKey = findViewById(R.id.et_geoapify_key);
        spinnerSecurity = findViewById(R.id.spinner_security);
        checkAutoSend = findViewById(R.id.checkbox_auto_send);
        textStatus = findViewById(R.id.text_status);
        Button btnSave = findViewById(R.id.btn_save);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SECURITY_OPTIONS);
        spinnerSecurity.setAdapter(adapter);

        loadCurrentSettings();

        btnSave.setOnClickListener(v -> save());

        requestNotificationPermissionIfNeeded();
    }

    /** Serve solo a mostrare la notifica "Invio del report in corso..." di
     *  ReportSendService (Android 13+ la richiede a runtime): se l'utente la nega il
     *  report viene comunque generato e inviato normalmente, semplicemente senza notifica
     *  visibile durante l'invio. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private void loadCurrentSettings() {
        etTo.setText(settings.getToEmailsRaw());
        etFrom.setText(settings.getFromEmail());
        etHost.setText(settings.getSmtpHost());
        etPort.setText(settings.getSmtpPort() > 0 ? String.valueOf(settings.getSmtpPort()) : "");
        etUsername.setText(settings.getUsername());
        etPassword.setText(settings.getPassword());
        etGeoapifyKey.setText(settings.getGeoapifyApiKey());
        checkAutoSend.setChecked(settings.isAutoSendEnabled());

        int idx = 0;
        for (int i = 0; i < SECURITY_OPTIONS.length; i++) {
            if (SECURITY_OPTIONS[i].equals(settings.getSecurity())) idx = i;
        }
        spinnerSecurity.setSelection(idx);
    }

    private void save() {
        String to = etTo.getText().toString().trim();
        String from = etFrom.getText().toString().trim();
        String host = etHost.getText().toString().trim();
        String portText = etPort.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String security = SECURITY_OPTIONS[spinnerSecurity.getSelectedItemPosition()];
        boolean autoSend = checkAutoSend.isChecked();
        String geoapifyKey = etGeoapifyKey.getText().toString().trim();

        if (host.isEmpty() || from.isEmpty() || to.isEmpty()) {
            textStatus.setText("Server SMTP, mittente e almeno un destinatario sono obbligatori.");
            return;
        }

        int port;
        try {
            port = portText.isEmpty() ? 587 : Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            textStatus.setText("La porta SMTP deve essere un numero (es. 587 o 465).");
            return;
        }

        settings.save(host, port, security, username, password, from, to, autoSend, geoapifyKey);
        textStatus.setText("Impostazioni salvate.");
        showSavedDialog();
    }

    /** Pop-up mostrato dopo il salvataggio: conferma l'avvenuto salvataggio e chiede
     *  se chiudere l'app oppure restare qui per fare altre modifiche. Non è annullabile
     *  toccando fuori (setCancelable(false)): l'utente deve scegliere una delle due
     *  opzioni esplicitamente. */
    private void showSavedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Impostazioni salvate")
                .setMessage("I parametri sono stati salvati correttamente. Vuoi chiudere l'app o continuare a fare modifiche?")
                .setCancelable(false)
                .setPositiveButton("Chiudi app", (dialog, which) -> closeAppForReal())
                .setNegativeButton("Continua a modificare", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /** finish() da solo chiude questa schermata ma non toglie l'app dalla lista delle app
     *  recenti: il task resta lì (anche se l'activity è già terminata), dando l'impressione
     *  che l'app sia ancora aperta in background. finishAndRemoveTask() chiude la schermata
     *  E toglie il task dalle app recenti, per un "Chiudi app" che chiude davvero. Non tocca
     *  ReportSendService: è un componente separato con vita propria, che se stesse ancora
     *  inviando un report in quel momento continua tranquillamente fino alla fine. */
    private void closeAppForReal() {
        finishAndRemoveTask();
    }
}
