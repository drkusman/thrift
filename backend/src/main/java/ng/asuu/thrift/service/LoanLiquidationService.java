package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.LoanLiquidation;
import ng.asuu.thrift.domain.LoanRepaymentSchedule;
import ng.asuu.thrift.domain.LoanRepaymentSchedule.ScheduleStatus;
import ng.asuu.thrift.domain.LoanStatus;
import ng.asuu.thrift.domain.LoanLiquidationRequest;
import ng.asuu.thrift.domain.LoanLiquidationRequest.RequestStatus;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.LoanLiquidationRepository;
import ng.asuu.thrift.repo.LoanLiquidationRequestRepository;
import ng.asuu.thrift.repo.LoanRepaymentScheduleRepository;
import ng.asuu.thrift.repo.LoanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * A liquidation pays off all or part of a loan's remaining balance out of the member's own savings, in one
 * lump sum, instead of waiting out the monthly repayment plan - Karim's own explanation: "liquidation is
 * done per loan... could be the entire balance or part of the balance. When done partially, admin then
 * reviews the repayplan downward." Every liquidation posts three ledger entries together: the liquidation
 * amount debited from savings (MSVG), a flat admin fee also debited from savings (FEES), and that same
 * liquidation amount - not reduced by the fee - credited to the loan (LRPT). The fee is a separate charge;
 * it never touches the loan balance.
 * <p>
 * A full liquidation (amount equals the loan's current balance) closes the loan immediately: every
 * remaining schedule installment is marked PAID and the loan goes straight to COMPLETED. A partial
 * liquidation instead spreads the reduced balance evenly across the loan's remaining not-yet-PAID
 * installments - same floor-division-with-remainder-on-the-last-installment convention
 * LoanService.approve() uses to build the original schedule - and updates the loan's own
 * monthlyRepaymentAmount to match. That's the "review the repayplan downward" step, done automatically
 * rather than the admin retyping every remaining row by hand.
 */
@Service
public class LoanLiquidationService {
    private static final long ADMIN_FEE = 1000;
    /** A member's savings can never be fully drained by a liquidation - this much always stays behind,
     *  same floor as the minimum monthly savings amount elsewhere in the app. Karim's own rule: "member
     *  with 100,000 total savings can only enjoy liquidation + admin fees to the tune of 80,000." */
    private static final long MINIMUM_RETAINED_SAVINGS = 20000;

    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanLiquidationRepository liquidationRepository;
    private final LoanLiquidationRequestRepository requestRepository;
    private final LedgerService ledgerService;
    private final LoanService loanService;

    public LoanLiquidationService(LoanRepository loanRepository, LoanRepaymentScheduleRepository scheduleRepository,
                                   LoanLiquidationRepository liquidationRepository,
                                   LoanLiquidationRequestRepository requestRepository, LedgerService ledgerService,
                                   LoanService loanService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.liquidationRepository = liquidationRepository;
        this.requestRepository = requestRepository;
        this.ledgerService = ledgerService;
        this.loanService = loanService;
    }

    /** A read-only preview so the admin sees exactly what a liquidation will do (the recalculated monthly
     *  repayment, whether it closes the loan, whether savings can cover it) before committing to it. */
    public record Preview(long currentBalance, long amount, long adminFee, boolean fullLiquidation, long newBalance,
                           long savingsBalance, long minimumRetainedSavings, long maxLiquidatable,
                           boolean sufficientSavings, Integer remainingInstallments, Long proposedMonthlyRepayment) {}

    public Preview preview(Long loanId, long amount) {
        Loan loan = requireLiquidatable(loanId);
        long balance = loanService.balanceFor(loanId);
        validateAmount(amount, balance);
        long newBalance = balance - amount;
        boolean full = newBalance <= 0;
        long savingsBalance = ledgerService.savingsBalance(loan.getMemberId());
        boolean sufficient = savingsAfter(savingsBalance, amount) >= MINIMUM_RETAINED_SAVINGS;
        long maxLiquidatable = Math.max(0, savingsBalance - MINIMUM_RETAINED_SAVINGS - ADMIN_FEE);

        Integer remainingCount = null;
        Long proposedMonthly = null;
        if (!full) {
            List<LoanRepaymentSchedule> remaining = remainingInstallments(loanId);
            if (!remaining.isEmpty()) {
                remainingCount = remaining.size();
                proposedMonthly = Math.floorDiv(newBalance, remainingCount);
            }
        }
        return new Preview(balance, amount, ADMIN_FEE, full, Math.max(newBalance, 0), savingsBalance,
                MINIMUM_RETAINED_SAVINGS, maxLiquidatable, sufficient, remainingCount, proposedMonthly);
    }

    private static long savingsAfter(long savingsBalance, long amount) {
        return savingsBalance - amount - ADMIN_FEE;
    }

    @Transactional
    public LoanLiquidation liquidate(Member admin, Long loanId, long amount) {
        Loan loan = requireLiquidatable(loanId);
        long balance = loanService.balanceFor(loanId);
        validateAmount(amount, balance);

        long savingsBalance = ledgerService.savingsBalance(loan.getMemberId());
        if (savingsAfter(savingsBalance, amount) < MINIMUM_RETAINED_SAVINGS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This would leave savings below the " + MINIMUM_RETAINED_SAVINGS + " minimum a member must " +
                    "always retain - liquidating " + amount + " plus the " + ADMIN_FEE + " admin fee needs " +
                    (amount + ADMIN_FEE + MINIMUM_RETAINED_SAVINGS) + " kept in savings, but savings only has " +
                    savingsBalance);
        }

        LocalDate today = LocalDate.now();
        var savingsEntry = ledgerService.post(loan.getMemberId(), amount, today,
                "Liquidation with Savings - " + loan.getLoanCode(), "MSVG", TransCat.SAVINGS, DrCr.DR,
                null, LedgerSource.MANUAL_ADMIN, admin.getId());
        var feeEntry = ledgerService.post(loan.getMemberId(), ADMIN_FEE, today,
                "Liquidation Fees - " + loan.getLoanCode(), "FEES", TransCat.SAVINGS, DrCr.DR,
                null, LedgerSource.MANUAL_ADMIN, admin.getId());
        var loanEntry = ledgerService.post(loan.getMemberId(), amount, today,
                "Loan Payment by Liquidation " + loan.getLoanCode(), "LRPT", TransCat.LOAN, DrCr.CR,
                loanId, LedgerSource.MANUAL_ADMIN, admin.getId());

        long newBalance = balance - amount;
        boolean full = newBalance <= 0;
        Long oldMonthly = loan.getMonthlyRepaymentAmount();
        Long newMonthly = null;

        if (full) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            for (LoanRepaymentSchedule s : scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId)) {
                if (s.getStatus() == ScheduleStatus.PAID) continue;
                s.setAmountPaid(s.getAmountDue());
                s.setStatus(ScheduleStatus.PAID);
                s.setPaidAt(now);
                scheduleRepository.save(s);
            }
            loan.setStatus(LoanStatus.COMPLETED);
            loanRepository.save(loan);
        } else {
            List<LoanRepaymentSchedule> remaining = remainingInstallments(loanId);
            if (!remaining.isEmpty()) {
                int n = remaining.size();
                long per = Math.floorDiv(newBalance, n);
                long remainder = newBalance - (per * n);
                for (int i = 0; i < n; i++) {
                    LoanRepaymentSchedule s = remaining.get(i);
                    s.setAmountDue(per + (i == n - 1 ? remainder : 0));
                    scheduleRepository.save(s);
                }
                newMonthly = per;
                loan.setMonthlyRepaymentAmount(newMonthly);
                loanRepository.save(loan);
            }
        }

        LoanLiquidation audit = new LoanLiquidation();
        audit.setLoanId(loanId);
        audit.setMemberId(loan.getMemberId());
        audit.setAmount(amount);
        audit.setAdminFee(ADMIN_FEE);
        audit.setFullLiquidation(full);
        audit.setOldMonthlyRepaymentAmount(oldMonthly);
        audit.setNewMonthlyRepaymentAmount(newMonthly);
        audit.setSavingsLedgerEntryId(savingsEntry.getId());
        audit.setFeeLedgerEntryId(feeEntry.getId());
        audit.setLoanLedgerEntryId(loanEntry.getId());
        audit.setPerformedBy(admin.getId());
        return liquidationRepository.save(audit);
    }

    /** Every unpaid-loan-balance-covering-in-full-or-part request a member has filed for themselves,
     *  newest first. */
    public List<LoanLiquidationRequest> requestsForMember(Long memberId) {
        return requestRepository.findByMemberIdOrderByRequestedAtDesc(memberId);
    }

    public List<LoanLiquidationRequest> pendingRequests() {
        return requestRepository.findByStatusOrderByRequestedAtAsc(RequestStatus.PENDING);
    }

    /** A member applying to liquidate their own loan - sanity-checked the same way an admin's direct
     *  liquidation is (amount within balance, savings can cover it), but not yet posted: approve() does
     *  that, re-validating fresh in case the balance or savings moved between request and decision. */
    @Transactional
    public LoanLiquidationRequest apply(Member member, Long loanId, long amount) {
        Loan loan = requireLiquidatable(loanId);
        if (!loan.getMemberId().equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your loan");
        }
        if (!requestRepository.findByLoanIdAndStatus(loanId, RequestStatus.PENDING).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This loan already has a liquidation request awaiting a decision");
        }
        long balance = loanService.balanceFor(loanId);
        validateAmount(amount, balance);
        long savingsBalance = ledgerService.savingsBalance(member.getId());
        if (savingsAfter(savingsBalance, amount) < MINIMUM_RETAINED_SAVINGS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This would leave your savings below the " + MINIMUM_RETAINED_SAVINGS + " minimum you must " +
                    "always retain");
        }

        LoanLiquidationRequest req = new LoanLiquidationRequest();
        req.setLoanId(loanId);
        req.setMemberId(member.getId());
        req.setRequestedAmount(amount);
        return requestRepository.save(req);
    }

    /** Approving actually runs the liquidation (posting the same three ledger entries and revising the
     *  schedule an admin's own direct liquidation would) - the request is just what triggered it. */
    @Transactional
    public LoanLiquidationRequest approve(Member admin, Long requestId) {
        LoanLiquidationRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        LoanLiquidation audit = liquidate(admin, req.getLoanId(), req.getRequestedAmount());
        req.setStatus(RequestStatus.APPROVED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        req.setLoanLiquidationId(audit.getId());
        return requestRepository.save(req);
    }

    @Transactional
    public LoanLiquidationRequest reject(Member admin, Long requestId, String note) {
        LoanLiquidationRequest req = requireRequest(requestId);
        if (req.getStatus() != RequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request already decided");
        }
        req.setStatus(RequestStatus.REJECTED);
        req.setDecidedBy(admin.getId());
        req.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        req.setDecisionNote(note);
        return requestRepository.save(req);
    }

    private LoanLiquidationRequest requireRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private List<LoanRepaymentSchedule> remainingInstallments(Long loanId) {
        return scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId).stream()
                .filter(s -> s.getStatus() != ScheduleStatus.PAID)
                .toList();
    }

    private Loan requireLiquidatable(Long loanId) {
        Loan loan = loanService.require(loanId);
        if (loan.getStatus() != LoanStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a running loan can be liquidated");
        }
        return loan;
    }

    private void validateAmount(long amount, long balance) {
        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liquidation amount must be positive");
        }
        if (amount > balance) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount exceeds this loan's outstanding balance of " + balance);
        }
    }
}
