package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every active member's savings and loan balance as they stood at the close of a given date (Karim's own
 * words: "the system runs all transactions from the beginning of the thrift records and end at the
 * selected date") - e.g. a fiscal year-end like 2026-10-31, for a year-end membership report. Computed as
 * one bulk SQL aggregate over every ledger entry up to and including that date, grouped by member, rather
 * than the usual per-member Java loop (LedgerService.savingsBalance/LoanService.outstandingBalance) - with
 * 600+ members that would mean 600+ separate full-history scans for a single report.
 */
@Service
public class MemberBalanceReportService {
    private final JdbcTemplate jdbc;
    private final MemberRepository memberRepository;

    public MemberBalanceReportService(JdbcTemplate jdbc, MemberRepository memberRepository) {
        this.jdbc = jdbc;
        this.memberRepository = memberRepository;
    }

    public record Row(Long memberId, String regno, String fullName, long savingsBalance, long loanBalance, long netEquity) {}

    public List<Row> report(LocalDate asOfDate) {
        Map<Long, long[]> sumsByMember = new HashMap<>();
        jdbc.query(
                "SELECT member_id, " +
                "SUM(CASE WHEN trans_cat = 'SAVINGS' AND dr_cr_status = 'CR' THEN amount " +
                "         WHEN trans_cat = 'SAVINGS' AND dr_cr_status = 'DR' THEN -amount ELSE 0 END) AS savings, " +
                "SUM(CASE WHEN trans_cat = 'LOAN' AND dr_cr_status = 'DR' THEN amount " +
                "         WHEN trans_cat = 'LOAN' AND dr_cr_status = 'CR' THEN -amount ELSE 0 END) AS loan " +
                "FROM ledger_entries WHERE date <= ? GROUP BY member_id",
                rs -> {
                    sumsByMember.put(rs.getLong("member_id"), new long[]{rs.getLong("savings"), rs.getLong("loan")});
                },
                asOfDate);

        List<Row> rows = new java.util.ArrayList<>();
        for (Member m : memberRepository.findByStatusOrderByFullNameAsc(MemberStatus.ACTIVE)) {
            long[] sums = sumsByMember.getOrDefault(m.getId(), new long[]{0, 0});
            rows.add(new Row(m.getId(), m.getRegno(), m.getFullName(), sums[0], sums[1], sums[0] - sums[1]));
        }
        return rows;
    }
}
