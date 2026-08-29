package com.mx5.driversmetrics;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera il report della registrazione in formato .xlsx (Excel), da allegare
 * all'email accanto al CSV: stessa tabella dati del CSV (un rigo di riepilogo
 * in testa, poi un campione al secondo), più un grafico a linee "pronto"
 * incorporato nel foglio con l'andamento di tutti i valori numerici della
 * sessione (velocità, G laterale, G longitudinale, altitudine, rotta,
 * distanza progressiva), così chi apre il file lo vede subito senza dover
 * creare il grafico a mano.
 *
 * Il file .xlsx è semplicemente uno zip di parti XML (formato OOXML): non
 * usa nessuna libreria esterna (Apache POI non è affidabile su Android: usa
 * classi java.awt/javax.imageio non disponibili sul runtime del telefono),
 * le parti vengono scritte a mano con java.util.zip, già incluso in Android.
 * Il formato è stato validato aprendo file generati con lo stesso identico
 * template in Excel/LibreOffice prima di essere portato qui.
 */
final class XlsxReportBuilder {

    private XlsxReportBuilder() {
    }

    private static final String[] COLUMNS = {
            "Indice", "Ora", "Latitudine", "Longitudine", "Velocita_kmh", "G_laterale",
            "G_longitudinale", "Altitudine_m", "Rotta_gradi", "Precisione_gps_m",
            "Distanza_progressiva_km", "Marker_mappa"
    };
    // Lettere di colonna corrispondenti a COLUMNS (A..L), usate per riferimenti e grafico.
    private static final String COL_VELOCITA = "E";
    private static final String COL_G_LAT = "F";
    private static final String COL_G_LON = "G";
    private static final String COL_ALTITUDINE = "H";
    private static final String COL_ROTTA = "I";
    private static final String COL_DISTANZA = "K";

    private static final int TITLE_ROW = 1;
    private static final int SUMMARY_FIRST_ROW = 2;
    private static final int SUMMARY_ROWS = 8;
    private static final int HEADER_ROW = SUMMARY_FIRST_ROW + SUMMARY_ROWS + 1; // riga vuota dopo il riepilogo
    private static final int DATA_START_ROW = HEADER_ROW + 1;

    static File build(Context context, List<TelemetrySample> samples, long sessionStartMs) throws IOException {
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.ITALY);
        String fileName = "Metriche guida MX-5-" + dateFmt.format(new Date(sessionStartMs)) + ".xlsx";
        File out = new File(context.getCacheDir(), fileName);

        double maxSpeed = 0, maxLatG = 0, maxBrakeG = 0, distanceTot = 0;
        for (TelemetrySample s : samples) {
            maxSpeed = Math.max(maxSpeed, s.speedKmh);
            maxLatG = Math.max(maxLatG, Math.abs(s.latG));
            maxBrakeG = Math.max(maxBrakeG, Math.max(0, -s.lonG));
            distanceTot = Math.max(distanceTot, s.distanceKm);
        }
        long endMs = samples.isEmpty() ? sessionStartMs : samples.get(samples.size() - 1).timestampMs;
        long durationSec = Math.max(0, (endMs - sessionStartMs) / 1000);
        int dataEndRow = DATA_START_ROW + Math.max(0, samples.size() - 1);

        SimpleDateFormat headerFmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY);
        SimpleDateFormat rowFmt = new SimpleDateFormat("HH:mm:ss", Locale.ITALY);

        String sheetXml = buildSheetXml(samples, sessionStartMs, endMs, durationSec,
                distanceTot, maxSpeed, maxLatG, maxBrakeG, headerFmt, rowFmt);
        String chartXml = buildChartXml(dataEndRow);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {
            writeEntry(zos, "[Content_Types].xml", CONTENT_TYPES);
            writeEntry(zos, "_rels/.rels", ROOT_RELS);
            writeEntry(zos, "xl/workbook.xml", WORKBOOK_XML);
            writeEntry(zos, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            writeEntry(zos, "xl/worksheets/sheet1.xml", sheetXml);
            writeEntry(zos, "xl/worksheets/_rels/sheet1.xml.rels", SHEET_RELS);
            writeEntry(zos, "xl/drawings/drawing1.xml", DRAWING_XML);
            writeEntry(zos, "xl/drawings/_rels/drawing1.xml.rels", DRAWING_RELS);
            writeEntry(zos, "xl/charts/chart1.xml", chartXml);
        }
        return out;
    }

    private static void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String buildSheetXml(List<TelemetrySample> samples, long sessionStartMs, long endMs,
                                         long durationSec, double distanceTot, double maxSpeed,
                                         double maxLatG, double maxBrakeG,
                                         SimpleDateFormat headerFmt, SimpleDateFormat rowFmt) {
        StringBuilder rows = new StringBuilder();

        rows.append(row(TITLE_ROW, strCell("A" + TITLE_ROW, "MX-5 Driver Metrics Advanced - Report registrazione")));

        String[][] summary = {
                {"Inizio", headerFmt.format(new Date(sessionStartMs))},
                {"Fine", headerFmt.format(new Date(endMs))},
                {"Durata", formatDuration(durationSec)},
                {"Campioni", String.valueOf(samples.size())},
                {"Distanza percorsa km", numText(distanceTot)},
                {"Velocita massima km/h", numText(maxSpeed)},
                {"G laterale massimo", numText(maxLatG)},
                {"G frenata massimo", numText(maxBrakeG)},
        };
        for (int i = 0; i < summary.length; i++) {
            int r = SUMMARY_FIRST_ROW + i;
            rows.append(row(r, strCell("A" + r, summary[i][0]) + strCell("B" + r, summary[i][1])));
        }

        StringBuilder header = new StringBuilder();
        String[] colLetters = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"};
        for (int c = 0; c < COLUMNS.length; c++) {
            header.append(strCell(colLetters[c] + HEADER_ROW, COLUMNS[c]));
        }
        rows.append(row(HEADER_ROW, header.toString()));

        int idx = 1;
        for (TelemetrySample s : samples) {
            int r = DATA_START_ROW + idx - 1;
            StringBuilder cells = new StringBuilder();
            cells.append(numCell(colLetters[0] + r, idx));
            cells.append(strCell(colLetters[1] + r, rowFmt.format(new Date(s.timestampMs))));
            cells.append(s.hasFix() ? numCell(colLetters[2] + r, s.lat) : "");
            cells.append(s.hasFix() ? numCell(colLetters[3] + r, s.lon) : "");
            cells.append(numCell(colLetters[4] + r, s.speedKmh));
            cells.append(numCell(colLetters[5] + r, s.latG));
            cells.append(numCell(colLetters[6] + r, s.lonG));
            cells.append(Double.isNaN(s.altitudeM) ? "" : numCell(colLetters[7] + r, s.altitudeM));
            cells.append(Double.isNaN(s.headingDeg) ? "" : numCell(colLetters[8] + r, s.headingDeg));
            cells.append(Double.isNaN(s.gpsAccuracyM) ? "" : numCell(colLetters[9] + r, s.gpsAccuracyM));
            cells.append(numCell(colLetters[10] + r, s.distanceKm));
            cells.append(s.mapMarkerIndex > 0 ? strCell(colLetters[11] + r, RouteMapBuilder.markerLabel(s.mapMarkerIndex)) : "");
            rows.append(row(r, cells.toString()));
            idx++;
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>" + rows + "</sheetData>"
                + "<drawing r:id=\"rId1\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>"
                + "</worksheet>";
    }

    private static String buildChartXml(int dataEndRow) {
        String[][] series = {
                {COL_VELOCITA, "Velocita_kmh", "FF4D4D"},
                {COL_G_LAT, "G_laterale", "1F77B4"},
                {COL_G_LON, "G_longitudinale", "2CA02C"},
                {COL_ALTITUDINE, "Altitudine_m", "9467BD"},
                {COL_ROTTA, "Rotta_gradi", "FF7F0E"},
                {COL_DISTANZA, "Distanza_progressiva_km", "17BECF"},
        };
        String catRef = "'Dati'!$A$" + DATA_START_ROW + ":$A$" + dataEndRow;
        StringBuilder ser = new StringBuilder();
        for (int i = 0; i < series.length; i++) {
            String col = series[i][0], name = series[i][1], color = series[i][2];
            String headerRef = "'Dati'!$" + col + "$" + HEADER_ROW;
            String valRef = "'Dati'!$" + col + "$" + DATA_START_ROW + ":$" + col + "$" + dataEndRow;
            ser.append("<c:ser>")
               .append("<c:idx val=\"").append(i).append("\"/>")
               .append("<c:order val=\"").append(i).append("\"/>")
               .append("<c:tx><c:strRef><c:f>").append(headerRef).append("</c:f>")
               .append("<c:strCache><c:ptCount val=\"1\"/><c:pt idx=\"0\"><c:v>").append(name).append("</c:v></c:pt></c:strCache>")
               .append("</c:strRef></c:tx>")
               .append("<c:spPr><a:ln w=\"19050\"><a:solidFill><a:srgbClr val=\"").append(color).append("\"/></a:solidFill>")
               .append("<a:prstDash val=\"solid\"/></a:ln></c:spPr>")
               .append("<c:marker><c:symbol val=\"none\"/></c:marker>")
               .append("<c:cat><c:numRef><c:f>").append(catRef).append("</c:f></c:numRef></c:cat>")
               .append("<c:val><c:numRef><c:f>").append(valRef).append("</c:f></c:numRef></c:val>")
               .append("<c:smooth val=\"0\"/>")
               .append("</c:ser>");
        }

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                + "<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" "
                + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<c:chart>"
                + "<c:title><c:tx><c:rich><a:bodyPr/><a:p><a:r><a:t>Andamento valori registrazione</a:t></a:r></a:p></c:rich></c:tx>"
                + "<c:overlay val=\"0\"/></c:title>"
                + "<c:autoTitleDeleted val=\"0\"/>"
                + "<c:plotArea><c:layout/>"
                + "<c:lineChart><c:grouping val=\"standard\"/><c:varyColors val=\"0\"/>"
                + ser
                + "<c:marker val=\"1\"/><c:axId val=\"111111111\"/><c:axId val=\"222222222\"/></c:lineChart>"
                + "<c:catAx><c:axId val=\"111111111\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>"
                + "<c:delete val=\"0\"/><c:axPos val=\"b\"/><c:majorTickMark val=\"out\"/><c:minorTickMark val=\"none\"/>"
                + "<c:crossAx val=\"222222222\"/></c:catAx>"
                + "<c:valAx><c:axId val=\"222222222\"/><c:scaling><c:orientation val=\"minMax\"/></c:scaling>"
                + "<c:delete val=\"0\"/><c:axPos val=\"l\"/><c:majorGridlines/><c:majorTickMark val=\"out\"/>"
                + "<c:minorTickMark val=\"none\"/><c:crossAx val=\"111111111\"/></c:valAx>"
                + "</c:plotArea>"
                + "<c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend>"
                + "<c:plotVisOnly val=\"1\"/>"
                + "</c:chart></c:chartSpace>";
    }

    private static String row(int r, String cellsXml) {
        return "<row r=\"" + r + "\">" + cellsXml + "</row>";
    }

    private static String strCell(String ref, String value) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escape(value) + "</t></is></c>";
    }

    private static String numCell(String ref, double value) {
        return "<c r=\"" + ref + "\"><v>" + numText(value) + "</v></c>";
    }

    /** Numero in formato "macchina" (punto come separatore decimale, mai virgola):
     *  a differenza del CSV, dentro l'XML di un .xlsx il separatore decimale è
     *  sempre il punto — è poi Excel a mostrarlo secondo le impostazioni locali. */
    private static String numText(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        String s = String.format(Locale.ROOT, "%.6f", v);
        // taglia gli zeri decimali superflui, es. "132.000000" -> "132"
        if (s.contains(".")) {
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String formatDuration(long totalSec) {
        long hh = totalSec / 3600, mm = (totalSec % 3600) / 60, ss = totalSec % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hh, mm, ss);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String CONTENT_TYPES =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/drawings/drawing1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawing+xml\"/>"
            + "<Override PartName=\"/xl/charts/chart1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.drawingml.chart+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Dati\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
            + "</workbook>";

    private static final String WORKBOOK_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "</Relationships>";

    private static final String SHEET_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing\" Target=\"../drawings/drawing1.xml\"/>"
            + "</Relationships>";

    private static final String DRAWING_RELS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart\" Target=\"../charts/chart1.xml\"/>"
            + "</Relationships>";

    // Grafico ancorato a destra della tabella dati (colonna N in poi), così non
    // si sovrappone mai alle celle indipendentemente da quanti campioni ci sono.
    private static final String DRAWING_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<xdr:wsDr xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" "
            + "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<xdr:oneCellAnchor>"
            + "<xdr:from><xdr:col>13</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>"
            + "<xdr:ext cx=\"9144000\" cy=\"5486400\"/>"
            + "<xdr:graphicFrame macro=\"\">"
            + "<xdr:nvGraphicFramePr><xdr:cNvPr id=\"2\" name=\"Chart 1\"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr>"
            + "<xdr:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/></xdr:xfrm>"
            + "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/chart\">"
            + "<c:chart xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" r:id=\"rId1\"/>"
            + "</a:graphicData></a:graphic>"
            + "</xdr:graphicFrame>"
            + "<xdr:clientData/>"
            + "</xdr:oneCellAnchor>"
            + "</xdr:wsDr>";
}
