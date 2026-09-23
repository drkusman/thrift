package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A member's statement is split into a Savings side and a Loan side, matching how they'd naturally
 * think of their account: a member's own contributions/interest/fees on one side, their loan
 * disbursements/repayments on the other. Only LOAN-category entries go to the loan side; SAVINGS,
 * INTEREST, FEES, and OTHER all fall under savings (see LedgerService/LoanService, which likewise
 * only ever post LOAN entries against a member's outstanding loan balance).
 */
@Service
public class TransactionExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS = {"Date", "Description", "Type", "DR/CR", "Amount", "Balance"};
    private static final float[] COL_WIDTHS = {68, 262, 90, 48, 100, 102};

    private final byte[] logoBytes;

    public TransactionExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    public byte[] toExcel(Member member, List<LedgerEntry> entries) throws IOException {
        List<LedgerEntry> savings = sortedAscending(entries.stream().filter(e -> e.getTransCat() != TransCat.LOAN).toList());
        List<LedgerEntry> loans = sortedAscending(entries.stream().filter(e -> e.getTransCat() == TransCat.LOAN).toList());

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);

            writeSheet(wb, pictureIdx, member, "Savings", savings);
            writeSheet(wb, pictureIdx, member, "Loan", loans);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(XSSFWorkbook wb, int pictureIdx, Member member, String sheetName, List<LedgerEntry> entries) {
        Sheet sheet = wb.createSheet(sheetName);

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

        CellStyle subStyle = wb.createCellStyle();
        Font subFont = wb.createFont();
        subFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        subStyle.setFont(subFont);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(2);
        titleCell.setCellValue("ASUU-MOAUM Thrift - " + sheetName + " Statement");
        titleCell.setCellStyle(titleStyle);

        Row subRow = sheet.createRow(1);
        Cell subCell = subRow.createCell(2);
        subCell.setCellValue(member.getFullName() + " (" + member.getRegno() + ")");
        subCell.setCellStyle(subStyle);

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
        String[] cols = {"Date", "Description", "Type", "DR/CR", "Amount", "Balance"};
        for (int i = 0; i < cols.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }

        long balance = 0;
        int r = headerRowIdx + 1;
        for (LedgerEntry e : entries) {
            balance += e.getDrCrStatus() == DrCr.CR ? e.getAmount() : -e.getAmount();

            Row row = sheet.createRow(r++);
            setCell(row, 0, e.getDate().format(DATE_FMT), cellStyle);
            setCell(row, 1, e.getDescription() == null ? "" : e.getDescription(), cellStyle);
            setCell(row, 2, e.getTransType() == null ? "" : e.getTransType(), cellStyle);
            setCell(row, 3, e.getDrCrStatus().name(), cellStyle);
            Cell amountCell = row.createCell(4);
            amountCell.setCellValue(e.getAmount());
            amountCell.setCellStyle(numberStyle);
            Cell balanceCell = row.createCell(5);
            balanceCell.setCellValue(balance);
            balanceCell.setCellStyle(numberStyle);
        }
        if (entries.isEmpty()) {
            Row row = sheet.createRow(r);
            Cell c = row.createCell(0);
            c.setCellValue("No transactions.");
            c.setCellStyle(cellStyle);
        }
        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private static void setCell(Row row, int idx, String value, CellStyle style) {
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

    public byte[] toPdf(Member member, List<LedgerEntry> entries) throws IOException {
        List<LedgerEntry> savings = sortedAscending(entries.stream().filter(e -> e.getTransCat() != TransCat.LOAN).toList());
        List<LedgerEntry> loans = sortedAscending(entries.stream().filter(e -> e.getTransCat() == TransCat.LOAN).toList());

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo);
            cursor.sectionHeading("Savings Statement", member);
            cursor.table(savings);
            cursor.sectionHeading("Loan Statement", member);
            cursor.table(loans);
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Walks pages top-to-bottom, drawing an actual ruled grid (not monospace-aligned text) so the
     *  statement looks like a real bank/thrift printout, with the crest on every page header. */
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
        private boolean anySection = false;
        private Member currentMember;

        PdfCursor(PDDocument doc, PDImageXObject logo) throws IOException {
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
            drawPageHeader();
        }

        private void drawPageHeader() throws IOException {
            cs.drawImage(logo, MARGIN, y - LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
            cs.setFont(bold, 14);
            text(MARGIN + LOGO_SIZE + 12, y - 14, "ASUU-MOAUM Thrift");
            cs.setFont(regular, 9);
            text(MARGIN + LOGO_SIZE + 12, y - 28, "Rev. Fr. Moses Orshio Adasu University, Makurdi");
            if (currentMember != null) {
                cs.setFont(regular, 9);
                String memberLine = currentMember.getFullName() + " (" + currentMember.getRegno() + ")";
                float width = regular.getStringWidth(memberLine) / 1000 * 9;
                text(MARGIN + tableWidth() - width, y - 14, memberLine);
            }
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void sectionHeading(String title, Member member) throws IOException {
            this.currentMember = member;
            if (!anySection) {
                newPage();
                anySection = true;
            } else if (y > MARGIN + 80) {
                y -= 14;
            } else {
                newPage();
            }
            cs.setFont(bold, 12);
            text(MARGIN, y, title);
            y -= 10;
        }

        void table(List<LedgerEntry> entries) throws IOException {
            if (entries.isEmpty()) {
                y -= ROW_HEIGHT;
                cs.setFont(regular, 9);
                text(MARGIN, y, "No transactions.");
                y -= 6;
                return;
            }

            long balance = 0;
            boolean headerNeeded = true;
            for (LedgerEntry e : entries) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    if (!headerNeeded) newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }

                balance += e.getDrCrStatus() == DrCr.CR ? e.getAmount() : -e.getAmount();
                String desc = e.getDescription() == null ? "" : e.getDescription();
                if (desc.length() > 40) desc = desc.substring(0, 37) + "...";
                String[] values = {
                        e.getDate().format(DATE_FMT), desc, safe(e.getTransType(), 14),
                        e.getDrCrStatus().name(), formatAmount(e.getAmount()), formatAmount(balance),
                };
                drawTableRow(values, false);
            }
            y -= 8;
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
                float textX = numeric ? x + colWidth - textWidth - 6 : x + 6;
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
            return String.format(java.util.Locale.UK, "%,d", amount);
        }

        private static String safe(String s, int maxLen) {
            if (s == null) return "";
            return s.length() > maxLen ? s.substring(0, maxLen) : s;
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
            if (!anySection) newPage();
            cs.close();
        }
    }

    private static List<LedgerEntry> sortedAscending(List<LedgerEntry> entries) {
        List<LedgerEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getId));
        return sorted;
    }
}
