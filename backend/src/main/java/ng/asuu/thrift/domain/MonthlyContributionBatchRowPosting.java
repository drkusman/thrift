package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** One ledger posting a batch row resulted in - a SAVINGS row can produce several (its own monthly
 *  savings, one or more loan repayments, and a Refund of Over Deduction), each reversible on its own. */
@Entity @Table(name = "monthly_contribution_batch_row_postings") @Getter @Setter
public class MonthlyContributionBatchRowPosting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "batch_row_id", nullable = false) private Long batchRowId;
    @Column(name = "ledger_entry_id", nullable = false) private Long ledgerEntryId;
    @Column(name = "schedule_id") private Long scheduleId;
    @Column(nullable = false) private long amount;
}
