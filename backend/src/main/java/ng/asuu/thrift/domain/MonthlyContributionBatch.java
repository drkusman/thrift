package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Table(name = "monthly_contribution_batches") @Getter @Setter
public class MonthlyContributionBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "period_month", nullable = false) private String periodMonth; // e.g. "2026-09"
    @Column(name = "file_name") private String fileName;
    @Column(name = "uploaded_by", nullable = false) private Long uploadedBy;
    @Column(name = "uploaded_at", nullable = false) private LocalDateTime uploadedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "total_rows") private int totalRows;
    @Column(name = "matched_rows") private int matchedRows;
    @Column(name = "total_amount") private long totalAmount;
    /** The original workbook as uploaded, so an admin can re-download exactly what was submitted. */
    @Column(name = "file_bytes") private byte[] fileBytes;
    /** True if any row in this batch went through the SAVINGS waterfall or a LOAN_REPAYMENT posting -
     *  the only kinds that can't safely be re-run for the same period. IOS1/IOS2/Cash Deposit/Refund of
     *  Over Deduction never set this, so their periods stay open for further uploads of the same kind. */
    @Column(name = "locks_period", nullable = false) private boolean locksPeriod = true;
}
