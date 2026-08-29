# MX-5 Driver Metrics Advanced

App per Android Auto che mostra in tempo reale le metriche di guida della Mazda MX-5 ND 2019 (velocità, accelerazione/frenata, G laterali) e permette di registrare una sessione e riceverne il report via email, con analisi descrittiva dello stile di guida.

Applicazione indipendente sviluppata da Alberto Bernacchi, non affiliata, sponsorizzata o approvata da Mazda Motor Corporation.

## Funzionalità

- **Gauge in tempo reale**: velocità, accelerazione/frenata e G laterale disegnati a schermo intero su Home, Velocità e Accelerazione, con lampeggio in rosso quando viene superato un nuovo massimo di sessione (velocità: 50/120/150/180/200 km/h; frenata: 0,4/0,6/0,8/1,0 g; G laterale: 0,3/0,5/0,7/0,9 g).
- **Avviso posizionamento telefono**: prima di ogni sessione, l'app ricorda dove appoggiare il telefono (vaschetta a sinistra del cambio, fotocamera verso il cruscotto) perché G laterale e frenata/accelerazione siano attendibili.
- **Telemetria**: Avvia/Arresta registrazione campiona velocità, G laterali e longitudinali, posizione GPS, altitudine e rotta una volta al secondo, con attesa del fix GPS all'avvio per un percorso accurato fin dal primo dato.
- **Report automatico via email**: a fine registrazione vengono generati un CSV (formato italiano, pronto per Excel) e un file Excel con già pronto un grafico dell'andamento dei valori, inviati in automatico se configurato. Il corpo dell'email include un'analisi descrittiva in linguaggio naturale dello stile di guida, che si adatta al percorso: sotto 1 km spiega che i dati non bastano per un'analisi attendibile (CSV, Excel e mappa restano comunque allegati); se il percorso GPS ripassa più volte dallo stesso punto viene riconosciuto come circuito, con tempi giro, regolarità e consigli mirati per migliorare; negli altri casi il percorso viene classificato come urbano, misto o extraurbano in base ai dati (soste, velocità, andatura). Il report include anche un'immagine del percorso su mappa OpenStreetMap con marker correlati ai momenti salienti.
- **Analisi di sessione**: distanza, velocità media/massima, indice di fluidità, frenate rilevate, G medi e massimi, consultabili direttamente in auto.
- **Impostazioni email**: schermata separata sul telefono (fuori da Android Auto) per destinatari, mittente, parametri SMTP, invio automatico e chiave API Geoapify (gratuita) per la mappa del percorso.
- **Guida agli indicatori** e **Crediti** consultabili in auto con la rotella.

## Affidabilità

- Salvataggio di sicurezza dei dati grezzi su disco subito dopo la registrazione: l'invio del report è affidato a un servizio dedicato, con un nuovo tentativo automatico al successivo avvio dell'app se il primo invio non va a buon fine.
- Navigazione con la rotellina o con touch su tutte le schermate (selezione, conferma, ritorno indietro).

## Requisiti

- Telefono Android con Android Auto.
- Un'auto compatibile con Android Auto (proiezione via cavo o wireless), oppure Android Auto Desktop Head Unit (DHU) per il test.
- Non è disponibile su Google Play: l'installazione avviene manualmente (vedi sotto).

## Installazione

### Scaricare l'APK già pronto

Se è già disponibile una build, scarica `MX-5 Drivers Metrics.apk` dalla sezione **Releases** di questo repository, oppure dagli **Artifacts** dell'ultima esecuzione del workflow **Build APK** in **Actions**, e installalo manualmente sul telefono — vedi il manuale incluso nel repository per i dettagli. (Servono KingInstaller (https://github.com/fcaronte/KingInstaller/releases) con Android Auto in modalità sviluppatore (https://www.smartworld.it/guide/come-abilitare-opzioni-sviluppatore-android-auto.html).)

### Compilare l'APK da soli (senza Android Studio)

1. Crea un account gratuito su GitHub e un nuovo repository con il contenuto di questa cartella.
2. Vai su **Actions → Build APK → Run workflow**.
3. A esecuzione completata, apri quell'esecuzione e scarica l'artifact **MX5-AndroidAuto-debug**: contiene `MX-5 Drivers Metrics.apk`.

Per i dettagli passo passo su installazione, modalità sviluppatore su Android Auto e configurazione, vedi il manuale incluso nel repository (`Mx5DriversMetricsManual_completo.pdf`).

## Nota sui dati

I valori mostrati (velocità, accelerazione, frenata) derivano dai sensori del telefono e dal GPS: sono indicativi, non misurazioni certificate. Non fare affidamento su questi dati durante la guida in condizioni che richiedano piena attenzione — l'app è pensata come supporto informativo, non come strumento di sicurezza.

## Licenza

Applicazione per uso personale, non destinata alla distribuzione o vendita a terzi. © 2026 Alberto Bernacchi. Tutti i diritti riservati. “Mazda” e “MX-5” sono marchi registrati dei rispettivi proprietari, citati in questo repository e nell'app solo a scopo descrittivo.
