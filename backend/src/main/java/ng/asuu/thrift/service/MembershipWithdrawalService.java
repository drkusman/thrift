package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.domain.MembershipWithdrawal;
import ng.asuu.thrift.domain.MembershipWithdrawalRequest;
import ng.asuu.thrift.domain.MembershipWithdrawalRequest.RequestStatus;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.repo.MembershipWithdrawalRepository;
import ng.asuu.thrift.repo.MembershipWithdrawalRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A membership withdrawal pays a member out and closes their account for good - Karim's own rule: every
 * loan must be liquidated to zero first (see LoanLiquidationService), then a COT (cost) of
 * balance * 5 / 1000 is deducted from what's left of their savings, and the remainder is paid out. Two
 * ledger entries post together: the COT (transType COT, DR) and the payout (transType WDRL, DR) - together
 * they drain the member's savings to exactly zero, since by the time this runs totalLoan is 0 and balance
 * equals their savings in full. The member's own status then flips to WITHDRAWN, which - via
 * MemberPrincipal - locks them out of logging in entirely, and drops them out of every active-member
 * picker, guarantor eligibility check, and future remittance schedule elsewhere in the app.
 * <p>
 * Like loan liquidation, this can be triggered directly by an admin, or via a member's own self-service
 * request that an admin later approves - approving just calls withdraw() under the hood.
 */
@Service
public class MembershipWithdrawalService {
    private final MemberRepository memberRepository;
    private final LedgerService ledgerService;
    private final LoanService loanService;
    private final MembershipWithdrawalRepository withdrawalRepository;
    private final MembershipWithdrawalRequestRepository requestRepository;

    public MembershipWithdrawalService(MemberRepository memberRepository, LedgerService ledgerService,
                                        LoanService loanService, MembershipWithdrawalRepository withdrawalRepository,
                                        MembershipWithdrawalRequestRepository requestRepository) {
        this.memberRepository = memberRepository;
        this.ledgerService = ledgerService;
        this.loanService = loanService;
        this.withdrawalRepository = withdrawalRepository;
        this.requestRepository = requestRepository;
    }

    /** A read-only preview of what withdrawing this member right now would look like. canWithdraw is
     *  false whenever totalLoan is still positive - every loan must be liquidated to zero first. */
    public record Summary(long totalSavings, long totalLoan, long balance, long cot, long withdrawableAmount,
                           boolean canWithdraw) {}

    public Summary summary(Long memberId) {
        long totalSavings = ledgerService.savingsBalance(memberId);
        long totalLoan = loanService.outstandingBalance(memberId);
        long balance = totalSavings - totalLoan;
        long cot = cotFor(balance);
        long withdrawable = balance - cot;
        return new Summary(totalSavings, totalLoan, balance, cot, withdrawable, totalLoan <= 0);
    }

    private static long cotFor(long balance) {
        return (balance * 5) / 1000;
    }

    @Transactional
    public MembershipWithdrawal withdraw(Member admin, Long memberId) {
        Member member = requireActiveMember(memberId);
        Summary s = summary(memberId);
        if (!s.canWithdraw()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This member still has an outstanding loan balance of " + s.totalLoan() +
                    " - liquidate every loan first");
        }
        if (s.balance() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This member has no savings balance to withdraw");
        }

        LocalDate today = LocalDate.now();
        var cotEntry = ledgerService.post(memberId, s.cot(), today, "Membership Withdrawal COT", "COT",
                TransCat.SAVINGS, DrCr.DR, null, LedgerSource.MANUAL_ADMIN, admin.getId());
        var payoutEntry = ledgerService.post(memberId, s.withdrawableAmount(), today, "Membership Withdrawal Payout", "WDRL",
                TransCat.SAVINGS, DrCr.DR, null, LedgerSource.MANUAL_ADMIN, admin.getId());

        member.setStatus(MemberStatus.WITHDRAWN);
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);

        MembershipWithdrawal audit = new MembershipWithdrawal();
        audit.setMemberId(memberId);
        audit.setTotalSavings(s.totalSavings());
        audit.setTotalLoan(s.totalLoan());
        audit.setBalance(s.balance());
        audit.setCot(s.cot());
        audit.setWithdrawableAmount(s.withdrawableAmount());
        audit.setCotLedgerEntryId(cotEntry.getId());
        audit.setPayoutLedgerEntryId(payoutEntry.getId());
        audit.setPerformedBy(admin.getId());
        return withdrawalRepository.save(audit);
    }

    public List<MembershipWithdrawalRequest> pendingRequests() {
        return requestRepository.findByStatusOrderByRequestedAtAsc(RequestStatus.PENDING);
    }

    public List<MembershipWithdrawalRequest> requestsForMember(Long memberId) {
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(memberId);
    }

    /** A member requesting their own withdrawal - unlike withdraw() itself, this doesn't require every
     *  loan to already be clear, since the realistic order is: member asks to leave, then the admin
     *  liquidates whatever loans remain before finally approving. */
    @Transactional
    public MembershipWithdrawalRequest apply(Member member) {
        requireActiveMember(member.getId());
        if (!requestRepository.findByMemberIdAndStatus(member.getId(), RequestStatus.PENDING).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a withdrawal request awaiting a decision");
        }
        MembershipWithdrawalRequest req = new MembershipWithdrawalRequest();
        req.setMemberId(member.getId());
        return requestRepository.save(req);
    }

    @Transactional
    public MembershipWithdrawalRequest approve(Member admin, Long requestId) {
        MembershipWithdrawalRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        MembershipWithdrawal audit = withdraw(admin, req.getMemberId());
        req.setStatus(RequestStatus.APPROVED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        req.setMembershipWithdrawalId(audit.getId());
        return requestRepository.save(req);
    }

    @Transactional
    public MembershipWithdrawalRequest reject(Member admin, Long requestId, String note) {
        MembershipWithdrawalRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        req.setStatus(RequestStatus.REJECTED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        req.setDecisionNote(note);
        return requestRepository.save(req);
    }

    private MembershipWithdrawalRequest requireRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private Member requireActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (!member.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This member is not active");
        }
        return member;
    }
}
