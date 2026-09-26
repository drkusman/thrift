package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member's request to be paid out their unpaid interest-on-savings (see IosPayoutService for how
 *  requestedAmount is computed - never typed in by the member or admin). PENDING until an admin either
 *  rejects it, or marks it PAID once they've paid the member externally and posted the matching IOS2
 *  debit through the monthly contribution upload. */
@Entity @Table(name = "ios_payout_requests") @Getter @Setter
public class IosPayoutRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;

    public enum RequestStatus { PENDING, PAID, REJECTED }
}
