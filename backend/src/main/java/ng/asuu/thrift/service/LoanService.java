package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.LoanRepaymentScheduleRepository;
import ng.asuu.thrift.repo.LoanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Interest is computed once, at approval time, per the loan type's method (Karim's own explanation):
 *   AT_SOURCE (AS): interest deducted up front; member still repays the full requested amount.
 *     interestAmount = requestedAmount * rate/100; disbursedAmount = requestedAmount - interestAmount;
 *     totalRepayable = requestedAmount; monthlyRepayment = requestedAmount / durationMonths.
 *   BUILT_IN (BI): full amount disbursed; interest added on top of what's repaid.
 *     interestAmount = requestedAmount * rate/100; disbursedAmount = requestedAmount;
 *     totalRepayable = requestedAmount + interestAmount; monthlyRepayment = totalRepayable / durationMonths.
 */
@Service
public class LoanService {
    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanTypeService loanTypeService;
    private final LedgerService ledgerService;

    public LoanService(LoanRepository loanRepository, LoanRepaymentScheduleRepository scheduleRepository,
                        LoanTypeService loanTypeService, LedgerService ledgerService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.loanTypeService = loanTypeService;
        this.ledgerService = ledgerService;
    }

    public Loan require(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found"));
    }

    public List<Loan> forMember(Long memberId) {
        return loanRepository.findByMemberIdOrderByAppliedAtDesc(memberId);
    }

    public List<Loan> pending() {
        return loanRepository.findByStatusOrderByAppliedAtAsc(LoanStatus.PENDING);
    }

    public List<LoanRepaymentSchedule> scheduleFor(Long loanId) {
        return scheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loanId);
    }

    @Transactional
    public Loan apply(Member member, Long loanTypeId, long requestedAmount, String reason, Integer durationMonths) {
        LoanType type = loanTypeService.require(loanTypeId);
        if (requestedAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested amount must be positive");
        }
        int duration = durationMonths == null ? type.getMaxDurationMonths() : durationMonths;
        if (duration <= 0 || duration > type.getMaxDurationMonths()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duration must be between 1 and " + type.getMaxDurationMonths() + " months for " + type.getName());
        }
        Loan loan = new Loan();
        loan.setMemberId(member.getId());
        loan.setLoanTypeId(type.getId());
        loan.setRequestedAmount(requestedAmount);
        loan.setReason(reason);
        loan.setDurationMonths(duration);
        loan.setStatus(LoanStatus.PENDING);
        return loanRepository.save(loan);
    }

    @Transactional
    public Loan approve(Member admin, Long loanId, String note) {
        Loan loan = require(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending loan can be approved");
        }
        LoanType type = loanTypeService.require(loan.getLoanTypeId());
        long requested = loan.getRequestedAmount();
        long interest = Math.round(requested * type.getInterestRate() / 100.0);
        long disbursed;
        long totalRepayable;
        if (type.getInterestMethod() == InterestMethod.AT_SOURCE) {
            disbursed = requested - interest;
            totalRepayable = requested;
        } else {
            disbursed = requested;
            totalRepayable = requested + interest;
        }
        long monthly = Math.floorDiv(totalRepayable, loan.getDurationMonths());
        long remainder = totalRepayable - (monthly * loan.getDurationMonths());

        loan.setInterestAmount(interest);
        loan.setDisbursedAmount(disbursed);
        loan.setTotalRepayable(totalRepayable);
        loan.setMonthlyRepaymentAmount(monthly);
        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDecidedBy(admin.getId());
        loan.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        loan.setDecisionNote(note);
        loan.setDisbursedAt(LocalDateTime.now(ZoneOffset.UTC));
        if (loan.getLoanCode() == null) {
            loan.setLoanCode("LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        loanRepository.save(loan);

        LocalDate firstDue = LocalDate.now().plusMonths(1).withDayOfMonth(1);
        List<LoanRepaymentSchedule> schedule = new ArrayList<>();
        for (int i = 1; i <= loan.getDurationMonths(); i++) {
            LoanRepaymentSchedule row = new LoanRepaymentSchedule();
            row.setLoanId(loan.getId());
            row.setInstallmentNo(i);
            row.setDueDate(firstDue.plusMonths(i - 1));
            long amountDue = monthly + (i == loan.getDurationMonths() ? remainder : 0);
            row.setAmountDue(amountDue);
            schedule.add(row);
        }
        scheduleRepository.saveAll(schedule);

        ledgerService.post(loan.getMemberId(), disbursed, LocalDate.now(),
                "Loan disbursement - " + type.getName() + " (" + loan.getLoanCode() + ")",
                type.getCode(), TransCat.LOAN, DrCr.DR, loan.getId(), LedgerSource.LOAN_DISBURSEMENT, admin.getId());

        return loan;
    }

    @Transactional
    public Loan reject(Member admin, Long loanId, String note) {
        Loan loan = require(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending loan can be rejected");
        }
        loan.setStatus(LoanStatus.REJECTED);
        loan.setDecidedBy(admin.getId());
        loan.setDecidedAt(LocalDateTime.now(ZoneOffset.UTC));
        loan.setDecisionNote(note);
        return loanRepository.save(loan);
    }

    /** Outstanding loan balance: sum of DR (disbursement) minus CR (repayments) LOAN-category postings. */
    public long outstandingBalance(Long memberId) {
        long balance = 0;
        for (var e : ledgerService.history(memberId)) {
            if (e.getTransCat() != TransCat.LOAN) continue;
            balance += e.getDrCrStatus() == DrCr.DR ? e.getAmount() : -e.getAmount();
        }
        return balance;
    }
}
