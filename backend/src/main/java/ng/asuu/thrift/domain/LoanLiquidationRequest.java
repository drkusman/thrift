package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member's self-service request to liquidate their own loan - approving it runs the same
 *  LoanLiquidationService.liquidate() an admin walk-in liquidation does. See LoanLiquidationService. */
@Entity @Table(name = "loan_liquidation_requests") @Getter @Setter
public class LoanLiquidationRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "loan_id", nullable = false) private Long loanId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    /** Set once approved - the audit record the actual liquidate() call produced. */
    @Column(name = "loan_liquidation_id") private Long loanLiquidationId;

    public enum RequestStatus { PENDING, APPROVED, REJECTED }
}
