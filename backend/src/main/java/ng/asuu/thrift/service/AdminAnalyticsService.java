package ng.asuu.thrift.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cooperative-wide figures for the admin overview: aggregate sums across every member, computed in
 * SQL rather than looping over ledger rows in Java (57k+ rows makes that noticeably slow).
 */
@Service
public class AdminAnalyticsService {
    private final JdbcTemplate jdbc;

    public AdminAnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Summary(long totalMonthlySavings, long totalLoanPayments, long totalMonthlyDeductions,
                           long totalSavings, long totalLoanBalance, long totalEquity) {}

    public record MonthPoint(String month, long amount) {}

    public record StatusCount(String status, long count) {}

    public record FiscalYearTrend(String label, List<MonthPoint> points) {}

    public record TypeAmount(String type, long amount) {}

    public record FiscalYearBreakdown(String label, List<TypeAmount> slices) {}

    public Summary summary() {
        long totalMonthlySavings = queryLong(
                "SELECT COALESCE(SUM(monthly_savings_amount), 0) FROM members WHERE status = 'ACTIVE'");
        long totalLoanPayments = queryLong(
                "SELECT COALESCE(SUM(monthly_repayment_amount), 0) FROM loans WHERE status IN ('DISBURSED', 'RUNNING')");
        long totalSavings = queryLong(
                "SELECT COALESCE(SUM(CASE WHEN dr_cr_status = 'CR' THEN amount ELSE -amount END), 0) " +
                "FROM ledger_entries WHERE trans_cat = 'SAVINGS'");
        long totalLoanBalance = queryLong(
                "SELECT COALESCE(SUM(CASE WHEN dr_cr_status = 'DR' THEN amount ELSE -amount END), 0) " +
                "FROM ledger_entries WHERE trans_cat = 'LOAN'");
        return new Summary(totalMonthlySavings, totalLoanPayments, totalMonthlySavings + totalLoanPayments,
                totalSavings, totalLoanBalance, totalSavings - totalLoanBalance);
    }

    /** Net savings collected (CR minus DR) per month of the thrift's current fiscal year, which runs
     *  1 November to 31 October. All 12 months are always returned, oldest (November) first, with 0
     *  for any month that hasn't happened yet or has no postings. */
    public FiscalYearTrend monthlySavingsTrendForCurrentFiscalYear() {
        return currentFiscalYearTrend(
                "SELECT to_char(date_trunc('month', date), 'YYYY-MM') AS month, " +
                "       SUM(CASE WHEN dr_cr_status = 'CR' THEN amount ELSE -amount END) AS amount " +
                "FROM ledger_entries WHERE trans_cat = 'SAVINGS' AND date >= ? AND date < ? " +
                "GROUP BY 1 ORDER BY 1");
    }

    /** Loans granted this fiscal year, broken down by loan type. Ledger-based (like the savings trend)
     *  rather than reading the loans table, so legacy disbursements are included too: the legacy import
     *  never reconstructed Loan rows for historical loans (see LegacyImportService), but it did carry
     *  the loan type code straight through into trans_type (e.g. "MNL" for every Main Loan disbursement -
     *  confirmed against the actual data, not assumed), so it's a reliable grouping key either way. */
    public FiscalYearBreakdown loansGrantedByTypeForCurrentFiscalYear() {
        LocalDate[] range = currentFiscalYearRange();
        List<TypeAmount> slices = jdbc.query(
                "SELECT COALESCE(lt.name, e.trans_type, 'Other') AS type, SUM(e.amount) AS amount " +
                "FROM ledger_entries e LEFT JOIN loan_types lt ON lt.code = e.trans_type " +
                "WHERE e.trans_cat = 'LOAN' AND e.dr_cr_status = 'DR' AND e.date >= ? AND e.date < ? " +
                "GROUP BY 1 ORDER BY SUM(e.amount) DESC",
                (rs, i) -> new TypeAmount(rs.getString("type"), rs.getLong("amount")),
                range[0], range[1]);
        return new FiscalYearBreakdown(fiscalYearLabel(range[0]), slices);
    }

    /** Loan repayments received (CR postings against the LOAN category) per month of the current
     *  fiscal year. */
    public FiscalYearTrend loanRepaymentsTrendForCurrentFiscalYear() {
        return currentFiscalYearTrend(
                "SELECT to_char(date_trunc('month', date), 'YYYY-MM') AS month, SUM(amount) AS amount " +
                "FROM ledger_entries WHERE trans_cat = 'LOAN' AND dr_cr_status = 'CR' AND date >= ? AND date < ? " +
                "GROUP BY 1 ORDER BY 1");
    }

    /** Runs a month-grouped query (must select "month" text and "amount" long, and take since/until
     *  as its last two bind params) over the thrift's current fiscal year (1 Nov - 31 Oct), filling
     *  in every month with 0 so the chart always shows a full 12-month frame. */
    private FiscalYearTrend currentFiscalYearTrend(String monthGroupedSql) {
        LocalDate[] range = currentFiscalYearRange();
        LocalDate since = range[0], until = range[1];

        List<MonthPoint> rows = jdbc.query(monthGroupedSql,
                (rs, i) -> new MonthPoint(rs.getString("month"), rs.getLong("amount")),
                since, until);
        Map<String, Long> byMonth = rows.stream().collect(Collectors.toMap(MonthPoint::month, MonthPoint::amount));

        List<MonthPoint> points = new ArrayList<>(12);
        DateTimeFormatter key = DateTimeFormatter.ofPattern("yyyy-MM");
        for (LocalDate cursor = since; cursor.isBefore(until); cursor = cursor.plusMonths(1)) {
            String month = cursor.format(key);
            points.add(new MonthPoint(month, byMonth.getOrDefault(month, 0L)));
        }
        return new FiscalYearTrend(fiscalYearLabel(since), points);
    }

    /** The thrift's fiscal year runs 1 November to 31 October; returns {since, until} for the one
     *  containing today, `until` exclusive. */
    private static LocalDate[] currentFiscalYearRange() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 11 ? today.getYear() : today.getYear() - 1;
        LocalDate since = LocalDate.of(startYear, 11, 1);
        return new LocalDate[]{since, since.plusYears(1)};
    }

    private static String fiscalYearLabel(LocalDate since) {
        return since.getYear() + "/" + (since.getYear() + 1);
    }

    /** Current lifecycle distribution across every loan the thrift has ever granted - Pending (awaiting
     *  a decision), Running (approved/disbursed/being repaid), Pulsed (a genuine legacy status, see
     *  LoanStatus), Completed (fully repaid). Rejected/defaulted loans are excluded - they never became
     *  a real ongoing loan. Deliberately NOT fiscal-year scoped, unlike the other analytics charts:
     *  lifecycle status is a present-moment fact about a loan regardless of when it was originally
     *  granted, and most historical loans (reconstructed by LegacyImportService.importHistoricalLoans)
     *  predate the current fiscal year, so scoping this by applied_at would hide almost all of them. */
    public FiscalYearBreakdown loanLifecycleBreakdown() {
        List<TypeAmount> rows = jdbc.query(
                "SELECT CASE " +
                "         WHEN status = 'PENDING' THEN 'Pending' " +
                "         WHEN status IN ('APPROVED', 'DISBURSED', 'RUNNING') THEN 'Running' " +
                "         WHEN status = 'PULSED' THEN 'Pulsed' " +
                "         WHEN status = 'COMPLETED' THEN 'Completed' " +
                "       END AS stage, COUNT(*) AS amount " +
                "FROM loans WHERE status NOT IN ('REJECTED', 'DEFAULTED') " +
                "GROUP BY 1",
                (rs, i) -> new TypeAmount(rs.getString("stage"), rs.getLong("amount")));

        // Fixed order regardless of what SQL returned, zero-filling gaps.
        Map<String, Long> byStage = rows.stream().collect(Collectors.toMap(TypeAmount::type, TypeAmount::amount));
        List<TypeAmount> slices = List.of("Pending", "Running", "Pulsed", "Completed").stream()
                .map(stage -> new TypeAmount(stage, byStage.getOrDefault(stage, 0L)))
                .collect(Collectors.toList());
        return new FiscalYearBreakdown("all-time", slices);
    }

    public List<StatusCount> memberStatusBreakdown() {
        return jdbc.query(
                "SELECT status, COUNT(*) AS count FROM members GROUP BY status ORDER BY status",
                (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("count")));
    }

    private long queryLong(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
