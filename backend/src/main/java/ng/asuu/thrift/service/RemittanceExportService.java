package ng.asuu.thrift.service;

import ng.asuu.thrift.web.dto.RemittanceRowDto;
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
import java.time.Month;
import java.util.List;
import java.util.Locale;

/**
 * The generated remittance schedule as a ruled-grid PDF or Excel file - the document an admin hands
 * (or emails) to the university's payroll office for a given pay point and period.
 */
@Service
public class RemittanceExportService {
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS = {"S/N", "Regno", "Name", "Monthly Savings", "Loan Repayment", "Total"};
    private static final float[] COL_WIDTHS = {35, 80, 220, 130, 130, 130};

    private final byte[] logoBytes;

    public RemittanceExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    private static String periodLabel(String payPoint, int year, int month) {
        String monthName = Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.UK);
        return payPoint + " - " + monthName + " " + year;
    }

    public byte[] toExcel(String payPoint, int year, int month, List<RemittanceRowDto> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet("Remittance Schedule");

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
            titleCell.setCellValue("ASUU-MOAUM Thrift - Remittance Schedule");
            titleCell.setCellStyle(titleStyle);

            Row subRow = sheet.createRow(1);
            Cell subCell = subRow.createCell(2);
            subCell.setCellValue(periodLabel(payPoint, year, month));

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
            CellStyle totalLabelStyle = wb.createCellStyle();
            Font boldFont2 = wb.createFont();
            boldFont2.setBold(true);
            totalLabelStyle.setFont(boldFont2);
            applyThinBorder(totalLabelStyle);
            CellStyle totalNumberStyle = wb.createCellStyle();
            totalNumberStyle.setFont(boldFont2);
            totalNumberStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            applyThinBorder(totalNumberStyle);

            int headerRowIdx = 5;
            Row header = sheet.createRow(headerRowIdx);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = headerRowIdx + 1;
            int sn = 1;
            long totalSavings = 0, totalRepayment = 0, totalOverall = 0;
            for (RemittanceRowDto row : rows) {
                Row xr = sheet.createRow(r++);
                Cell snCell = xr.createCell(0);
                snCell.setCellValue(sn++);
                snCell.setCellStyle(cellStyle);
                setCell(xr, 1, row.regno(), cellStyle);
                setCell(xr, 2, row.fullName(), cellStyle);
                setNumberCell(xr, 3, row.monthlySavings(), numberStyle);
                setNumberCell(xr, 4, row.loanRepayment(), numberStyle);
                setNumberCell(xr, 5, row.total(), numberStyle);
                totalSavings += row.monthlySavings();
                totalRepayment += row.loanRepayment();
                totalOverall += row.total();
            }
            if (rows.isEmpty()) {
                Row row = sheet.createRow(r++);
                Cell c = row.createCell(0);
                c.setCellValue("No active members at this pay point.");
                c.setCellStyle(cellStyle);
            } else {
                Row totalRow = sheet.createRow(r);
                setCell(totalRow, 2, "TOTAL", totalLabelStyle);
                Cell c0 = totalRow.createCell(0); c0.setCellStyle(totalLabelStyle);
                Cell c1 = totalRow.createCell(1); c1.setCellStyle(totalLabelStyle);
                setNumberCell(totalRow, 3, totalSavings, totalNumberStyle);
                setNumberCell(totalRow, 4, totalRepayment, totalNumberStyle);
                setNumberCell(totalRow, 5, totalOverall, totalNumberStyle);
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

    public byte[] toPdf(String payPoint, int year, int month, List<RemittanceRowDto> rows) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo, periodLabel(payPoint, year, month));
            cursor.table(rows);
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Walks pages top-to-bottom, drawing an actual ruled grid (not monospace-aligned text) so the
     *  schedule looks like a real printout, with the crest and period on every page header. */
    private static class PdfCursor {
        private final PDDocument doc;
        private final PDImageXObject logo;
        private final String periodLabel;
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final float MARGIN = 40;
        private static final float ROW_HEIGHT = 18;
        private static final float LOGO_SIZE = 40;
        private static final java.awt.Color HEADER_FILL = new java.awt.Color(107, 31, 46);
        private PDPageContentStream cs;
        private float y;
        private boolean anyPage = false;

        PdfCursor(PDDocument doc, PDImageXObject logo, String periodLabel) {
            this.doc = doc;
            this.logo = logo;
            this.periodLabel = periodLabel;
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
            text(MARGIN + LOGO_SIZE + 12, y - 14, "ASUU-MOAUM Thrift - Remittance Schedule");
            cs.setFont(regular, 9);
            text(MARGIN + LOGO_SIZE + 12, y - 28, periodLabel);
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void table(List<RemittanceRowDto> rows) throws IOException {
            if (rows.isEmpty()) {
                newPage();
                cs.setFont(regular, 9);
                text(MARGIN, y, "No active members at this pay point.");
                return;
            }

            boolean headerNeeded = true;
            int sn = 1;
            long totalSavings = 0, totalRepayment = 0, totalOverall = 0;
            for (RemittanceRowDto row : rows) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }
                String[] values = {
                        String.valueOf(sn++), row.regno(), safe(row.fullName(), 34),
                        formatAmount(row.monthlySavings()), formatAmount(row.loanRepayment()), formatAmount(row.total()),
                };
                drawTableRow(values, false, false);
                totalSavings += row.monthlySavings();
                totalRepayment += row.loanRepayment();
                totalOverall += row.total();
            }

            if (y - ROW_HEIGHT < MARGIN) newPage();
            String[] totalValues = {"", "", "TOTAL", formatAmount(totalSavings), formatAmount(totalRepayment), formatAmount(totalOverall)};
            drawTableRow(totalValues, false, true);
        }

        private void drawTableHeaderRow() throws IOException {
            drawTableRow(COLUMNS, true, false);
        }

        private void drawTableRow(String[] values, boolean header, boolean totals) throws IOException {
            float rowTop = y;
            float rowBottom = y - ROW_HEIGHT;
            float x = MARGIN;

            if (header) {
                cs.setNonStrokingColor(HEADER_FILL);
                cs.addRect(MARGIN, rowBottom, tableWidth(), ROW_HEIGHT);
                cs.fill();
                cs.setNonStrokingColor(java.awt.Color.BLACK);
            }

            cs.setFont(header || totals ? bold : regular, 8.5f);
            for (int i = 0; i < COLUMNS.length; i++) {
                boolean numeric = i >= 3;
                String v = values[i];
                float colWidth = COL_WIDTHS[i];
                float textWidth = (header || totals ? bold : regular).getStringWidth(v) / 1000 * 8.5f;
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
