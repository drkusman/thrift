package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member's requested new monthly savings amount. Amounts <= 70,000 auto-apply immediately (see
 *  SavingsService); amounts above that sit PENDING until an admin approves or rejects them. */
@Entity @Table(name = "savings_amount_change_requests") @Getter @Setter
public class SavingsAmountChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;

    public enum RequestStatus { PENDING, APPROVED, REJECTED }
}
