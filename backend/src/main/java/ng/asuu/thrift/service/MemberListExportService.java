package ng.asuu.thrift.service;

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
import java.util.List;
import java.util.Locale;

/** The admin Members list (whatever status/search filter is currently applied) as a ruled-grid PDF
 *  or Excel file - same look as MemberBalanceExportService/LoanListExportService. */
@Service
public class MemberListExportService {
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    private static final String[] COLUMNS = {"S/N", "Regno", "Name", "Status", "Role", "Monthly Savings"};
    private static final float[] COL_WIDTHS = {30, 65, 200, 80, 70, 110};

    private final byte[] logoBytes;

    public MemberListExportService() throws IOException {
        try (var in = new ClassPathResource("images/logo.jpg").getInputStream()) {
            this.logoBytes = in.readAllBytes();
        }
    }

    private static String title(String statusLabel) {
        return "ASUU-MOAUM Thrift - Members (" + statusLabel + ")";
    }

    public byte[] toExcel(List<Member> members, String statusLabel) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int pictureIdx = wb.addPicture(logoBytes, Workbook.PICTURE_TYPE_JPEG);
            Sheet sheet = wb.createSheet("Members");

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
            setTitleCell(sheet, title(statusLabel), titleStyle);

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
            for (Member m : members) {
                Row xr = sheet.createRow(r++);
                Cell snCell = xr.createCell(0);
                snCell.setCellValue(sn++);
                snCell.setCellStyle(cellStyle);
                setCell(xr, 1, m.getRegno(), cellStyle);
                setCell(xr, 2, m.getFullName(), cellStyle);
                setCell(xr, 3, m.getStatus().name(), cellStyle);
                setCell(xr, 4, m.getRole().name(), cellStyle);
                setNumberCell(xr, 5, m.getMonthlySavingsAmount(), numberStyle);
            }
            if (members.isEmpty()) {
                Row row = sheet.createRow(r);
                Cell c = row.createCell(0);
                c.setCellValue("No members match this filter.");
                c.setCellStyle(cellStyle);
            }
            for (int i = 0; i < COLUMNS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void setTitleCell(Sheet sheet, String value, CellStyle style) {
        Row row = sheet.createRow(0);
        Cell c = row.createCell(2);
        c.setCellValue(value);
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

    public byte[] toPdf(List<Member> members, String statusLabel) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = PDImageXObject.createFromByteArray(doc, logoBytes, "logo");
            PdfCursor cursor = new PdfCursor(doc, logo, statusLabel);
            cursor.table(members);
            cursor.close();

            doc.save(out);
            return out.toByteArray();
        }
    }

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
            text(MARGIN + LOGO_SIZE + 12, y - 14, title(statusLabel));
            y -= (LOGO_SIZE + 14);
            line(MARGIN, y, MARGIN + tableWidth(), y);
            y -= 18;
        }

        void table(List<Member> members) throws IOException {
            if (members.isEmpty()) {
                newPage();
                cs.setFont(regular, 9);
                text(MARGIN, y, "No members match this filter.");
                return;
            }

            boolean headerNeeded = true;
            int sn = 1;
            for (Member m : members) {
                if (headerNeeded || y - ROW_HEIGHT < MARGIN) {
                    newPage();
                    drawTableHeaderRow();
                    headerNeeded = false;
                }
                String[] values = {
                        String.valueOf(sn++), m.getRegno(), safe(m.getFullName(), 32),
                        m.getStatus().name(), m.getRole().name(), formatAmount(m.getMonthlySavingsAmount()),
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
                boolean numeric = i == 5;
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
