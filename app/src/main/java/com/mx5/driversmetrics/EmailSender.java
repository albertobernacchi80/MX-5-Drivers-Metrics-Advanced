package com.mx5.driversmetrics;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 * Invio SMTP del report a fine registrazione: CSV, Excel e (se disponibile) l'immagine del
 * percorso in allegato, con l'analisi descrittiva (DrivingReportBuilder) scritta direttamente
 * nel corpo del messaggio, non come allegato separato. Va sempre chiamato da un thread in
 * background: apre una connessione di rete e non deve mai girare sul thread principale
 * dell'app auto.
 */
final class EmailSender {

    private EmailSender() {
    }

    static void send(AppSettings settings, File csv, File xlsx, File mapImage, String reportText, long sessionStartMs) throws Exception {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("Parametri email non configurati (vai in Impostazioni email sul telefono)");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", settings.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(settings.getSmtpPort()));
        props.put("mail.smtp.auth", String.valueOf(!settings.getUsername().isEmpty()));

        switch (settings.getSecurity()) {
            case "SSL":
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", String.valueOf(settings.getSmtpPort()));
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                break;
            case "NESSUNA":
                break;
            case "STARTTLS":
            default:
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                break;
        }

        Session session = Session.getInstance(props, new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(settings.getUsername(), settings.getPassword());
            }
        });

        MimeMessage message = new MimeMessage(session);
        // Il nome visualizzato del mittente è sempre "MX-5 Driver Metrics Advanced",
        // indipendentemente dall'indirizzo email configurato in Impostazioni.
        message.setFrom(new InternetAddress(settings.getFromEmail(), "MX-5 Driver Metrics Advanced", "UTF-8"));
        for (String to : settings.getToEmails()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
        }

        // Oggetto sempre uguale, per poter filtrare/cercare facilmente questi report in casella.
        message.setSubject("Report metriche guida MX-5");

        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY);

        Multipart multipart = new MimeMultipart();

        MimeBodyPart textPart = new MimeBodyPart();
        String body = "In allegato il report CSV (e lo stesso report in formato Excel, con già pronto "
                + "un grafico a linee dell'andamento dei valori) della registrazione telemetrica avviata il "
                + fmt.format(new Date(sessionStartMs)) + ".\n\n"
                + (mapImage != null
                        ? "È allegata anche un'immagine del percorso: i numeri e le lettere sui marker "
                          + "corrispondono alla colonna \"Marker_mappa\" del CSV/Excel, per risalire a dove è "
                          + "stato rilevato ogni dato.\n\n"
                        : "L'immagine del percorso non è stata inclusa (download della mappa non riuscito).\n\n")
                + (reportText != null && !reportText.isEmpty()
                        ? "------------------------------------------------------------\n"
                          + reportText + "\n"
                          + "------------------------------------------------------------\n\n"
                        : "")
                + "Email generata automaticamente da MX-5 Driver Metrics Advanced.";
        textPart.setText(body);
        multipart.addBodyPart(textPart);

        MimeBodyPart csvPart = new MimeBodyPart();
        csvPart.setDataHandler(new DataHandler(new FileDataSource(csv)));
        csvPart.setFileName(csv.getName());
        multipart.addBodyPart(csvPart);

        if (xlsx != null) {
            MimeBodyPart xlsxPart = new MimeBodyPart();
            xlsxPart.setDataHandler(new DataHandler(new FileDataSource(xlsx)));
            xlsxPart.setFileName(xlsx.getName());
            multipart.addBodyPart(xlsxPart);
        }

        if (mapImage != null) {
            MimeBodyPart mapPart = new MimeBodyPart();
            mapPart.setDataHandler(new DataHandler(new FileDataSource(mapImage)));
            mapPart.setFileName(mapImage.getName());
            multipart.addBodyPart(mapPart);
        }

        message.setContent(multipart);
        Transport.send(message);
    }
}
