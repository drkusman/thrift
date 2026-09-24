package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.BankRepository;
import ng.asuu.thrift.repo.LedgerEntryRepository;
import ng.asuu.thrift.repo.LoanRepository;
import ng.asuu.thrift.repo.LoanTypeRepository;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * One-time, re-runnable historical import from the legacy system's CSV exports. Re-running is safe:
 * banks/loan types/members are upserted by their natural key, ledger rows are skipped if their
 * transcode was already imported (see LedgerEntryRepository#existsByTransCode), and historical loans
 * are skipped if their reconstructed loan_code already exists.
 *
 * Legacy data is imported as-is and never recomputed - including monthly savings amounts outside the
 * new ₦20k-₦70k rule (that rule only governs new/changed amounts going forward). Historical Loan rows
 * ARE reconstructed from the ledger (see importHistoricalLoans): despite an earlier assumption here
 * that the loan type couldn't be reliably determined, the legacy transtype column turns out to carry
 * the loan type code directly on every disbursement row (confirmed against the real data), and the
 * loancode column gives a reliable grouping key linking a disbursement to its repayments.
 */
@Service
public class LegacyImportService {
    private static final Set<String> EXCLUDED_BANK_CODES = Set.of("001", "002", "003", "BK1");
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
    };

    private final BankRepository bankRepository;
    private final LoanTypeRepository loanTypeRepository;
    private final MemberRepository memberRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LoanRepository loanRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public LegacyImportService(BankRepository bankRepository, LoanTypeRepository loanTypeRepository,
                                MemberRepository memberRepository, LedgerEntryRepository ledgerEntryRepository,
                                LoanRepository loanRepository, PasswordEncoder passwordEncoder,
                                org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.bankRepository = bankRepository;
        this.loanTypeRepository = loanTypeRepository;
        this.memberRepository = memberRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.loanRepository = loanRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    public static class Summary {
        public int processed, created, updated, skipped;
        public final List<String> errors = new ArrayList<>();
        public String text() {
            return String.format(Locale.UK, "Processed %,d rows: %,d created, %,d updated, %,d skipped.%s",
                    processed, created, updated, skipped, errors.isEmpty() ? "" : " First errors: " + String.join("; ", errors.subList(0, Math.min(10, errors.size()))));
        }
    }

    @Transactional
    public Summary importBanks(String csvText) {
        Summary s = new Summary();
        for (Map<String, String> row : CsvUtil.parseCsvWithHeader(csvText)) {
            s.processed++;
            String code = row.getOrDefault("bankcode", "").trim();
            if (code.isEmpty() || EXCLUDED_BANK_CODES.contains(code.toUpperCase())) { s.skipped++; continue; }
            Bank bank = bankRepository.findByBankCode(code).orElseGet(Bank::new);
            boolean isNew = bank.getId() == null;
            bank.setBankCode(code);
            bank.setName(row.getOrDefault("name", "").trim());
            bank.setSortCode(row.getOrDefault("sortcode", "").trim());
            bankRepository.save(bank);
            if (isNew) s.created++; else s.updated++;
        }
        return s;
    }

    @Transactional
    public Summary importLoanTypes(String csvText) {
        Summary s = new Summary();
        for (Map<String, String> row : CsvUtil.parseCsvWithHeader(csvText)) {
            s.processed++;
            String code = row.getOrDefault("loadcode", row.getOrDefault("loancode", "")).trim();
            if (code.isEmpty()) { s.skipped++; continue; }
            LoanType type = loanTypeRepository.findByCode(code).orElseGet(LoanType::new);
            boolean isNew = type.getId() == null;
            type.setCode(code);
            type.setName(row.getOrDefault("loanname", "").trim());
            type.setInterestRate(parseDouble(row.get("interestrate"), 0));
            String method = row.getOrDefault("interesttime", "AS").trim();
            type.setInterestMethod("BI".equalsIgnoreCase(method) ? InterestMethod.BUILT_IN : InterestMethod.AT_SOURCE);
            type.setMaxDurationMonths((int) parseDouble(row.get("maxduration"), 12));
            loanTypeRepository.save(type);
            if (isNew) s.created++; else s.updated++;
        }
        return s;
    }

    @Transactional
    public Summary importMembers(String csvText) {
        Summary s = new Summary();
        Map<String, Long> bankIdByCode = new HashMap<>();
        for (Bank b : bankRepository.findAll()) bankIdByCode.put(b.getBankCode().toUpperCase(), b.getId());

        for (Map<String, String> row : CsvUtil.parseCsvWithHeader(csvText)) {
            s.processed++;
            String regno = row.getOrDefault("regno", "").trim();
            if (regno.isEmpty()) { s.skipped++; s.errors.add("row " + s.processed + ": blank regno"); continue; }

            Member m = memberRepository.findByRegno(regno).orElseGet(Member::new);
            boolean isNew = m.getId() == null;
            m.setRegno(regno);
            m.setFullName(row.getOrDefault("fullname", "").trim());
            m.setPhone(row.getOrDefault("phone", "").trim());
            m.setEmail(row.getOrDefault("email", "").trim());
            m.setSex(row.getOrDefault("sex", "").trim());
            m.setDeptCode(row.getOrDefault("deptcode", "").trim());
            m.setFactCode(row.getOrDefault("factcode", "").trim());
            m.setPayPoint(row.getOrDefault("pay_point", row.getOrDefault("paypoint", "")).trim());
            String bankCode = row.getOrDefault("bankcode", "").trim().toUpperCase();
            m.setBankId(bankIdByCode.get(bankCode));
            m.setAccountNo(row.getOrDefault("accountno", "").trim());
            m.setStatus(mapStatus(row.getOrDefault("statuscode", "")));
            m.setMonthlySavingsAmount((long) parseDouble(row.get("monthlysavings"), 20000));
            m.setRole(mapRole(row.getOrDefault("role", "")));
            if (isNew) {
                m.setPasswordHash(passwordEncoder.encode(regno));
                m.setMustChangePassword(true);
            }
            memberRepository.save(m);
            if (isNew) s.created++; else s.updated++;
        }
        return s;
    }

    private static final String INSERT_LEDGER_SQL =
            "INSERT INTO ledger_entries (trans_code, member_id, amount, date, description, trans_type, trans_cat, dr_cr_status, source, created_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?, now())";
    private static final int BATCH_SIZE = 1000;

    /**
     * Plain JDBC batch insert rather than JPA saves: at 58k+ rows, going through Hibernate one row at a
     * time (each save() is its own round trip since IDENTITY generation defeats batching, and the
     * persistence context grows unbounded across the whole method) made this take 30+ minutes and get
     * slower as it went. This does one upfront query for already-imported transcodes, then batches inserts.
     */
    @Transactional
    public Summary importLedger(String csvText) {
        Summary s = new Summary();
        Map<String, Long> memberIdByRegno = new HashMap<>();
        for (Member m : memberRepository.findAll()) memberIdByRegno.put(m.getRegno(), m.getId());

        Set<String> existingTransCodes = new HashSet<>(
                jdbcTemplate.queryForList("SELECT trans_code FROM ledger_entries WHERE trans_code IS NOT NULL", String.class));

        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
        for (Map<String, String> row : CsvUtil.parseCsvWithHeader(csvText)) {
            s.processed++;
            String transCode = row.getOrDefault("transcode", "").trim();
            if (transCode.isEmpty()) { s.skipped++; s.errors.add("row " + s.processed + ": blank transcode"); continue; }
            if (!existingTransCodes.add(transCode)) { s.skipped++; continue; }

            String staffId = row.getOrDefault("staffid", "").trim();
            Long memberId = memberIdByRegno.get(staffId);
            if (memberId == null) { s.skipped++; s.errors.add("row " + s.processed + ": unknown staffid " + staffId); continue; }

            LocalDate date = parseDate(row.get("date"));
            if (date == null) { s.skipped++; s.errors.add("row " + s.processed + ": unparsable date " + row.get("date")); continue; }

            long amount = Math.round(parseDouble(row.get("amount"), 0));
            String rawCat = row.getOrDefault("transcat", "").trim();
            String rawDrCr = row.getOrDefault("drcrstatus", "").trim();

            batch.add(new Object[]{
                    transCode, memberId, amount, java.sql.Date.valueOf(date),
                    row.getOrDefault("description", "").trim(), row.getOrDefault("transtype", "").trim(),
                    mapTransCat(rawCat).name(), ("DR".equalsIgnoreCase(rawDrCr) ? DrCr.DR : DrCr.CR).name(),
                    LedgerSource.LEGACY_IMPORT.name(),
            });
            s.created++;

            if (batch.size() >= BATCH_SIZE) {
                jdbcTemplate.batchUpdate(INSERT_LEDGER_SQL, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbcTemplate.batchUpdate(INSERT_LEDGER_SQL, batch);
        return s;
    }

    private static final String INSERT_LOAN_SQL =
            "INSERT INTO loans (loan_code, member_id, loan_type_id, requested_amount, interest_amount, " +
            "disbursed_amount, total_repayable, status, applied_at, disbursed_at, decision_note) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Reconstructs a historical Loan row per distinct legacy loan (grouped by the ledger's `loancode`
     * column, e.g. "A00436MNL-2022-04") from its disbursement row(s), then links every ledger row that
     * belongs to that loan - the disbursement(s) and any matching repayments - by setting their loan_id.
     * Run this AFTER importLedger (it needs members already imported, and works off the same CSV).
     * Must be re-run-safe: skips any loancode whose reconstructed loan_code already exists.
     */
    @Transactional
    public Summary importHistoricalLoans(String csvText) {
        Summary s = new Summary();
        Map<String, Long> memberIdByRegno = new HashMap<>();
        for (Member m : memberRepository.findAll()) memberIdByRegno.put(m.getRegno(), m.getId());
        Map<String, Long> loanTypeIdByCode = new HashMap<>();
        for (LoanType t : loanTypeRepository.findAll()) loanTypeIdByCode.put(t.getCode().toUpperCase(), t.getId());
        Set<String> existingLoanCodes = new HashSet<>(
                jdbcTemplate.queryForList("SELECT loan_code FROM loans WHERE loan_code IS NOT NULL", String.class));

        List<Map<String, String>> rows = CsvUtil.parseCsvWithHeader(csvText);

        Map<String, List<Map<String, String>>> grantsByLoanCode = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String transType = row.getOrDefault("transtype", "").trim().toUpperCase();
            String drCr = row.getOrDefault("drcrstatus", "").trim().toUpperCase();
            String loanCode = row.getOrDefault("loancode", "").trim();
            if (drCr.equals("DR") && loanTypeIdByCode.containsKey(transType) && !loanCode.isEmpty()) {
                grantsByLoanCode.computeIfAbsent(loanCode, k -> new ArrayList<>()).add(row);
            }
        }

        Map<String, List<String>> repaymentTransCodesByLoanCode = new HashMap<>();
        for (Map<String, String> row : rows) {
            if (!"LRPT".equalsIgnoreCase(row.getOrDefault("transtype", "").trim())) continue;
            String loanCode = row.getOrDefault("loancode", "").trim();
            String transCode = row.getOrDefault("transcode", "").trim();
            if (loanCode.isEmpty() || transCode.isEmpty()) continue;
            repaymentTransCodesByLoanCode.computeIfAbsent(loanCode, k -> new ArrayList<>()).add(transCode);
        }

        for (Map.Entry<String, List<Map<String, String>>> entry : grantsByLoanCode.entrySet()) {
            s.processed++;
            String csvLoanCode = entry.getKey();
            String legacyLoanCode = "LEGACY-" + csvLoanCode;
            if (existingLoanCodes.contains(legacyLoanCode)) { s.skipped++; continue; }

            List<Map<String, String>> group = entry.getValue();
            Map<String, String> first = group.get(0);
            String staffId = first.getOrDefault("staffid", "").trim();
            Long memberId = memberIdByRegno.get(staffId);
            if (memberId == null) { s.skipped++; s.errors.add("loan " + csvLoanCode + ": unknown staffid " + staffId); continue; }

            String typeCode = first.getOrDefault("transtype", "").trim().toUpperCase();
            Long loanTypeId = loanTypeIdByCode.get(typeCode);
            if (loanTypeId == null) { s.skipped++; s.errors.add("loan " + csvLoanCode + ": unknown loan type " + typeCode); continue; }

            long disbursedAmount = 0, requestedAmount = 0, interestAmount = 0;
            LocalDate earliest = null;
            String runningStatus = null;
            for (Map<String, String> r : group) {
                disbursedAmount += Math.round(parseDouble(r.get("amount"), 0));
                requestedAmount += Math.round(parseDouble(r.get("loanamount"), 0));
                interestAmount += Math.round(parseDouble(r.get("loaninterest"), 0));
                LocalDate d = parseDate(r.get("date"));
                if (d != null && (earliest == null || d.isBefore(earliest))) earliest = d;
                String rs = r.getOrDefault("runningstatus", "").trim();
                if (!rs.isEmpty()) runningStatus = rs;
            }
            if (earliest == null) earliest = LocalDate.now();
            LoanStatus status = mapLoanRunningStatus(runningStatus);
            Timestamp appliedAt = Timestamp.valueOf(earliest.atStartOfDay());

            final long finalRequested = requestedAmount, finalInterest = interestAmount, finalDisbursed = disbursedAmount;
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                // Postgres's driver returns every column (not just the PK) for a bare
                // RETURN_GENERATED_KEYS statement, which trips up KeyHolder#getKey() when there's more
                // than one column - naming the key column explicitly limits it to just "id".
                var ps = con.prepareStatement(INSERT_LOAN_SQL, new String[]{"id"});
                ps.setString(1, legacyLoanCode);
                ps.setLong(2, memberId);
                ps.setLong(3, loanTypeId);
                ps.setLong(4, finalRequested);
                ps.setLong(5, finalInterest);
                ps.setLong(6, finalDisbursed);
                ps.setLong(7, finalRequested + finalInterest);
                ps.setString(8, status.name());
                ps.setTimestamp(9, appliedAt);
                ps.setTimestamp(10, appliedAt);
                ps.setString(11, "Reconstructed from legacy ledger (loan code: " + csvLoanCode + ")");
                return ps;
            }, keyHolder);
            Long loanId = keyHolder.getKey().longValue();

            List<String> transCodesToLink = new ArrayList<>();
            for (Map<String, String> r : group) {
                String tc = r.getOrDefault("transcode", "").trim();
                if (!tc.isEmpty()) transCodesToLink.add(tc);
            }
            transCodesToLink.addAll(repaymentTransCodesByLoanCode.getOrDefault(csvLoanCode, List.of()));
            if (!transCodesToLink.isEmpty()) {
                List<Object[]> linkBatch = new ArrayList<>(transCodesToLink.size());
                for (String tc : transCodesToLink) linkBatch.add(new Object[]{loanId, tc});
                jdbcTemplate.batchUpdate("UPDATE ledger_entries SET loan_id = ? WHERE trans_code = ?", linkBatch);
            }
            s.created++;
        }
        return s;
    }

    /**
     * Backfills monthly_repayment_amount on RUNNING legacy loans - importHistoricalLoans never set it,
     * so every reconstructed loan's dashboard "loan payments" figure reads as zero until this runs.
     * Derives the CURRENT installment rate from actual repayment history: a legacy loan's rate can
     * change over time (renegotiated, or a member catching up after a gap), so if the most recent 2-3
     * non-zero repayments agree on one amount, that wins over older history; otherwise the most common
     * non-zero repayment amount is used. Zero-amount ledger rows (adjustments, not real repayments) are
     * ignored either way. Only fills loans still NULL, so it's safe to re-run as new repayments post.
     */
    @Transactional
    public Summary backfillLegacyMonthlyRepayments() {
        Summary s = new Summary();
        for (Loan loan : loanRepository.findByStatusOrderByAppliedAtAsc(LoanStatus.RUNNING)) {
            if (loan.getMonthlyRepaymentAmount() != null) continue;
            s.processed++;
            List<Long> mostRecentFirst = new ArrayList<>();
            for (LedgerEntry e : ledgerEntryRepository.findByLoanIdOrderByDateAsc(loan.getId())) {
                if (e.getDrCrStatus() == DrCr.CR && e.getAmount() > 0) mostRecentFirst.add(e.getAmount());
            }
            Collections.reverse(mostRecentFirst);
            if (mostRecentFirst.isEmpty()) { s.skipped++; continue; }
            loan.setMonthlyRepaymentAmount(resolveMonthlyRate(mostRecentFirst));
            loanRepository.save(loan);
            s.updated++;
        }
        return s;
    }

    private static Long resolveMonthlyRate(List<Long> repaymentsMostRecentFirst) {
        int n = Math.min(3, repaymentsMostRecentFirst.size());
        List<Long> recent = repaymentsMostRecentFirst.subList(0, n);
        if (recent.size() >= 2 && new HashSet<>(recent).size() == 1) {
            return recent.get(0);
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Long amount : repaymentsMostRecentFirst) counts.merge(amount, 1L, Long::sum);
        return counts.entrySet().stream()
                .max(Map.Entry.<Long, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static LoanStatus mapLoanRunningStatus(String raw) {
        if (raw == null) return LoanStatus.RUNNING;
        return switch (raw.trim().toLowerCase()) {
            case "running" -> LoanStatus.RUNNING;
            case "complete", "completed" -> LoanStatus.COMPLETED;
            case "pulses", "pulsed" -> LoanStatus.PULSED;
            default -> LoanStatus.RUNNING;
        };
    }

    private static MemberStatus mapStatus(String raw) {
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "active" -> MemberStatus.ACTIVE;
            case "withdrawn", "resigned" -> MemberStatus.WITHDRAWN;
            case "retired" -> MemberStatus.RETIRED;
            case "deceased" -> MemberStatus.DECEASED;
            case "inactive" -> MemberStatus.INACTIVE;
            default -> MemberStatus.ACTIVE;
        };
    }

    private static MemberRole mapRole(String raw) {
        String v = raw.trim().toUpperCase().replace("_", " ");
        return "FIN SEC".equals(v) ? MemberRole.FIN_SEC : MemberRole.MEMBER;
    }

    private static TransCat mapTransCat(String raw) {
        String v = raw.trim().toUpperCase();
        if (v.equals("SAVINGS")) return TransCat.SAVINGS;
        if (v.equals("LOAN") || v.equals("DISBUR")) return TransCat.LOAN;
        if (v.equals("INT") || v.equals("INTEREST")) return TransCat.INTEREST;
        if (v.contains("FEE")) return TransCat.FEES;
        return TransCat.OTHER;
    }

    private static double parseDouble(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try { return Double.parseDouble(raw.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(v, f); } catch (Exception ignored) {}
        }
        return null;
    }
}
