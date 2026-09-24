package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LoanType;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.MemberRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bulk version of LoanService.applyOnBehalf() for members who applied offline with a paper form -
 * admin fills in one row per application on the downloadable template instead of using the on-screen
 * form for each one individually. Every guarantor still goes straight to ACCEPTED, same as a single
 * on-behalf application, since their approval was already collected on paper. Each row is applied in
 * its own transaction (see importBatch), so one bad row is skipped and reported without losing the
 * rows around it.
 */
@Service
public class LoanBatchApplicationService {
    private static final String[] TEMPLATE_HEADERS =
            {"regno", "loantype", "amount", "guarantor1regno", "guarantor2regno", "reason"};

    private final LoanService loanService;
    private final LoanTypeService loanTypeService;
    private final MemberRepository memberRepository;

    public LoanBatchApplicationService(LoanService loanService, LoanTypeService loanTypeService,
                                        MemberRepository memberRepository) {
        this.loanService = loanService;
        this.loanTypeService = loanTypeService;
        this.memberRepository = memberRepository;
    }

    public byte[] template() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Applications");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(TEMPLATE_HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("A00123");
            example.createCell(1).setCellValue("EML");
            example.createCell(2).setCellValue(170000);
            example.createCell(3).setCellValue("A00456");
            example.createCell(4).setCellValue("A00789");
            example.createCell(5).setCellValue("Applied offline with signed guarantor approval form");

            Row note = sheet.createRow(3);
            note.createCell(0).setCellValue("loantype is the code: EML (Emergency), MNL (Main), or PDL (Product). " +
                    "guarantor1regno/guarantor2regno must be two different active members. reason is optional.");

            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    public static class Summary {
        public int processed, created, skipped;
        public final List<String> errors = new ArrayList<>();

        public String text() {
            return String.format(Locale.UK, "Processed %,d rows: %,d created, %,d skipped.%s",
                    processed, created, skipped,
                    errors.isEmpty() ? "" : " First errors: " + String.join("; ", errors.subList(0, Math.min(10, errors.size()))));
        }
    }

    public Summary importBatch(MultipartFile file) throws IOException {
        Summary s = new Summary();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                s.errors.add("Empty sheet");
                return s;
            }

            int regnoCol = -1, typeCol = -1, amountCol = -1, g1Col = -1, g2Col = -1, reasonCol = -1;
            for (Cell c : header) {
                String h = stringOf(c);
                if (h == null) continue;
                switch (h.trim().toLowerCase()) {
                    case "regno" -> regnoCol = c.getColumnIndex();
                    case "loantype" -> typeCol = c.getColumnIndex();
                    case "amount" -> amountCol = c.getColumnIndex();
                    case "guarantor1regno" -> g1Col = c.getColumnIndex();
                    case "guarantor2regno" -> g2Col = c.getColumnIndex();
                    case "reason" -> reasonCol = c.getColumnIndex();
                    default -> { }
                }
            }
            if (regnoCol < 0 || typeCol < 0 || amountCol < 0 || g1Col < 0 || g2Col < 0) {
                s.errors.add("Sheet must have regno, loantype, amount, guarantor1regno and guarantor2regno columns");
                return s;
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String regno = stringOf(row.getCell(regnoCol));
                if (regno == null || regno.isBlank()) continue;
                s.processed++;

                try {
                    Member applicant = memberRepository.findByRegno(regno.trim())
                            .orElseThrow(() -> new IllegalArgumentException("no member with regno " + regno));
                    String typeCode = stringOf(row.getCell(typeCol));
                    LoanType type = loanTypeService.requireByCode(typeCode == null ? "" : typeCode.trim().toUpperCase());
                    long amount = Math.round(numericOf(row.getCell(amountCol)));
                    String g1Regno = stringOf(row.getCell(g1Col));
                    String g2Regno = stringOf(row.getCell(g2Col));
                    Member g1 = memberRepository.findByRegno(g1Regno == null ? "" : g1Regno.trim())
                            .orElseThrow(() -> new IllegalArgumentException("no guarantor with regno " + g1Regno));
                    Member g2 = memberRepository.findByRegno(g2Regno == null ? "" : g2Regno.trim())
                            .orElseThrow(() -> new IllegalArgumentException("no guarantor with regno " + g2Regno));
                    String reason = reasonCol >= 0 ? stringOf(row.getCell(reasonCol)) : null;
                    if (reason == null || reason.isBlank()) reason = "Applied offline with signed guarantor approval form";

                    loanService.applyOnBehalf(applicant, type.getId(), amount, reason, g1.getId(), g2.getId());
                    s.created++;
                } catch (Exception e) {
                    s.skipped++;
                    String message = e instanceof ResponseStatusException rse && rse.getReason() != null
                            ? rse.getReason() : e.getMessage();
                    s.errors.add("Row " + (r + 1) + " (" + regno + "): " + message);
                }
            }
        }
        return s;
    }

    private static String stringOf(Cell c) {
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            default -> null;
        };
    }

    private static double numericOf(Cell c) {
        if (c == null) return 0;
        return switch (c.getCellType()) {
            case NUMERIC -> c.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(c.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 0;
                }
            }
            default -> 0;
        };
    }
}
