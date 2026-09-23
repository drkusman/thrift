package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.BankRepository;
import ng.asuu.thrift.repo.LedgerEntryRepository;
import ng.asuu.thrift.repo.LoanTypeRepository;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * One-time, re-runnable historical import from the legacy system's CSV exports. Re-running is safe:
 * banks/loan types/members are upserted by their natural key, and ledger rows are skipped if their
 * transcode was already imported (see LedgerEntryRepository#existsByTransCode).
 *
 * Legacy data is imported as-is and never recomputed - including monthly savings amounts outside the
 * new ₦20k-₦70k rule (that rule only governs new/changed amounts going forward), and legacy loan
 * columns (loanamount/loaninterest/loanrepayplan/loancode) are NOT used to fabricate Loan/repayment-
 * schedule records here: the ledger.csv gives no reliable signal for which LoanType (EML/MNL/PDL) a
 * historical loan was, and guessing would put an incorrect FK into the loans table. Ledger rows import
 * losslessly either way (that's the actual ask); loan_id is simply left null on legacy rows.
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
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public LegacyImportService(BankRepository bankRepository, LoanTypeRepository loanTypeRepository,
                                MemberRepository memberRepository, LedgerEntryRepository ledgerEntryRepository,
                                PasswordEncoder passwordEncoder, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.bankRepository = bankRepository;
        this.loanTypeRepository = loanTypeRepository;
        this.memberRepository = memberRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
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
