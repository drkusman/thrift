package ng.asuu.thrift.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The cooperative's own income for a fiscal year (1 Nov - 31 Oct), broken into the three sources that
 * actually generate income rather than just moving money between members: interest booked on loans at
 * disbursement (Loan.interestAmount - fixed once, at approval, per LoanService.approve() - see its own
 * doc comment for the AS/BI formulas), split by loan type; the flat admin fee on loan liquidations
 * (LoanLiquidationService, ledger transType FEES); and the COT deducted on membership withdrawals
 * (MembershipWithdrawalService, transType COT). All three date ranges (like AdminAnalyticsService's
 * charts) include legacy-imported data that falls in the range, not just activity from this app's own
 * new features - the legacy system posted real FEES/COT charges too, and those are just as much real
 * income for the year as anything posted since.
 * <p>
 * Everything is parameterized by the fiscal year's own start date (forFiscalYear(since)) rather than
 * hardcoded to "now", so the same logic produces the current year's figures and the previous year's for a
 * side-by-side comparison - currentFiscalYear() and previousFiscalYear() are just forFiscalYear() called
 * with two different starting points.
 * <p>
 * Every total here is drill-down-able: interestByLoanType() and interestLoans() back the "Interest on
 * loans" split, and ledgerTransactions() backs the FEES/COT totals - each returning the exact rows that
 * were summed, so an admin can trace a total back to the individual loans or postings behind it.
 */
@Service
public class IncomeReportService {
    private final JdbcTemplate jdbc;

    public IncomeReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TypeAmount(String type, long amount) {}

    public record Income(String fiscalYearLabel, LocalDate since, LocalDate until, long interestOnLoans,
                          List<TypeAmount> interestByLoanType, long liquidationFees, long withdrawalCot, long total) {}

    public record LoanInterestRow(String regno, String fullName, String loanCode, String loanType,
                                   String disbursedAt, long interestAmount) {}

    public record TransactionRow(String regno, String fullName, String date, String description, long amount) {}

    public Income currentFiscalYear() {
        return forFiscalYear(currentFiscalYearStart());
    }

    public Income previousFiscalYear() {
        return forFiscalYear(currentFiscalYearStart().minusYears(1));
    }

    public Income forFiscalYear(LocalDate since) {
        LocalDate until = since.plusYears(1);
        List<TypeAmount> byType = interestByLoanType(since, until);
        long interest = byType.stream().mapToLong(TypeAmount::amount).sum();
        long liquidationFees = queryLong(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE trans_type = 'FEES' AND date >= ? AND date < ?",
                since, until);
        long withdrawalCot = queryLong(
                "SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE trans_type = 'COT' AND date >= ? AND date < ?",
                since, until);

        return new Income(fiscalYearLabel(since), since, until.minusDays(1),
                interest, byType, liquidationFees, withdrawalCot, interest + liquidationFees + withdrawalCot);
    }

    /** Interest booked at disbursement in the given fiscal year, grouped by loan type - largest first. */
    public List<TypeAmount> interestByLoanType(LocalDate since, LocalDate until) {
        return jdbc.query(
                "SELECT COALESCE(lt.name, 'Other') AS type, SUM(l.interest_amount) AS amount " +
                "FROM loans l LEFT JOIN loan_types lt ON lt.id = l.loan_type_id " +
                "WHERE l.disbursed_at::date >= ? AND l.disbursed_at::date < ? " +
                "GROUP BY 1 ORDER BY 2 DESC",
                (rs, i) -> new TypeAmount(rs.getString("type"), rs.getLong("amount")),
                since, until);
    }

    /** The individual loans behind interestByLoanType()'s totals for the fiscal year starting `since`,
     *  optionally narrowed to a single loan type (pass null for every type at once). */
    public List<LoanInterestRow> interestLoans(LocalDate since, String loanType) {
        LocalDate until = since.plusYears(1);
        StringBuilder sql = new StringBuilder(
                "SELECT m.regno, m.full_name, l.loan_code, COALESCE(lt.name, 'Other') AS loan_type, " +
                "l.disbursed_at, l.interest_amount " +
                "FROM loans l JOIN members m ON m.id = l.member_id LEFT JOIN loan_types lt ON lt.id = l.loan_type_id " +
                "WHERE l.disbursed_at::date >= ? AND l.disbursed_at::date < ?");
        List<Object> args = new ArrayList<>(List.of(since, until));
        if (loanType != null && !loanType.isBlank()) {
            sql.append(" AND COALESCE(lt.name, 'Other') = ?");
            args.add(loanType);
        }
        sql.append(" ORDER BY l.disbursed_at DESC");
        return jdbc.query(sql.toString(),
                (rs, i) -> new LoanInterestRow(rs.getString("regno"), rs.getString("full_name"),
                        rs.getString("loan_code"), rs.getString("loan_type"),
                        rs.getTimestamp("disbursed_at").toLocalDateTime().toLocalDate().toString(),
                        rs.getLong("interest_amount")),
                args.toArray());
    }

    /** The individual ledger postings behind a FEES or COT total for the fiscal year starting `since`. */
    public List<TransactionRow> ledgerTransactions(LocalDate since, String transType) {
        LocalDate until = since.plusYears(1);
        return jdbc.query(
                "SELECT m.regno, m.full_name, e.date, e.description, e.amount " +
                "FROM ledger_entries e JOIN members m ON m.id = e.member_id " +
                "WHERE e.trans_type = ? AND e.date >= ? AND e.date < ? ORDER BY e.date DESC",
                (rs, i) -> new TransactionRow(rs.getString("regno"), rs.getString("full_name"),
                        rs.getDate("date").toLocalDate().toString(), rs.getString("description"), rs.getLong("amount")),
                transType, since, until);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** The thrift's fiscal year runs 1 November to 31 October; returns the start date of the one
     *  containing today. Mirrors AdminAnalyticsService.currentFiscalYearRange(). */
    private static LocalDate currentFiscalYearStart() {
        LocalDate today = LocalDate.now();
        int startYear = today.getMonthValue() >= 11 ? today.getYear() : today.getYear() - 1;
        return LocalDate.of(startYear, 11, 1);
    }

    private static String fiscalYearLabel(LocalDate since) {
        return since.getYear() + "/" + (since.getYear() + 1);
    }
}
