package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.Member;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class TransactionExportService {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();

    public byte[] toExcel(Member member, List<LedgerEntry> entries) throws IOException {
        List<LedgerEntry> sorted = sortedAscending(entries);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Transactions");
            CellStyle boldStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            boldStyle.setFont(bold);

            Row header = sheet.createRow(0);
            String[] cols = {"Date", "Description", "Type", "Category", "DR/CR", "Amount", "Balance"};
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(boldStyle);
            }

            long savingsBalance = 0, loanBalance = 0;
            int r = 1;
            for (LedgerEntry e : sorted) {
                long signed = e.getDrCrStatus() == DrCr.CR ? e.getAmount() : -e.getAmount();
                if (e.getTransCat() == LedgerEntry.TransCat.SAVINGS) savingsBalance += signed;
                else if (e.getTransCat() == LedgerEntry.TransCat.LOAN) loanBalance -= signed;

                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getDate().format(DATE_FMT));
                row.createCell(1).setCellValue(e.getDescription() == null ? "" : e.getDescription());
                row.createCell(2).setCellValue(e.getTransType() == null ? "" : e.getTransType());
                row.createCell(3).setCellValue(e.getTransCat().name());
                row.createCell(4).setCellValue(e.getDrCrStatus().name());
                row.createCell(5).setCellValue(e.getAmount());
                row.createCell(6).setCellValue(e.getTransCat() == LedgerEntry.TransCat.SAVINGS ? savingsBalance : loanBalance);
            }
            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] toPdf(Member member, List<LedgerEntry> entries) throws IOException {
        List<LedgerEntry> sorted = sortedAscending(entries);
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin = 40;
            float rowHeight = 16;
            float y = 0;
            PDPage page = null;
            PDPageContentStream cs = null;

            long savingsBalance = 0, loanBalance = 0;
            int rowsPerPage = (int) ((PAGE_HEIGHT - 140) / rowHeight);
            int rowOnPage = rowsPerPage;

            for (LedgerEntry e : sorted) {
                if (rowOnPage >= rowsPerPage) {
                    if (cs != null) cs.close();
                    page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = PAGE_HEIGHT - margin;
                    cs.setFont(bold, 14);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText("ASUU-MOAUM Thrift - Transaction History");
                    cs.endText();
                    y -= 20;
                    cs.setFont(regular, 10);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(member.getFullName() + " (" + member.getRegno() + ")");
                    cs.endText();
                    y -= 24;

                    cs.setFont(bold, 9);
                    cs.beginText();
                    cs.newLineAtOffset(margin, y);
                    cs.showText(String.format("%-12s %-30s %-10s %-8s %10s %12s", "Date", "Description", "Category", "DR/CR", "Amount", "Balance"));
                    cs.endText();
                    y -= rowHeight;
                    rowOnPage = 0;
                }

                long signed = e.getDrCrStatus() == DrCr.CR ? e.getAmount() : -e.getAmount();
                if (e.getTransCat() == LedgerEntry.TransCat.SAVINGS) savingsBalance += signed;
                else if (e.getTransCat() == LedgerEntry.TransCat.LOAN) loanBalance -= signed;
                long balance = e.getTransCat() == LedgerEntry.TransCat.SAVINGS ? savingsBalance : loanBalance;

                String desc = e.getDescription() == null ? "" : e.getDescription();
                if (desc.length() > 30) desc = desc.substring(0, 27) + "...";
                String line = String.format(Locale.UK, "%-12s %-30s %-10s %-8s %,10d %,12d",
                        e.getDate().format(DATE_FMT), desc, e.getTransCat().name(), e.getDrCrStatus().name(), e.getAmount(), balance);

                cs.setFont(regular, 8);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText(line);
                cs.endText();
                y -= rowHeight;
                rowOnPage++;
            }
            if (cs != null) cs.close();
            if (doc.getNumberOfPages() == 0) {
                PDPage empty = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
                doc.addPage(empty);
                try (PDPageContentStream ecs = new PDPageContentStream(doc, empty)) {
                    ecs.setFont(regular, 12);
                    ecs.beginText();
                    ecs.newLineAtOffset(margin, PAGE_HEIGHT - margin);
                    ecs.showText("No transactions found for " + member.getFullName() + " (" + member.getRegno() + ")");
                    ecs.endText();
                }
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private static List<LedgerEntry> sortedAscending(List<LedgerEntry> entries) {
        List<LedgerEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(LedgerEntry::getDate).thenComparing(LedgerEntry::getId));
        return sorted;
    }
}
