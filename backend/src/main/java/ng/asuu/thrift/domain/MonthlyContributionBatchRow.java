package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "monthly_contribution_batch_rows") @Getter @Setter
public class MonthlyContributionBatchRow {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_id", nullable = false) private Long batchId;
    @Column(nullable = false) private String regno;
    @Column(nullable = false) private long amount;
    @Column(name = "kind") private String kind; // "SAVINGS" or "LOAN_REPAYMENT"
    @Column(nullable = false) private boolean matched;
    @Column(name = "error_message") private String errorMessage;
}
