package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.BadDebt;
import ng.asuu.thrift.domain.BadDebtStatus;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.BadDebtRepository;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Members who withdrew from the cooperative still owing money (see MembershipWithdrawalService) -
 *  tracked here so admins can find them again and record repayments as they trickle in, even though
 *  the member's own account is closed (WITHDRAWN) and they can no longer log in themselves. */
@Service
public class BadDebtService {
    private final BadDebtRepository badDebtRepository;
    private final MemberRepository memberRepository;
    private final LedgerService ledgerService;

    public BadDebtService(BadDebtRepository badDebtRepository, MemberRepository memberRepository, LedgerService ledgerService) {
        this.badDebtRepository = badDebtRepository;
        this.memberRepository = memberRepository;
        this.ledgerService = ledgerService;
    }

    /** Called from MembershipWithdrawalService.withdraw() when a member's balance comes out negative -
     *  no ledger entry is posted here, the negative balance is simply whatever their history already
     *  says; this just records that fact so they can be found again later. */
    @Transactional
    public void record(Member member, Long membershipWithdrawalId, long owedAmount) {
        BadDebt debt = new BadDebt();
        debt.setMemberId(member.getId());
        debt.setMembershipWithdrawalId(membershipWithdrawalId);
        debt.setOriginalAmount(owedAmount);
        badDebtRepository.save(debt);
        member.setBadDebt(true);
        memberRepository.save(member);
    }

    public record Row(Long badDebtId, Long memberId, String regno, String fullName, long originalAmount,
                       long currentlyOwed, String createdAt) {}

    /** currentlyOwed is read live from the ledger rather than from originalAmount, since repayments
     *  post as ordinary ledger entries and may not be the only activity on the account since withdrawal. */
    public List<Row> outstanding() {
        return badDebtRepository.findByStatusOrderByCreatedAtAsc(BadDebtStatus.OUTSTANDING).stream()
                .map(d -> {
                    Member m = memberRepository.findById(d.getMemberId()).orElse(null);
                    long owed = Math.max(0, -ledgerService.savingsBalance(d.getMemberId()));
                    return new Row(d.getId(), d.getMemberId(), m == null ? "?" : m.getRegno(),
                            m == null ? "Unknown member" : m.getFullName(), d.getOriginalAmount(), owed,
                            d.getCreatedAt().toString());
                })
                .toList();
    }

    /** Records a repayment as an ordinary SAVINGS ledger CR entry against the member (same mechanism as
     *  any other credit to their account), then checks whether that's brought their balance back to zero
     *  or above - if so, the debt is cleared and they come off the outstanding list for good. */
    @Transactional
    public BadDebt recordRepayment(Member admin, Long badDebtId, long amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repayment amount must be greater than zero");
        }
        BadDebt debt = badDebtRepository.findById(badDebtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bad debt record not found"));
        if (debt.getStatus() != BadDebtStatus.OUTSTANDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This debt is already cleared");
        }
        Member member = memberRepository.findById(debt.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        ledgerService.post(member.getId(), amount, LocalDate.now(), "Bad debt repayment", "BDRP",
                TransCat.SAVINGS, DrCr.CR, null, LedgerSource.MANUAL_ADMIN, admin.getId());

        if (ledgerService.savingsBalance(member.getId()) >= 0) {
            debt.setStatus(BadDebtStatus.CLEARED);
            debt.setClearedAt(LocalDateTime.now(ZoneOffset.UTC));
            member.setBadDebt(false);
            memberRepository.save(member);
        }
        return badDebtRepository.save(debt);
    }
}
