package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.domain.MonthlyContributionBatch;
import ng.asuu.thrift.domain.MonthlyContributionBatchRow;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.repo.MonthlyContributionBatchRepository;
import ng.asuu.thrift.repo.MonthlyContributionBatchRowPostingRepository;
import ng.asuu.thrift.repo.MonthlyContributionBatchRowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates and posts each active member's annual Interest on Savings (IOS1) automatically, instead of
 * admin computing it externally and uploading it via Monthly Upload's IOS1 kind - same end result (an
 * IOS1 CR ledger entry per member, transType IOS1), just computed here: balance = savings balance at the
 * close of the fiscal year (mirrors MemberBalanceReportService's own point-in-time snapshot), IOS =
 * round(balance * ratePercent / 100). The rate is whatever the admin types in for this specific run - not
 * a persisted setting - since it's expected to change year to year and this only runs once a year anyway.
 * <p>
 * Reuses the exact same MonthlyContributionBatch/Row/Posting tables Monthly Upload already writes to,
 * rather than a new audit table: the resulting "batch" shows up in the existing batch history (and its
 * existing deleteBatch() already reverses it in full) with no new UI needed for that. Marked
 * non-locking, same as a manually-uploaded IOS1 file - it's an annual event, not a monthly payroll run.
 */
@Service
public class AnnualIosService {
    private static final String BATCH_LABEL_PREFIX = "Auto-calculated IOS - FY ";

    private final JdbcTemplate jdbc;
    private final MemberRepository memberRepository;
    private final MonthlyContributionBatchRepository batchRepository;
    private final MonthlyContributionBatchRowRepository rowRepository;
    private final MonthlyContributionBatchRowPostingRepository postingRepository;
    private final LedgerService ledgerService;
    private final IncomeReportService incomeReportService;

    public AnnualIosService(JdbcTemplate jdbc, MemberRepository memberRepository,
                             MonthlyContributionBatchRepository batchRepository,
                             MonthlyContributionBatchRowRepository rowRepository,
                             MonthlyContributionBatchRowPostingRepository postingRepository,
                             LedgerService ledgerService, IncomeReportService incomeReportService) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.postingRepository = postingRepository;
        this.ledgerService = ledgerService;
        this.incomeReportService = incomeReportService;
    }

    public record Row(Long memberId, String regno, String fullName, long savingsBalance, long iosAmount) {}

    /** totalCooperativeIncome is the same fiscal year's total income (see IncomeReportService) - shown
     *  alongside the proposed IOS total so the admin can see what share of the year's income this payout
     *  would represent before committing to it, not just the payout amount in isolation. */
    public record Preview(String fiscalYearLabel, LocalDate since, LocalDate until, double ratePercent,
                           List<Row> rows, long totalAmount, boolean alreadyRun, long totalCooperativeIncome) {}

    public Preview preview(LocalDate since, double ratePercent) {
        LocalDate until = since.plusYears(1).minusDays(1);
        Map<Long, Long> savingsByMember = savingsBalances(until);

        List<Row> rows = memberRepository.findByStatusOrderByFullNameAsc(MemberStatus.ACTIVE).stream()
                .map(m -> {
                    long balance = savingsByMember.getOrDefault(m.getId(), 0L);
                    long ios = Math.round(balance * ratePercent / 100.0);
                    return new Row(m.getId(), m.getRegno(), m.getFullName(), balance, ios);
                })
                .filter(r -> r.iosAmount() > 0)
                .toList();

        long total = rows.stream().mapToLong(Row::iosAmount).sum();
        long income = incomeReportService.forFiscalYear(since).total();
        return new Preview(fiscalYearLabel(since), since, until, ratePercent, rows, total, alreadyRun(since), income);
    }

    /** True if an auto-calculated IOS batch already exists for this fiscal year - re-running would
     *  double-credit everyone, so the admin must delete that batch first (via the existing Monthly
     *  Upload batch history, which already reverses it in full). */
    public boolean alreadyRun(LocalDate since) {
        String label = BATCH_LABEL_PREFIX + fiscalYearLabel(since);
        return batchRepository.findAllByOrderByUploadedAtDesc().stream()
                .anyMatch(b -> label.equals(b.getFileName()));
    }

    /** Reconstructs the exact rows a posted IOS batch credited, for downloading after the fact - reads
     *  the batch's own persisted rows (regno + amount) rather than recomputing preview() fresh, since by
     *  now the just-posted IOS1 credits are themselves part of everyone's savings balance and would
     *  double-count if run through the balance-at-FY-close query again. The one number that query still
     *  needs - the balance as it stood right before this batch posted - is recovered by subtracting each
     *  row's own IOS amount back out of today's balance. */
    public Preview forBatch(Long batchId) {
        MonthlyContributionBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        List<MonthlyContributionBatchRow> batchRows = rowRepository.findByBatchId(batchId).stream()
                .filter(r -> "IOS1".equals(r.getKind()))
                .toList();

        LocalDate until = LocalDate.parse(batch.getPeriodMonth() + "-31");
        LocalDate since = until.minusYears(1).plusDays(1);
        Map<Long, Long> balancesNow = savingsBalances(until);
        Map<String, Member> membersByRegno = new HashMap<>();
        for (Member m : memberRepository.findAll()) membersByRegno.put(m.getRegno(), m);

        List<Row> rows = batchRows.stream()
                .map(br -> {
                    Member m = membersByRegno.get(br.getRegno());
                    long balanceNow = m == null ? 0 : balancesNow.getOrDefault(m.getId(), 0L);
                    long balanceBefore = balanceNow - br.getAmount();
                    return new Row(m == null ? null : m.getId(), br.getRegno(),
                            m == null ? br.getRegno() : m.getFullName(), balanceBefore, br.getAmount());
                })
                .sorted((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()))
                .toList();

        long total = rows.stream().mapToLong(Row::iosAmount).sum();
        long income = incomeReportService.forFiscalYear(since).total();
        return new Preview(fiscalYearLabel(since), since, until, 0, rows, total, true, income);
    }

    @Transactional
    public MonthlyContributionBatch post(Member admin, LocalDate since, double ratePercent) {
        if (alreadyRun(since)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "IOS has already been calculated and posted for FY " + fiscalYearLabel(since) +
                    " - delete that batch from Monthly Upload first if you need to redo it.");
        }
        Preview preview = preview(since, ratePercent);

        MonthlyContributionBatch batch = new MonthlyContributionBatch();
        batch.setPeriodMonth(preview.until().toString().substring(0, 7));
        batch.setFileName(BATCH_LABEL_PREFIX + preview.fiscalYearLabel());
        batch.setUploadedBy(admin.getId());
        batch.setLocksPeriod(false);
        batch = batchRepository.save(batch);

        for (Row row : preview.rows()) {
            MonthlyContributionBatchRow batchRow = new MonthlyContributionBatchRow();
            batchRow.setBatchId(batch.getId());
            batchRow.setRegno(row.regno());
            batchRow.setKind("IOS1");
            batchRow.setAmount(row.iosAmount());
            batchRow.setMatched(true);
            batchRow = rowRepository.save(batchRow);

            var entry = ledgerService.post(row.memberId(), row.iosAmount(), preview.until(),
                    "Interest on Savings - FY " + preview.fiscalYearLabel(), "IOS1", TransCat.SAVINGS, DrCr.CR,
                    null, LedgerSource.MONTHLY_UPLOAD, admin.getId());
            var posting = new ng.asuu.thrift.domain.MonthlyContributionBatchRowPosting();
            posting.setBatchRowId(batchRow.getId());
            posting.setLedgerEntryId(entry.getId());
            posting.setAmount(row.iosAmount());
            postingRepository.save(posting);
        }

        batch.setTotalRows(preview.rows().size());
        batch.setMatchedRows(preview.rows().size());
        batch.setTotalAmount(preview.totalAmount());
        return batchRepository.save(batch);
    }

    /** Net savings balance (CR minus DR) per member as at the close of the given date - one bulk SQL
     *  aggregate over every ledger entry, same approach as MemberBalanceReportService (600+ members would
     *  make a per-member Java loop noticeably slow). */
    private Map<Long, Long> savingsBalances(LocalDate asOfDate) {
        Map<Long, Long> byMember = new HashMap<>();
        jdbc.query(
                "SELECT member_id, SUM(CASE WHEN dr_cr_status = 'CR' THEN amount ELSE -amount END) AS balance " +
                "FROM ledger_entries WHERE trans_cat = 'SAVINGS' AND date <= ? GROUP BY member_id",
                rs -> { byMember.put(rs.getLong("member_id"), rs.getLong("balance")); },
                asOfDate);
        return byMember;
    }

    private static String fiscalYearLabel(LocalDate since) {
        return since.getYear() + "/" + (since.getYear() + 1);
    }
}
