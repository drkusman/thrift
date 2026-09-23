package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.LoanRepaymentSchedule.ScheduleStatus;
import ng.asuu.thrift.repo.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin uploads an Excel sheet of regno -> amount per month (savings, or loan repayments). Each row
 * is matched to a member and posted as a LedgerEntry; unmatched rows are recorded with an error so
 * the admin can see exactly what to fix and re-upload, same UX pattern as GAT's CSV import summaries.
 * Expected columns (header row, any order): regno, amount, kind ("SAVINGS" or "LOAN_REPAYMENT").
 */
@Service
public class MonthlyContributionService {
    private final MonthlyContributionBatchRepository batchRepository;
    private final MonthlyContributionBatchRowRepository rowRepository;
    private final MemberRepository memberRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanRepository loanRepository;
    private final LedgerService ledgerService;

    public MonthlyContributionService(MonthlyContributionBatchRepository batchRepository,
                                       MonthlyContributionBatchRowRepository rowRepository,
                                       MemberRepository memberRepository,
                                       LoanRepaymentScheduleRepository scheduleRepository,
                                       LoanRepository loanRepository,
                                       LedgerService ledgerService) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.memberRepository = memberRepository;
        this.scheduleRepository = scheduleRepository;
        this.loanRepository = loanRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public MonthlyContributionBatch upload(Member admin, String periodMonth, MultipartFile file) throws IOException {
        Map<String, Long> memberIdByRegno = new HashMap<>();
        for (Member m : memberRepository.findAll()) memberIdByRegno.put(m.getRegno(), m.getId());

        MonthlyContributionBatch batch = new MonthlyContributionBatch();
        batch.setPeriodMonth(periodMonth);
        batch.setFileName(file.getOriginalFilename());
        batch.setUploadedBy(admin.getId());
        batch = batchRepository.save(batch);

        int total = 0, matched = 0;
        long totalAmount = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            int regnoCol = -1, amountCol = -1, kindCol = -1;
            for (Cell c : header) {
                String h = c.getStringCellValue().trim().toLowerCase();
                if (h.equals("regno")) regnoCol = c.getColumnIndex();
                else if (h.equals("amount")) amountCol = c.getColumnIndex();
                else if (h.equals("kind")) kindCol = c.getColumnIndex();
            }
            if (regnoCol < 0 || amountCol < 0) {
                throw new IllegalArgumentException("Sheet must have 'regno' and 'amount' columns");
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String regno = stringOf(row.getCell(regnoCol));
                if (regno == null || regno.isBlank()) continue;
                total++;

                MonthlyContributionBatchRow batchRow = new MonthlyContributionBatchRow();
                batchRow.setBatchId(batch.getId());
                batchRow.setRegno(regno);
                String kind = kindCol >= 0 ? stringOf(row.getCell(kindCol)) : "SAVINGS";
                if (kind == null || kind.isBlank()) kind = "SAVINGS";
                batchRow.setKind(kind.toUpperCase());

                double amountVal = numericOf(row.getCell(amountCol));
                long amount = Math.round(amountVal);
                batchRow.setAmount(amount);

                Long memberId = memberIdByRegno.get(regno);
                if (memberId == null) {
                    batchRow.setMatched(false);
                    batchRow.setErrorMessage("No member with regno " + regno);
                } else {
                    LocalDate postDate = firstOfMonth(periodMonth);
                    boolean isRepayment = "LOAN_REPAYMENT".equals(batchRow.getKind());
                    var entry = ledgerService.post(memberId, amount, postDate,
                            (isRepayment ? "Loan repayment" : "Monthly savings") + " - " + periodMonth,
                            batchRow.getKind(), isRepayment ? TransCat.LOAN : TransCat.SAVINGS, DrCr.CR,
                            null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
                    batchRow.setMatched(true);
                    batchRow.setLedgerEntryId(entry.getId());
                    matched++;
                    totalAmount += amount;

                    if (isRepayment) applyToSchedule(memberId, amount);
                }
                rowRepository.save(batchRow);
            }
        }

        batch.setTotalRows(total);
        batch.setMatchedRows(matched);
        batch.setTotalAmount(totalAmount);
        return batchRepository.save(batch);
    }

    private void applyToSchedule(Long memberId, long amountPaid) {
        for (Loan loan : loanRepository.findByMemberIdOrderByAppliedAtDesc(memberId)) {
            if (loan.getStatus() != LoanStatus.DISBURSED && loan.getStatus() != LoanStatus.RUNNING) continue;
            var next = scheduleRepository.findFirstByLoanIdAndStatusNotOrderByInstallmentNoAsc(loan.getId(), ScheduleStatus.PAID);
            if (next.isEmpty()) continue;
            LoanRepaymentSchedule installment = next.get();
            long newPaid = installment.getAmountPaid() + amountPaid;
            installment.setAmountPaid(newPaid);
            installment.setStatus(newPaid >= installment.getAmountDue() ? ScheduleStatus.PAID : ScheduleStatus.PARTIAL);
            installment.setPaidAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
            scheduleRepository.save(installment);

            if (loan.getStatus() == LoanStatus.DISBURSED) {
                loan.setStatus(LoanStatus.RUNNING);
                loanRepository.save(loan);
            }
            boolean allPaid = scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loan.getId())
                    .stream().allMatch(s -> s.getStatus() == ScheduleStatus.PAID);
            if (allPaid) {
                loan.setStatus(LoanStatus.COMPLETED);
                loanRepository.save(loan);
            }
            return;
        }
    }

    private static LocalDate firstOfMonth(String periodMonth) {
        try {
            return LocalDate.parse(periodMonth + "-01");
        } catch (Exception e) {
            return LocalDate.now().withDayOfMonth(1);
        }
    }

    private static String stringOf(Cell c) {
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            default -> null;
        };
    }

    private static double numericOf(Cell c) {
        if (c == null) return 0;
        return switch (c.getCellType()) {
            case NUMERIC -> c.getNumericCellValue();
            case STRING -> { try { yield Double.parseDouble(c.getStringCellValue().trim()); } catch (Exception e) { yield 0; } }
            default -> 0;
        };
    }
}
