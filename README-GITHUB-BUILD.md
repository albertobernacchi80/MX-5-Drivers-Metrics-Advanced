# Creare l'APK gratis senza Android Studio

## Metodo consigliato: GitHub Actions

1. Crea un account gratuito su GitHub.
2. Crea un nuovo repository. Se vuoi evitare il consumo dei minuti Actions del piano gratuito privato, rendilo **Public**.
3. Carica tutto il contenuto di questa cartella nel repository (non lo ZIP dentro un'altra cartella).
4. Vai in **Actions** → **Build APK** → **Run workflow**.
5. Quando il workflow termina con una spunta verde, apri la relativa esecuzione.
6. In fondo trovi **Artifacts** → `MX5-AndroidAuto-debug`.
7. Scarica l'archivio dell'artifact e dentro troverai `app-debug.apk`.

Non serve installare Android Studio sul PC.

## Nota
L'APK debug è adatto a test personali. Per pubblicazione sul Play Store serve una build firmata/release e la verifica dei requisiti Android Auto/Google Play.
