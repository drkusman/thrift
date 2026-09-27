package ng.asuu.thrift.service;

import ng.asuu.thrift.service.IncomeReportService.LoanInterestRow;
import ng.asuu.thrift.service.IncomeReportService.TransactionRow;
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

/** Downloadable Excel/PDF for the income report's drill-down lists (the exact loans or ledger postings
 *  behind a total) - same ruled-grid look as MemberBalanceExportService/LoanListExportService. */
@Service
public class IncomeReportExportService {
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();

    private static final String[] LOAN_COLUMNS = {"S/N", "Regno", "Name", "Loan Code", "Loan Type", "Disbursed", "Interest"};
    private static final float[] LOAN_COL_WIDTHS = {30, 60, 160, 130, 90, 70, 90};

    private static final String[] TXN_COLUMNS = {"S/N", "Regno", "Name", "Date", "Description", "Amount"};
    private static final float[] TXN_COL_WIDTHS = {30, 60, 170, 70, 200, 100};

    private final byte[] logoBytes;

    public IncomeReportExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    public byte[] loanInterestExcel(List<LoanInterestRow> rows, String title) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet("Interest");
            drawHeader(wb, sheet, pictureIdx, title, LOAN_COLUMNS);

            CellStyle cellStyle = wb.createCellStyle();
            applyThinBorder(cellStyle);
            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            applyThinBorder(numberStyle);

            int r = 6;
            int sn = 1;
            for (LoanInterestRow row : rows) {
                Row xr = sheet.createRow(r++);
                setNumberCell(xr, 0, sn++, cellStyle);
                setCell(xr, 1, row.regno(), cellStyle);
                setCell(xr, 2, row.fullName(), cellStyle);
                setCell(xr, 3, row.loanCode() == null ? "" : row.loanCode(), cellStyle);
                setCell(xr, 4, row.loanType(), cellStyle);
                setCell(xr, 5, row.disbursedAt(), cellStyle);
                setNumberCell(xr, 6, row.interestAmount(), numberStyle);
            }
            if (rows.isEmpty()) noRowsMessage(sheet, r, cellStyle);
            for (int i = 0; i < LOAN_COLUMNS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] loanInterestPdf(List<LoanInterestRow> rows, String title) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo, title, LOAN_COLUMNS, LOAN_COL_WIDTHS);
            if (rows.isEmpty()) {
                cursor.emptyMessage();
            } else {
                int sn = 1;
                for (LoanInterestRow row : rows) {
                    cursor.row(new String[]{
                            String.valueOf(sn++), row.regno(), safe(row.fullName(), 30),
                            safe(row.loanCode() == null ? "-" : row.loanCode(), 26), row.loanType(),
                            row.disbursedAt(), formatAmount(row.interestAmount()),
                    });
                }
            }
            cursor.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    public byte[] transactionsExcel(List<TransactionRow> rows, String title) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet("Transactions");
            drawHeader(wb, sheet, pictureIdx, title, TXN_COLUMNS);

            CellStyle cellStyle = wb.createCellStyle();
            applyThinBorder(cellStyle);
            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            applyThinBorder(numberStyle);

            int r = 6;
            int sn = 1;
            for (TransactionRow row : rows) {
                Row xr = sheet.createRow(r++);
                setNumberCell(xr, 0, sn++, cellStyle);
                setCell(xr, 1, row.regno(), cellStyle);
                setCell(xr, 2, row.fullName(), cellStyle);
                setCell(xr, 3, row.date(), cellStyle);
                setCell(xr, 4, row.description() == null ? "" : row.description(), cellStyle);
                setNumberCell(xr, 5, row.amount(), numberStyle);
            }
            if (rows.isEmpty()) noRowsMessage(sheet, r, cellStyle);
            for (int i = 0; i < TXN_COLUMNS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] transactionsPdf(List<TransactionRow> rows, String title) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo, title, TXN_COLUMNS, TXN_COL_WIDTHS);
            if (rows.isEmpty()) {
                cursor.emptyMessage();
            } else {
                int sn = 1;
                for (TransactionRow row : rows) {
                    cursor.row(new String[]{
                            String.valueOf(sn++), row.regno(), safe(row.fullName(), 26), row.date(),
                            safe(row.description() == null ? "" : row.description(), 34), formatAmount(row.amount()),
                    });
                }
            }
            cursor.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void drawHeader(XSSFWorkbook wb, Sheet sheet, int pictureIdx, String title, String[] columns) {
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
        titleCell.setCellValue("ASUU-MOAUM Thrift - " + title);
        titleCell.setCellStyle(titleStyle);

        CellStyle headerStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        boldFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(boldFont);
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0x6b, (byte) 0x1f, (byte) 0x2e}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorder(headerStyle);

        Row header = sheet.createRow(5);
        for (int i = 0; i < columns.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(columns[i]);
            c.setCellStyle(headerStyle);
        }
    }

    private static void noRowsMessage(Sheet sheet, int rowIdx, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell c = row.createCell(0);
        c.setCellValue("No transactions.");
        c.setCellStyle(style);
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

    private static String formatAmount(long amount) {
        return String.format(Locale.UK, "%,d", amount);
    }

    private static String safe(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }

    /** Walks pages top-to-bottom, drawing an actual ruled grid, with the crest and title on every page. */
    private static class PdfCursor {
        private final PDDocument doc;
        private final PDImageXObject logo;
        private final String title;
        private final String[] columns;
        private final float[] colWidths;
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final float MARGIN = 40;
        private static final float ROW_HEIGHT = 18;
        private static final float LOGO_SIZE = 40;
        private static final java.awt.Color HEADER_FILL = new java.awt.Color(107, 31, 46);
        private PDPageContentStream cs;
        private float y;
        private boolean anyPage = false;
        private boolean headerNeeded = true;
        private int numericFromCol;

        PdfCursor(PDDocument doc, PDImageXObject logo, String title, String[] columns, float[] colWidths) {
            this.doc = doc;
            this.logo = logo;
            this.title = title;
            this.columns = columns;
            this.colWidths = colWidths;
            this.numericFromCol = columns.length - 1;
        }

        private float tableWidth() {
            float w = 0;
            for (float cw : colWidths) w += cw;
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
            cs.setFont(bold, 13);
            text(MARGIN + LOGO_SIZE + 12, y - 14, "ASUU-MOAUM Thrift - " + title);
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void row(String[] values) throws IOException {
            if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                newPage();
                drawRow(columns, true);
                headerNeeded = false;
            }
            drawRow(values, false);
        }

        void emptyMessage() throws IOException {
            newPage();
            cs.setFont(regular, 9);
            text(MARGIN, y, "No transactions.");
        }

        private void drawRow(String[] values, boolean header) throws IOException {
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
            for (int i = 0; i < columns.length; i++) {
                boolean numeric = i >= numericFromCol;
                String v = values[i];
                float colWidth = colWidths[i];
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
            for (float colWidth : colWidths) {
                x += colWidth;
                line(x, rowTop, x, rowBottom);
            }
            y = rowBottom;
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
