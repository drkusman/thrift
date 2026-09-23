package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** The unified financial ledger, mirroring the legacy ledger.csv shape. transType is kept as free text
 *  (not a strict enum) because the legacy data is inconsistent (bank names, fee descriptions, etc. leak
 *  into it) - see LegacyImportService. All savings postings, loan disbursements, and repayments flow
 *  through here; running balances are computed by summing CR/DR amounts per member. */
@Entity @Table(name = "ledger_entries") @Getter @Setter
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "trans_code", unique = true) private String transCode;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private long amount;
    @Column(nullable = false) private LocalDate date;
    private String description;
    @Column(name = "trans_type") private String transType;
    @Enumerated(EnumType.STRING) @Column(name = "trans_cat", nullable = false) private TransCat transCat;
    @Enumerated(EnumType.STRING) @Column(name = "dr_cr_status", nullable = false) private DrCr drCrStatus;
    @Column(name = "loan_id") private Long loanId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LedgerSource source;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "created_by") private Long createdBy;

    public enum TransCat { SAVINGS, LOAN, INTEREST, FEES, OTHER }
    public enum DrCr { DR, CR }
    public enum LedgerSource { LEGACY_IMPORT, MONTHLY_UPLOAD, LOAN_DISBURSEMENT, LOAN_REPAYMENT, MANUAL_ADMIN }
}
