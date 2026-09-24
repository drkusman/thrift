package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.GuaranteeStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loan applications still awaiting an admin decision (see AdminLoanController, which only ever passes
 * this service the pending list), grouped by loan type - one section per type in the PDF, one sheet
 * per type in the Excel - with both guarantors' names and acceptance status next to each application,
 * so an admin can see at a glance which are still waiting on a guarantor before they can be approved.
 */
@Service
public class LoanExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS = {"S/N", "Member", "Amount", "Applied", "Guarantor 1", "Guarantor 2", "Remark"};
    private static final float[] COL_WIDTHS = {30, 150, 70, 65, 165, 165, 110};

    private final byte[] logoBytes;

    public LoanExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    public byte[] toExcel(List<Loan> loans, List<LoanType> loanTypes, Map<Long, Member> membersById) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            for (LoanType type : loanTypes) {
                List<Loan> ofType = sortedAscending(loans.stream().filter(l -> type.getId().equals(l.getLoanTypeId())).toList());
                writeSheet(wb, pictureIdx, type.getName(), ofType, membersById);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeSheet(XSSFWorkbook wb, int pictureIdx, String sheetName, List<Loan> loans, Map<Long, Member> membersById) {
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

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(2);
        titleCell.setCellValue("ASUU-MOAUM Thrift - Pending " + sheetName + " Applications");
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
        for (Loan l : loans) {
            Row row = sheet.createRow(r++);
            Cell snCell = row.createCell(0);
            snCell.setCellValue(sn++);
            snCell.setCellStyle(cellStyle);
            setCell(row, 1, memberCell(l, membersById), cellStyle);
            Cell amountCell = row.createCell(2);
            amountCell.setCellValue(l.getRequestedAmount());
            amountCell.setCellStyle(numberStyle);
            setCell(row, 3, l.getAppliedAt() == null ? "" : l.getAppliedAt().toLocalDate().format(DATE_FMT), cellStyle);
            setCell(row, 4, guarantorCell(l, true, membersById), cellStyle);
            setCell(row, 5, guarantorCell(l, false, membersById), cellStyle);
            setCell(row, 6, "", cellStyle);
        }
        if (loans.isEmpty()) {
            Row row = sheet.createRow(r);
            Cell c = row.createCell(0);
            c.setCellValue("No applications.");
            c.setCellStyle(cellStyle);
        }
        for (int i = 0; i < COLUMNS.length; i++) sheet.autoSizeColumn(i);
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

    public byte[] toPdf(List<Loan> loans, List<LoanType> loanTypes, Map<Long, Member> membersById) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo);
            for (LoanType type : loanTypes) {
                List<Loan> ofType = sortedAscending(loans.stream().filter(l -> type.getId().equals(l.getLoanTypeId())).toList());
                cursor.sectionHeading(type.getName() + " Applications");
                cursor.table(ofType, membersById);
            }
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String memberCell(Loan l, Map<Long, Member> membersById) {
        Member m = membersById.get(l.getMemberId());
        return m == null ? ("#" + l.getMemberId()) : m.getFullName() + " (" + m.getRegno() + ")";
    }

    private static String guarantorCell(Loan l, boolean first, Map<Long, Member> membersById) {
        Long guarantorId = first ? l.getGuarantorOneId() : l.getGuarantorTwoId();
        if (guarantorId == null) return "-";
        Member g = membersById.get(guarantorId);
        GuaranteeStatus status = first ? l.getGuarantorOneStatus() : l.getGuarantorTwoStatus();
        String name = g == null ? ("#" + guarantorId) : g.getFullName() + " (" + g.getRegno() + ")";
        return name + " - " + status.name();
    }

    /** Walks pages top-to-bottom, drawing an actual ruled grid (not monospace-aligned text) so the
     *  report looks like a real printout, with the crest on every page header. */
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
            drawPageHeader();
        }

        private void drawPageHeader() throws IOException {
            cs.drawImage(logo, MARGIN, y - LOGO_SIZE, LOGO_SIZE, LOGO_SIZE);
            cs.setFont(bold, 14);
            text(MARGIN + LOGO_SIZE + 12, y - 14, "ASUU-MOAUM Thrift");
            cs.setFont(regular, 9);
            text(MARGIN + LOGO_SIZE + 12, y - 28, "Pending Loan Applications Report");
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void sectionHeading(String title) throws IOException {
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

        void table(List<Loan> loans, Map<Long, Member> membersById) throws IOException {
            if (loans.isEmpty()) {
                y -= ROW_HEIGHT;
                cs.setFont(regular, 9);
                text(MARGIN, y, "No applications.");
                y -= 6;
                return;
            }

            boolean headerNeeded = true;
            int sn = 1;
            for (Loan l : loans) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    if (!headerNeeded) newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }

                String[] values = {
                        String.valueOf(sn++),
                        safe(memberCell(l, membersById), 26),
                        formatAmount(l.getRequestedAmount()),
                        l.getAppliedAt() == null ? "" : l.getAppliedAt().toLocalDate().format(DATE_FMT),
                        safe(guarantorCell(l, true, membersById), 32),
                        safe(guarantorCell(l, false, membersById), 32),
                        "",
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

            cs.setFont(header ? bold : regular, 8);
            for (int i = 0; i < COLUMNS.length; i++) {
                boolean numeric = i == 2;
                String v = values[i];
                float colWidth = COL_WIDTHS[i];
                float textWidth = (header ? bold : regular).getStringWidth(v) / 1000 * 8;
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
            return String.format(java.util.Locale.UK, "%,d", amount);
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
            if (!anySection) newPage();
            cs.close();
        }
    }

    private static List<Loan> sortedAscending(List<Loan> loans) {
        List<Loan> sorted = new ArrayList<>(loans);
        sorted.sort(Comparator.comparing(Loan::getAppliedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Loan::getId));
        return sorted;
    }
}
