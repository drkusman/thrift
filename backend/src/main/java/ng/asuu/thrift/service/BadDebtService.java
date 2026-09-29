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
 *  the member's own account is closed (WITHDRAWN) and they can no longer log in themselves. The amount
 *  still owed is read live off the ledger under TransCat.BAD_DEBT (see owedNow()) - the same pattern
 *  SAVINGS/LOAN use for their own running balances - rather than kept as a separate counter, so this
 *  debt and its paydown show up in the member's own transaction history like everything else. */
@Service
public class BadDebtService {
    private final BadDebtRepository badDebtRepository;
    private final MemberRepository memberRepository;
    private final LedgerService ledgerService;

    public BadDebtService(BadDebtRepository badDebtRepository, MemberRepository memberRepository,
            LedgerService ledgerService) {
        this.badDebtRepository = badDebtRepository;
        this.memberRepository = memberRepository;
        this.ledgerService = ledgerService;
    }

    /** Sum of DR (debt raised) minus CR (repayments) BAD_DEBT-category postings - same DR-minus-CR
     *  convention LoanService.outstandingBalance() uses for LOAN postings. */
    private long owedNow(Long memberId) {
        long balance = 0;
        for (var e : ledgerService.history(memberId)) {
            if (e.getTransCat() != TransCat.BAD_DEBT) continue;
            balance += e.getDrCrStatus() == DrCr.DR ? e.getAmount() : -e.getAmount();
        }
        return Math.max(0, balance);
    }

    /** Called from MembershipWithdrawalService.withdraw() when a member's balance comes out negative -
     *  posts the DR entry that puts this debt on the ledger in the first place, then records the fact so
     *  admins can find this member again later even though their account is WITHDRAWN. */
    @Transactional
    public void record(Member admin, Member member, Long membershipWithdrawalId, long owedAmount) {
        ledgerService.post(member.getId(), owedAmount, LocalDate.now(), "Bad debt on withdrawal", "BDDR",
                TransCat.BAD_DEBT, DrCr.DR, null, LedgerSource.MANUAL_ADMIN, admin.getId());

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

    public List<Row> outstanding() {
        return badDebtRepository.findByStatusOrderByCreatedAtAsc(BadDebtStatus.OUTSTANDING).stream()
                .map(d -> {
                    Member m = memberRepository.findById(d.getMemberId()).orElse(null);
                    return new Row(d.getId(), d.getMemberId(), m == null ? "?" : m.getRegno(),
                            m == null ? "Unknown member" : m.getFullName(), d.getOriginalAmount(), owedNow(d.getMemberId()),
                            d.getCreatedAt().toString());
                })
                .toList();
    }

    /** Records a repayment as a BAD_DEBT-category CR entry, mirroring how a loan repayment reduces
     *  LOAN-category balance - then checks whether that's brought the ledger-derived balance back to
     *  zero, clearing the debt if so. */
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

        long owed = owedNow(member.getId());
        if (amount > owed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Repayment cannot exceed the amount still owed (" + owed + ")");
        }

        ledgerService.post(member.getId(), amount, LocalDate.now(), "Bad debt repayment", "BDRP",
                TransCat.BAD_DEBT, DrCr.CR, null, LedgerSource.MANUAL_ADMIN, admin.getId());

        if (owedNow(member.getId()) <= 0) {
            debt.setStatus(BadDebtStatus.CLEARED);
            debt.setClearedAt(LocalDateTime.now(ZoneOffset.UTC));
            member.setBadDebt(false);
            memberRepository.save(member);
        }
        return badDebtRepository.save(debt);
    }
}
