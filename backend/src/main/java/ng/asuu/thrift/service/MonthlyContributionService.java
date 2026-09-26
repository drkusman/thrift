package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.LoanRepaymentSchedule.ScheduleStatus;
import ng.asuu.thrift.repo.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin uploads an Excel sheet of regno -> amount per month. Each row is matched to a member; unmatched
 * rows are recorded with an error so the admin can see exactly what to fix and re-upload, same UX
 * pattern as GAT's CSV import summaries. Expected columns (header row, any order): regno, amount, kind.
 * <p>
 * KIND drives how a row is posted:
 * <ul>
 *   <li>LOAN_REPAYMENT - a single credit against TransCat.LOAN (transType LRPT), applied to the member's
 *       oldest unpaid schedule installment if one exists.
 *   <li>CASH (or DEPOSIT, CASH DEPOSIT) - a single savings credit, transType MSVG3.
 *   <li>REFUND (or OVER DEDUCTION, REFUND OF OVER DEDUCTION) - a single savings credit, transType MSVG2 -
 *       same code the SAVINGS waterfall below uses for its own leftover, but postable on its own too
 *       (e.g. a manual correction unrelated to that month's savings lump sum).
 *   <li>IOS1 - a single savings credit, transType IOS1. The admin's end-of-fiscal-year interest-on-savings
 *       calculation, uploaded as a credit to every member at once.
 *   <li>IOS2 - a single savings <b>debit</b>, transType IOS2 - IOS1's reaction: a member can optionally
 *       apply at any time to have their accumulated interest paid out. The admin pays them externally
 *       (bank transfer) then uploads that same amount here as an IOS2 debit, removing it from their
 *       savings since it's no longer sitting in the thrift account.
 *   <li>Anything else (including SAVINGS, or a blank cell) - the complex case: the amount is a single
 *       lump sum that pays the member's own standing monthly savings first, then whatever's left is
 *       applied one loan at a time (oldest disbursed first) to each RUNNING loan's own repayment plan,
 *       capped at that loan's remaining balance - until either every loan is covered or the money runs
 *       out. Whichever loan the money runs out on gets a partial payment; every loan after it in the
 *       order gets nothing this period. Money left over after every running loan is fully covered is
 *       posted back to the member as a Refund of Over Deduction (transType MSVG2).
 * </ul>
 * KIND nomenclature isn't consistent across uploads, so recognition is case-insensitive and tolerant of
 * the shorter variants above rather than requiring the exact canonical phrase.
 * <p>
 * The admin declares, per upload, whether it's the bulk payroll-driven "Monthly contribution" file (see
 * upload's mainContribution parameter) - that one locks its period against a double-run, since re-posting
 * it would double-credit everyone in it - or an "IOS / Correction" file (IOS1, IOS2, Cash Deposit, Refund
 * of Over Deduction), which doesn't lock the period: those are ad-hoc, per-member events (an annual
 * interest run, a member's one-off payout, a manual correction) that legitimately happen more than once,
 * even several times, within a period whose main contribution is already posted. The declaration is
 * checked against the file's actual KIND content, not blindly trusted either way.
 */
@Service
public class MonthlyContributionService {
    /** Admin's free-text KIND column, upper-cased and trimmed, mapped onto a single canonical code -
     *  the nomenclature isn't consistent across uploads, so every variant seen in practice is accepted. */
    private static final Set<String> CASH_DEPOSIT_KINDS = Set.of("CASH", "DEPOSIT", "CASH DEPOSIT");
    private static final Set<String> REFUND_OVER_DEDUCTION_KINDS = Set.of("REFUND", "OVER DEDUCTION", "REFUND OF OVER DEDUCTION");
    private static final Set<String> IOS1_KINDS = Set.of("IOS1", "INTEREST ON SAVINGS");
    private static final Set<String> IOS2_KINDS = Set.of("IOS2", "PAYMENT OF DIVIDEND");

    private final MonthlyContributionBatchRepository batchRepository;
    private final MonthlyContributionBatchRowRepository rowRepository;
    private final MonthlyContributionBatchRowPostingRepository postingRepository;
    private final MemberRepository memberRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanRepository loanRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final LoanService loanService;

    public MonthlyContributionService(MonthlyContributionBatchRepository batchRepository,
                                       MonthlyContributionBatchRowRepository rowRepository,
                                       MonthlyContributionBatchRowPostingRepository postingRepository,
                                       MemberRepository memberRepository,
                                       LoanRepaymentScheduleRepository scheduleRepository,
                                       LoanRepository loanRepository,
                                       LedgerEntryRepository ledgerEntryRepository,
                                       LedgerService ledgerService,
                                       LoanService loanService) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.postingRepository = postingRepository;
        this.memberRepository = memberRepository;
        this.scheduleRepository = scheduleRepository;
        this.loanRepository = loanRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
        this.loanService = loanService;
    }

    /** Periods whose main contribution (SAVINGS/LOAN_REPAYMENT) has already been posted - the frontend
     *  disables these in its month picker so that specific upload can't be posted twice; admin must
     *  deleteBatch() first to reopen one. A period with only IOS1/IOS2/Cash Deposit/Refund of Over
     *  Deduction batches stays open, since those are ad-hoc per-member events that legitimately recur. */
    public Set<String> uploadedPeriods() {
        Set<String> periods = new LinkedHashSet<>();
        for (MonthlyContributionBatch b : batchRepository.findAllByOrderByUploadedAtDesc()) {
            if (b.isLocksPeriod()) periods.add(b.getPeriodMonth());
        }
        return periods;
    }

    public List<MonthlyContributionBatch> batches() {
        return batchRepository.findAllByOrderByUploadedAtDesc();
    }

    public MonthlyContributionBatch requireBatch(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such batch: " + id));
    }

    /**
     * mainContribution is the admin's own explicit declaration of which kind of file this is - not
     * inferred from KIND cell content alone, so the choice (and therefore whether the period gets
     * locked) is always visible and deliberate, rather than a side effect of what happens to be typed
     * in a column. true = the bulk monthly SAVINGS/LOAN_REPAYMENT file, locks the period; false = an
     * IOS1/IOS2/Cash Deposit/Refund of Over Deduction correction, which doesn't. Content is still
     * checked against that declaration: a "correction" upload that actually contains a locking row is
     * rejected outright rather than silently let through without the protection it needs.
     */
    @Transactional
    public MonthlyContributionBatch upload(Member admin, String periodMonth, boolean mainContribution, MultipartFile file) throws IOException {
        byte[] fileBytes = file.getBytes();
        if (!mainContribution && containsLockingKind(fileBytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This file has a SAVINGS or LOAN_REPAYMENT row, which needs the period lock - re-upload it with " +
                    "\"Monthly contribution\" selected instead of \"IOS / Correction\".");
        }
        boolean locksPeriod = mainContribution;
        if (locksPeriod && batchRepository.findByPeriodMonth(periodMonth).stream().anyMatch(MonthlyContributionBatch::isLocksPeriod)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    periodMonth + " already has a posted SAVINGS/LOAN_REPAYMENT upload - delete that batch first to " +
                    "re-upload it. IOS1, IOS2, Cash Deposit, and Refund of Over Deduction aren't affected by this and " +
                    "can still be uploaded for this period.");
        }
        Map<String, Long> memberIdByRegno = new HashMap<>();
        for (Member m : memberRepository.findAll()) memberIdByRegno.put(m.getRegno(), m.getId());

        MonthlyContributionBatch batch = new MonthlyContributionBatch();
        batch.setPeriodMonth(periodMonth);
        batch.setFileName(file.getOriginalFilename());
        batch.setUploadedBy(admin.getId());
        batch.setFileBytes(fileBytes);
        batch.setLocksPeriod(locksPeriod);
        batch = batchRepository.save(batch);

        int total = 0, matched = 0;
        long totalAmount = 0;

        try (InputStream in = new ByteArrayInputStream(fileBytes); Workbook wb = WorkbookFactory.create(in)) {
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
                    rowRepository.save(batchRow);
                } else {
                    batchRow = rowRepository.save(batchRow);
                    LocalDate postDate = lastDayOfMonth(periodMonth);
                    String kindUpper = batchRow.getKind();
                    if ("LOAN_REPAYMENT".equals(kindUpper)) {
                        var entry = ledgerService.post(memberId, amount, postDate, "Loan repayment - " + periodMonth,
                                "LRPT", TransCat.LOAN, DrCr.CR, null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
                        Long scheduleId = applyToSchedule(memberId, amount);
                        savePosting(batchRow.getId(), entry.getId(), scheduleId, amount);
                    } else if (CASH_DEPOSIT_KINDS.contains(kindUpper)) {
                        postSimpleSavingsEntry(admin, memberId, amount, postDate, periodMonth,
                                "MSVG3", "Cash deposit", DrCr.CR, batchRow.getId());
                    } else if (REFUND_OVER_DEDUCTION_KINDS.contains(kindUpper)) {
                        postSimpleSavingsEntry(admin, memberId, amount, postDate, periodMonth,
                                "MSVG2", "Refund of Over Deduction", DrCr.CR, batchRow.getId());
                    } else if (IOS1_KINDS.contains(kindUpper)) {
                        postSimpleSavingsEntry(admin, memberId, amount, postDate, periodMonth,
                                "IOS1", "Interest on Savings", DrCr.CR, batchRow.getId());
                    } else if (IOS2_KINDS.contains(kindUpper)) {
                        postSimpleSavingsEntry(admin, memberId, amount, postDate, periodMonth,
                                "IOS2", "Payment of Dividend", DrCr.DR, batchRow.getId());
                    } else {
                        allocateSavings(admin, memberId, amount, periodMonth, postDate, batchRow.getId());
                    }
                    batchRow.setMatched(true);
                    rowRepository.save(batchRow);
                    matched++;
                    totalAmount += amount;
                }
            }
        }

        batch.setTotalRows(total);
        batch.setMatchedRows(matched);
        batch.setTotalAmount(totalAmount);
        return batchRepository.save(batch);
    }

    /** A LOAN_REPAYMENT row, or anything that falls through to the SAVINGS waterfall (including a blank
     *  KIND cell, or no KIND column at all), is a bulk once-per-period posting that can't safely be
     *  re-run for the same period. IOS1/IOS2/Cash Deposit/Refund of Over Deduction are ad-hoc per-member
     *  events that can legitimately recur, so they're exempt. */
    private static boolean isLockingKind(String kindUpper) {
        if (CASH_DEPOSIT_KINDS.contains(kindUpper)) return false;
        if (REFUND_OVER_DEDUCTION_KINDS.contains(kindUpper)) return false;
        if (IOS1_KINDS.contains(kindUpper)) return false;
        if (IOS2_KINDS.contains(kindUpper)) return false;
        return true;
    }

    /** Pre-scans the workbook's KIND column, without touching the database, to decide whether this
     *  upload needs the period lock at all - avoids blocking a same-period IOS1/IOS2/Cash Deposit/Refund
     *  upload just because that period already has a locking batch. */
    private boolean containsLockingKind(byte[] fileBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(fileBytes); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            int kindCol = -1;
            for (Cell c : header) {
                if (c.getStringCellValue().trim().equalsIgnoreCase("kind")) kindCol = c.getColumnIndex();
            }
            if (kindCol < 0) return true; // no KIND column at all - every row defaults to SAVINGS

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String kind = stringOf(row.getCell(kindCol));
                if (kind == null || kind.isBlank()) return true; // defaults to SAVINGS
                if (isLockingKind(kind.trim().toUpperCase())) return true;
            }
            return false;
        }
    }

    /** Returns the installment's id this payment was applied to, so a later batch deletion can reverse
     *  this exact amount off this exact row instead of guessing which installment it touched. */
    private Long applyToSchedule(Long memberId, long amountPaid) {
        for (Loan loan : loanRepository.findByMemberIdOrderByAppliedAtDesc(memberId)) {
            if (loan.getStatus() != LoanStatus.RUNNING) continue;
            Long scheduleId = applyToScheduleForLoan(loan.getId(), amountPaid);
            if (scheduleId != null) return scheduleId;
        }
        return null;
    }

    /** Same as applyToSchedule, but for a specific loan already chosen by the caller (the SAVINGS
     *  waterfall knows exactly which loan each payment belongs to, rather than guessing "the first
     *  running loan for this member"). Returns null if this loan has no unpaid installment at all -
     *  a legacy-reconstructed loan, for instance, has no schedule rows to apply to. */
    private Long applyToScheduleForLoan(Long loanId, long amountPaid) {
        var next = scheduleRepository.findFirstByLoanIdAndStatusNotOrderByInstallmentNoAsc(loanId, ScheduleStatus.PAID);
        if (next.isEmpty()) return null;
        LoanRepaymentSchedule installment = next.get();
        long newPaid = installment.getAmountPaid() + amountPaid;
        installment.setAmountPaid(newPaid);
        installment.setStatus(newPaid >= installment.getAmountDue() ? ScheduleStatus.PAID : ScheduleStatus.PARTIAL);
        installment.setPaidAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        scheduleRepository.save(installment);

        boolean allPaid = scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId)
                .stream().allMatch(s -> s.getStatus() == ScheduleStatus.PAID);
        if (allPaid) {
            loanRepository.findById(loanId).ifPresent(loan -> {
                loan.setStatus(LoanStatus.COMPLETED);
                loanRepository.save(loan);
            });
        }
        return installment.getId();
    }

    /**
     * The SAVINGS waterfall: pays the member's own standing monthly savings first, then applies
     * whatever's left to each RUNNING loan in turn (oldest disbursed first - the older loan gets priority
     * when there isn't enough to go around), each capped at its own remaining balance - same cap rule as
     * LoanService.activeMonthlyRepayment. Money that outlasts every running loan's plan comes back to the
     * member as a Refund of Over Deduction.
     */
    private void allocateSavings(Member admin, Long memberId, long amount, String periodMonth, LocalDate postDate, Long batchRowId) {
        long remaining = amount;
        Member member = memberRepository.findById(memberId).orElseThrow();

        long savingsPortion = Math.min(remaining, member.getMonthlySavingsAmount());
        if (savingsPortion > 0) {
            var entry = ledgerService.post(memberId, savingsPortion, postDate, "Monthly savings - " + periodMonth,
                    "MSVG", TransCat.SAVINGS, DrCr.CR, null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
            savePosting(batchRowId, entry.getId(), null, savingsPortion);
        }
        remaining -= savingsPortion;
        if (remaining <= 0) return;

        for (Loan loan : loanRepository.findByMemberIdAndStatusOrderByDisbursedAtAsc(memberId, LoanStatus.RUNNING)) {
            Long monthly = loan.getMonthlyRepaymentAmount();
            if (monthly == null) continue;
            long balance = loanService.balanceFor(loan.getId());
            if (balance <= 0) continue;
            long due = Math.min(monthly, balance);

            long payment = Math.min(remaining, due);
            var entry = ledgerService.post(memberId, payment, postDate, "Loan repayment - " + periodMonth,
                    "LRPT", TransCat.LOAN, DrCr.CR, loan.getId(), LedgerSource.MONTHLY_UPLOAD, admin.getId());
            Long scheduleId = applyToScheduleForLoan(loan.getId(), payment);
            savePosting(batchRowId, entry.getId(), scheduleId, payment);

            remaining -= payment;
            if (remaining <= 0) return;
        }

        if (remaining > 0) {
            var entry = ledgerService.post(memberId, remaining, postDate, "Refund of Over Deduction - " + periodMonth,
                    "MSVG2", TransCat.SAVINGS, DrCr.CR, null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
            savePosting(batchRowId, entry.getId(), null, remaining);
        }
    }

    /** A plain single posting against the member's savings, no loan or waterfall involved - covers Cash
     *  Deposit (MSVG3, CR), a standalone Refund of Over Deduction (MSVG2, CR - same code the SAVINGS
     *  waterfall produces for its own leftover, but postable directly too), IOS1 (CR, the admin's
     *  end-of-fiscal-year interest calculation), and IOS2 (DR, its optional reaction when a member cashes
     *  out their interest externally and it needs removing from their savings here). */
    private void postSimpleSavingsEntry(Member admin, Long memberId, long amount, LocalDate postDate,
                                         String periodMonth, String transType, String label, DrCr drCr, Long batchRowId) {
        var entry = ledgerService.post(memberId, amount, postDate, label + " - " + periodMonth,
                transType, TransCat.SAVINGS, drCr, null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
        savePosting(batchRowId, entry.getId(), null, amount);
    }

    private void savePosting(Long batchRowId, Long ledgerEntryId, Long scheduleId, long amount) {
        MonthlyContributionBatchRowPosting posting = new MonthlyContributionBatchRowPosting();
        posting.setBatchRowId(batchRowId);
        posting.setLedgerEntryId(ledgerEntryId);
        posting.setScheduleId(scheduleId);
        posting.setAmount(amount);
        postingRepository.save(posting);
    }

    /**
     * Undoes a bad upload in full: deletes every ledger entry it posted (a SAVINGS row may have posted
     * several - its own savings, one or more loan repayments, and a refund), reverses the exact amount
     * each posting applied off its own schedule installment (never another upload's), reopens a loan if
     * reversing tips it back below fully-paid, then removes the batch and its rows - reopening the
     * period for re-upload.
     */
    @Transactional
    public void deleteBatch(Long batchId) {
        if (!batchRepository.existsById(batchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such batch: " + batchId);
        }
        List<MonthlyContributionBatchRow> rows = rowRepository.findByBatchId(batchId);
        List<Long> rowIds = rows.stream().map(MonthlyContributionBatchRow::getId).toList();
        List<MonthlyContributionBatchRowPosting> postings = postingRepository.findByBatchRowIdIn(rowIds);
        List<Long> ledgerEntryIds = new ArrayList<>();
        for (MonthlyContributionBatchRowPosting posting : postings) {
            if (posting.getScheduleId() != null) {
                scheduleRepository.findById(posting.getScheduleId()).ifPresent(installment -> reverseInstallment(installment, posting.getAmount()));
            }
            ledgerEntryIds.add(posting.getLedgerEntryId());
        }
        // Postings and rows reference the ledger entries by FK, so they must go first.
        postingRepository.deleteAll(postings);
        rowRepository.deleteAll(rows);
        ledgerEntryRepository.deleteAllById(ledgerEntryIds);
        batchRepository.deleteById(batchId);
    }

    private void reverseInstallment(LoanRepaymentSchedule installment, long amount) {
        long newPaid = Math.max(0, installment.getAmountPaid() - amount);
        installment.setAmountPaid(newPaid);
        installment.setStatus(newPaid <= 0 ? ScheduleStatus.PENDING
                : newPaid < installment.getAmountDue() ? ScheduleStatus.PARTIAL : ScheduleStatus.PAID);
        scheduleRepository.save(installment);

        loanRepository.findById(installment.getLoanId()).ifPresent(loan -> {
            if (loan.getStatus() != LoanStatus.COMPLETED) return;
            boolean stillAllPaid = scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loan.getId())
                    .stream().allMatch(s -> s.getStatus() == ScheduleStatus.PAID);
            if (!stillAllPaid) {
                loan.setStatus(LoanStatus.RUNNING);
                loanRepository.save(loan);
            }
        });
    }

    /** Blank sheet for admin to fill in: SN/NAME are for readability only - only regno and amount are
     *  actually read on upload, so a member's amount can be found and set correctly regardless of them. */
    public byte[] template() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Monthly Contributions");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);

            String[] headers = {"SN", "REGNO", "NAME", "AMOUNT", "KIND"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue(1);
            example.createCell(1).setCellValue("A00123");
            example.createCell(2).setCellValue("JOHN DOE");
            example.createCell(3).setCellValue(20000);
            example.createCell(4).setCellValue("SAVINGS");

            Row note = sheet.createRow(3);
            note.createCell(0).setCellValue("KIND is SAVINGS, LOAN_REPAYMENT, CASH DEPOSIT, REFUND OF OVER DEDUCTION, " +
                    "IOS1, or IOS2 (default SAVINGS if left blank). SN and NAME are for readability only - only REGNO and AMOUNT are used to post the entry.");

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Posts on the last day of the period, matching every legacy-imported entry (28th/30th/31st) and
     *  the remittance schedule's own "last date of the month" convention - not the 1st. */
    private static LocalDate lastDayOfMonth(String periodMonth) {
        try {
            LocalDate first = LocalDate.parse(periodMonth + "-01");
            return first.withDayOfMonth(first.lengthOfMonth());
        } catch (Exception e) {
            LocalDate now = LocalDate.now();
            return now.withDayOfMonth(now.lengthOfMonth());
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
