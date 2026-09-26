package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member's request to be paid out one specific unpaid IOS1 credit (see LedgerService.unpaidIosCredits
 *  and IosPayoutService for how requestedAmount/sourceLedgerEntryId are chosen - never typed in by the
 *  member or admin, and never a blended total of several years' interest). PENDING until an admin either
 *  rejects it, or marks it PAID once they've paid the member externally and posted the matching IOS2
 *  debit through the monthly contribution upload. */
@Entity @Table(name = "ios_payout_requests") @Getter @Setter
public class IosPayoutRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    /** The specific IOS1 ledger entry this request claims against - lets a member apply for one year's
     *  interest at a time instead of a combined lump sum. */
    @Column(name = "source_ledger_entry_id") private Long sourceLedgerEntryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RequestStatus status = RequestStatus.PENDING;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;

    public enum RequestStatus { PENDING, PAID, REJECTED }
}
