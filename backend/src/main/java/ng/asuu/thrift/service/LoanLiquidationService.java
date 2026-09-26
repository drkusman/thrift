package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.LoanLiquidation;
import ng.asuu.thrift.domain.LoanRepaymentSchedule;
import ng.asuu.thrift.domain.LoanRepaymentSchedule.ScheduleStatus;
import ng.asuu.thrift.domain.LoanStatus;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.repo.LoanLiquidationRepository;
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

    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanLiquidationRepository liquidationRepository;
    private final LedgerService ledgerService;
    private final LoanService loanService;

    public LoanLiquidationService(LoanRepository loanRepository, LoanRepaymentScheduleRepository scheduleRepository,
                                   LoanLiquidationRepository liquidationRepository, LedgerService ledgerService,
                                   LoanService loanService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.liquidationRepository = liquidationRepository;
        this.ledgerService = ledgerService;
        this.loanService = loanService;
    }

    /** A read-only preview so the admin sees exactly what a liquidation will do (the recalculated monthly
     *  repayment, whether it closes the loan, whether savings can cover it) before committing to it. */
    public record Preview(long currentBalance, long amount, long adminFee, boolean fullLiquidation, long newBalance,
                           long savingsBalance, boolean sufficientSavings, Integer remainingInstallments,
                           Long proposedMonthlyRepayment) {}

    public Preview preview(Long loanId, long amount) {
        Loan loan = requireLiquidatable(loanId);
        long balance = loanService.balanceFor(loanId);
        validateAmount(amount, balance);
        long newBalance = balance - amount;
        boolean full = newBalance <= 0;
        long savingsBalance = ledgerService.savingsBalance(loan.getMemberId());
        boolean sufficient = savingsBalance >= amount + ADMIN_FEE;

        Integer remainingCount = null;
        Long proposedMonthly = null;
        if (!full) {
            List<LoanRepaymentSchedule> remaining = remainingInstallments(loanId);
            if (!remaining.isEmpty()) {
                remainingCount = remaining.size();
                proposedMonthly = Math.floorDiv(newBalance, remainingCount);
            }
        }
        return new Preview(balance, amount, ADMIN_FEE, full, Math.max(newBalance, 0), savingsBalance, sufficient,
                remainingCount, proposedMonthly);
    }

    @Transactional
    public Loan liquidate(Member admin, Long loanId, long amount) {
        Loan loan = requireLiquidatable(loanId);
        long balance = loanService.balanceFor(loanId);
        validateAmount(amount, balance);

        long savingsBalance = ledgerService.savingsBalance(loan.getMemberId());
        if (savingsBalance < amount + ADMIN_FEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient savings balance - liquidating " + amount + " plus the " + ADMIN_FEE +
                    " admin fee needs " + (amount + ADMIN_FEE) + ", but savings only has " + savingsBalance);
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

        LoanLiquidation record = new LoanLiquidation();
        record.setLoanId(loanId);
        record.setMemberId(loan.getMemberId());
        record.setAmount(amount);
        record.setAdminFee(ADMIN_FEE);
        record.setFullLiquidation(full);
        record.setOldMonthlyRepaymentAmount(oldMonthly);
        record.setNewMonthlyRepaymentAmount(newMonthly);
        record.setSavingsLedgerEntryId(savingsEntry.getId());
        record.setFeeLedgerEntryId(feeEntry.getId());
        record.setLoanLedgerEntryId(loanEntry.getId());
        record.setPerformedBy(admin.getId());
        liquidationRepository.save(record);

        return loan;
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
