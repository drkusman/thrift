package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member's self-service request to withdraw from the cooperative entirely - approving it runs the
 *  same MembershipWithdrawalService.withdraw() an admin-initiated withdrawal does. See
 *  MembershipWithdrawalService. */
@Entity @Table(name = "membership_withdrawal_requests") @Getter @Setter
public class MembershipWithdrawalRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    /** Set once approved - the audit record the actual withdraw() call produced. */
    @Column(name = "membership_withdrawal_id") private Long membershipWithdrawalId;

    public enum RequestStatus { PENDING, APPROVED, REJECTED }
}
