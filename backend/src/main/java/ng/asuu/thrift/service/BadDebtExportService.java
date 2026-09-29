package ng.asuu.thrift.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** The admin Bad Debt list (see BadDebtService.outstanding()) as a ruled-grid PDF or Excel file - same
 *  look as MemberListExportService/LoanListExportService. */
@Service
public class BadDebtExportService {
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS = {"S/N", "Regno", "Name", "Owed Since", "Original Amount", "Currently Owed"};
    private static final float[] COL_WIDTHS = {30, 65, 190, 80, 100, 105};
    private static final String TITLE = "ASUU-MOAUM Thrift - Bad Debt List";

    private final byte[] logoBytes;

    public BadDebtExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    public byte[] toExcel(List<BadDebtService.Row> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet("Bad Debt");

            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = new XSSFClientAnchor();
            anchor.setCol1(0); anchor.setRow1(0);
            anchor.setCol2(1); anchor.setRow2(4);
            drawing.createPicture(anchor, pictureIdx).resize(0.5);

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(2);
            titleCell.setCellValue(TITLE);
            titleCell.setCellStyle(titleStyle);

            CellStyle headerStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            boldFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x6b, (byte) 0x1f, (byte) 0x2e}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyThinBorder(headerStyle);

            CellStyle cellStyle = wb.createCellStyle();
            applyThinBorder(cellStyle);
            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            applyThinBorder(numberStyle);

            int headerRowIdx = 5;
            Row header = sheet.createRow(headerRowIdx);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = headerRowIdx + 1;
            int sn = 1;
            long totalOwed = 0;
            for (BadDebtService.Row row : rows) {
                Row xr = sheet.createRow(r++);
                Cell snCell = xr.createCell(0);
                snCell.setCellValue(sn++);
                snCell.setCellStyle(cellStyle);
                setCell(xr, 1, row.regno(), cellStyle);
                setCell(xr, 2, row.fullName(), cellStyle);
                setCell(xr, 3, row.createdAt().length() >= 10 ? row.createdAt().substring(0, 10) : row.createdAt(), cellStyle);
                setNumberCell(xr, 4, row.originalAmount(), numberStyle);
                setNumberCell(xr, 5, row.currentlyOwed(), numberStyle);
                totalOwed += row.currentlyOwed();
            }
            if (rows.isEmpty()) {
                Row row = sheet.createRow(r);
                Cell c = row.createCell(0);
                c.setCellValue("No outstanding bad debt.");
                c.setCellStyle(cellStyle);
            } else {
                Row totalRow = sheet.createRow(r + 1);
                setCell(totalRow, 2, "Total currently owed", cellStyle);
                setNumberCell(totalRow, 5, totalOwed, numberStyle);
            }
            for (int i = 0; i < COLUMNS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void setCell(Row row, int idx, String value, CellStyle style) {
        Cell c = row.createCell(idx);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void setNumberCell(Row row, int idx, long value, CellStyle style) {
        Cell c = row.createCell(idx);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void applyThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    public byte[] toPdf(List<BadDebtService.Row> rows) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo);
            cursor.table(rows);
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    private static class PdfCursor {
        private final PDDocument doc;
        private final PDImageXObject logo;
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final float MARGIN = 40;
        private static final float ROW_HEIGHT = 18;
        private static final float LOGO_SIZE = 40;
        private static final java.awt.Color HEADER_FILL = new java.awt.Color(107, 31, 46);
        private PDPageContentStream cs;
        private float y;
        private boolean anyPage = false;

        PdfCursor(PDDocument doc, PDImageXObject logo) {
            this.doc = doc;
            this.logo = logo;
        }

        private float tableWidth() {
            float w = 0;
            for (float cw : COL_WIDTHS) w += cw;
            return w;
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PAGE_HEIGHT - MARGIN;
            anyPage = true;
            drawPageHeader();
        }

        private void drawPageHeader() throws IOException {
            cs.drawImage(logo, MARGIN, y - LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
            cs.setFont(bold, 14);
            text(MARGIN + LOGO_SIZE + 12, y - 14, TITLE);
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void table(List<BadDebtService.Row> rows) throws IOException {
            if (rows.isEmpty()) {
                newPage();
                cs.setFont(regular, 9);
                text(MARGIN, y, "No outstanding bad debt.");
                return;
            }

            boolean headerNeeded = true;
            int sn = 1;
            long totalOwed = 0;
            for (BadDebtService.Row row : rows) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }
                String createdAt = row.createdAt().length() >= 10 ? row.createdAt().substring(0, 10) : row.createdAt();
                String[] values = {
                        String.valueOf(sn++), row.regno(), safe(row.fullName(), 30),
                        createdAt, formatAmount(row.originalAmount()), formatAmount(row.currentlyOwed()),
                };
                drawTableRow(values, false);
                totalOwed += row.currentlyOwed();
            }

            if (y - ROW_HEIGHT < MARGIN) newPage();
            String[] totalValues = {"", "", "Total currently owed", "", "", formatAmount(totalOwed)};
            drawTableRow(totalValues, false);
        }

        private void drawTableHeaderRow() throws IOException {
            drawTableRow(COLUMNS, true);
        }

        private void drawTableRow(String[] values, boolean header) throws IOException {
            float rowTop = y;
            float rowBottom = y - ROW_HEIGHT;
            float x = MARGIN;

            if (header) {
                cs.setNonStrokingColor(HEADER_FILL);
                cs.addRect(MARGIN, rowBottom, tableWidth(), ROW_HEIGHT);
                cs.fill();
                cs.setNonStrokingColor(java.awt.Color.BLACK);
            }

            cs.setFont(header ? bold : regular, 8.5f);
            for (int i = 0; i < COLUMNS.length; i++) {
                boolean numeric = i >= 4;
                String v = values[i];
                float colWidth = COL_WIDTHS[i];
                float textWidth = (header ? bold : regular).getStringWidth(v) / 1000 * 8.5f;
                float textX = numeric ? x + colWidth - textWidth - 6 : x + 4;
                if (header) cs.setNonStrokingColor(java.awt.Color.WHITE);
                text(textX, rowBottom + 5, v);
                if (header) cs.setNonStrokingColor(java.awt.Color.BLACK);
                x += colWidth;
            }

            cs.setLineWidth(0.6f);
            rect(MARGIN, rowBottom, tableWidth(), ROW_HEIGHT);
            x = MARGIN;
            for (float colWidth : COL_WIDTHS) {
                x += colWidth;
                line(x, rowTop, x, rowBottom);
            }
            y = rowBottom;
        }

        private static String formatAmount(long amount) {
            return String.format(Locale.UK, "%,d", amount);
        }

        private static String safe(String s, int maxLen) {
            if (s == null) return "";
            return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
        }

        private void text(float x, float yPos, String s) throws IOException {
            cs.beginText();
            cs.newLineAtOffset(x, yPos);
            cs.showText(s);
            cs.endText();
        }

        private void line(float x1, float y1, float x2, float y2) throws IOException {
            cs.moveTo(x1, y1);
            cs.lineTo(x2, y2);
            cs.stroke();
        }

        private void rect(float x, float yBottom, float w, float h) throws IOException {
            cs.addRect(x, yBottom, w, h);
            cs.stroke();
        }

        void close() throws IOException {
            if (!anyPage) newPage();
            cs.close();
        }
    }
}
