package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.LoanType;
import ng.asuu.thrift.domain.Member;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A plain loan list for a single status (Running, Pulsed, Completed, ...) as a ruled-grid PDF or Excel
 * file - admin's general-purpose "show me every X loan" report, distinct from LoanExportService's
 * guarantor-focused pending-applications report.
 */
@Service
public class LoanListExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS =
            {"S/N", "Regno", "Name", "Loan Type", "Amount", "Monthly Repayment", "Balance", "Applied"};
    private static final float[] COL_WIDTHS = {30, 65, 165, 85, 80, 100, 85, 70};

    private final byte[] logoBytes;
    private final LoanService loanService;

    public LoanListExportService(LoanService loanService) throws IOException {
        this.loanService = loanService;
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    private record Row(String regno, String name, String loanType, long amount, Long monthly, Long balance, String applied) {
        static Row of(Loan l, Map<Long, Member> membersById, Map<Long, LoanType> loanTypesById, long balance) {
            Member m = membersById.get(l.getMemberId());
            LoanType t = loanTypesById.get(l.getLoanTypeId());
            return new Row(
                    m != null ? m.getRegno() : "-",
                    m != null ? m.getFullName() : "Member #" + l.getMemberId(),
                    t != null ? t.getName() : "-",
                    l.getRequestedAmount(), l.getMonthlyRepaymentAmount(), balance,
                    l.getAppliedAt() == null ? "-" : l.getAppliedAt().toLocalDate().format(DATE_FMT));
        }
    }

    private List<Row> toRows(List<Loan> loans, Map<Long, Member> membersById, Map<Long, LoanType> loanTypesById) {
        return loans.stream().map(l -> Row.of(l, membersById, loanTypesById, loanService.balanceFor(l.getId()))).toList();
    }

    public byte[] toExcel(List<Loan> loans, Map<Long, Member> membersById, Map<Long, LoanType> loanTypesById, String statusLabel) throws IOException {
        List<Row> rows = toRows(loans, membersById, loanTypesById);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet(statusLabel + " Loans");

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

            setTitleCell(sheet, "ASUU-MOAUM Thrift - " + statusLabel + " Loans", titleStyle);

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
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(headerRowIdx);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(COLUMNS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = headerRowIdx + 1;
            int sn = 1;
            for (Row row : rows) {
                org.apache.poi.ss.usermodel.Row xr = sheet.createRow(r++);
                Cell snCell = xr.createCell(0);
                snCell.setCellValue(sn++);
                snCell.setCellStyle(cellStyle);
                setCell(xr, 1, row.regno(), cellStyle);
                setCell(xr, 2, row.name(), cellStyle);
                setCell(xr, 3, row.loanType(), cellStyle);
                setNumberCell(xr, 4, row.amount(), numberStyle);
                setNumberCell(xr, 5, row.monthly() == null ? 0 : row.monthly(), numberStyle);
                setNumberCell(xr, 6, row.balance() == null ? 0 : row.balance(), numberStyle);
                setCell(xr, 7, row.applied(), cellStyle);
            }
            if (rows.isEmpty()) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(r);
                Cell c = row.createCell(0);
                c.setCellValue("No " + statusLabel.toLowerCase(Locale.ROOT) + " loans.");
                c.setCellStyle(cellStyle);
            }
            for (int i = 0; i < COLUMNS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void setTitleCell(Sheet sheet, String value, CellStyle style) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(0);
        Cell c = row.createCell(2);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void setCell(org.apache.poi.ss.usermodel.Row row, int idx, String value, CellStyle style) {
        Cell c = row.createCell(idx);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void setNumberCell(org.apache.poi.ss.usermodel.Row row, int idx, long value, CellStyle style) {
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

    public byte[] toPdf(List<Loan> loans, Map<Long, Member> membersById, Map<Long, LoanType> loanTypesById, String statusLabel) throws IOException {
        List<Row> rows = toRows(loans, membersById, loanTypesById);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo, statusLabel);
            cursor.table(rows);
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Walks pages top-to-bottom, drawing an actual ruled grid (not monospace-aligned text) so the
     *  list looks like a real printout, with the crest and status label on every page header. */
    private static class PdfCursor {
        private final PDDocument doc;
        private final PDImageXObject logo;
        private final String statusLabel;
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final float MARGIN = 40;
        private static final float ROW_HEIGHT = 18;
        private static final float LOGO_SIZE = 40;
        private static final java.awt.Color HEADER_FILL = new java.awt.Color(107, 31, 46);
        private PDPageContentStream cs;
        private float y;
        private boolean anyPage = false;

        PdfCursor(PDDocument doc, PDImageXObject logo, String statusLabel) {
            this.doc = doc;
            this.logo = logo;
            this.statusLabel = statusLabel;
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
            text(MARGIN + LOGO_SIZE + 12, y - 14, "ASUU-MOAUM Thrift - " + statusLabel + " Loans");
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void table(List<Row> rows) throws IOException {
            if (rows.isEmpty()) {
                newPage();
                cs.setFont(regular, 9);
                text(MARGIN, y, "No " + statusLabel.toLowerCase(Locale.ROOT) + " loans.");
                return;
            }

            boolean headerNeeded = true;
            int sn = 1;
            for (Row row : rows) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }
                String[] values = {
                        String.valueOf(sn++), row.regno(), safe(row.name(), 32), safe(row.loanType(), 18),
                        formatAmount(row.amount()), formatAmount(row.monthly() == null ? 0 : row.monthly()),
                        formatAmount(row.balance() == null ? 0 : row.balance()), row.applied(),
                };
                drawTableRow(values, false);
            }
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
                boolean numeric = i >= 4 && i <= 6;
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
