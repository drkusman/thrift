package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.*;
import ng.asuu.thrift.domain.LedgerEntry.DrCr;
import ng.asuu.thrift.domain.LedgerEntry.LedgerSource;
import ng.asuu.thrift.domain.LedgerEntry.TransCat;
import ng.asuu.thrift.repo.LoanRepaymentScheduleRepository;
import ng.asuu.thrift.repo.LoanRepository;
import ng.asuu.thrift.repo.MemberRepository;
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
    private final MemberRepository memberRepository;

    public LoanService(LoanRepository loanRepository, LoanRepaymentScheduleRepository scheduleRepository,
                        LoanTypeService loanTypeService, LedgerService ledgerService, MemberRepository memberRepository) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.loanTypeService = loanTypeService;
        this.ledgerService = ledgerService;
        this.memberRepository = memberRepository;
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
    public Loan apply(Member member, Long loanTypeId, long requestedAmount, String reason,
                       Long guarantorOneId, Long guarantorTwoId) {
        return createApplication(member, loanTypeId, requestedAmount, reason, guarantorOneId, guarantorTwoId,
                GuaranteeStatus.PENDING);
    }

    /** For a member who applied offline (a paper form) with both guarantors' signatures already on it -
     *  admin keys in the application and both guarantors go straight to ACCEPTED, since their approval
     *  was already collected on paper rather than needing to be re-confirmed digitally. */
    @Transactional
    public Loan applyOnBehalf(Member member, Long loanTypeId, long requestedAmount, String reason,
                               Long guarantorOneId, Long guarantorTwoId) {
        return createApplication(member, loanTypeId, requestedAmount, reason, guarantorOneId, guarantorTwoId,
                GuaranteeStatus.ACCEPTED);
    }

    private Loan createApplication(Member member, Long loanTypeId, long requestedAmount, String reason,
                                    Long guarantorOneId, Long guarantorTwoId, GuaranteeStatus initialGuarantorStatus) {
        LoanType type = loanTypeService.require(loanTypeId);
        if (requestedAmount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested amount must be positive");
        }
        if (type.getMinAmount() != null && requestedAmount < type.getMinAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    type.getName() + " requires at least " + type.getMinAmount());
        }
        if (type.getMaxAmount() != null && requestedAmount > type.getMaxAmount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    type.getName() + " cannot exceed " + type.getMaxAmount());
        }
        if (type.getMaxConcurrentActive() != null) {
            long activeOfThisType = loanRepository.findByMemberIdOrderByAppliedAtDesc(member.getId()).stream()
                    .filter(l -> type.getId().equals(l.getLoanTypeId()))
                    .filter(l -> l.getStatus() == LoanStatus.RUNNING || l.getStatus() == LoanStatus.PULSED)
                    .count();
            if (activeOfThisType >= type.getMaxConcurrentActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You already have " + activeOfThisType + " " + type.getName() + "(s) running - " +
                                "complete at least one before applying for another");
            }
        }
        if (guarantorOneId == null || guarantorTwoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Two guarantors are required");
        }
        if (guarantorOneId.equals(guarantorTwoId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The two guarantors must be different members");
        }
        if (guarantorOneId.equals(member.getId()) || guarantorTwoId.equals(member.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot list yourself as a guarantor");
        }
        requireActiveMember(guarantorOneId, "First guarantor");
        requireActiveMember(guarantorTwoId, "Second guarantor");
        rejectIfDuplicatePending(member, type, requestedAmount, guarantorOneId, guarantorTwoId);

        Loan loan = new Loan();
        loan.setMemberId(member.getId());
        loan.setLoanTypeId(type.getId());
        loan.setRequestedAmount(requestedAmount);
        loan.setReason(reason);
        loan.setDurationMonths(type.getMaxDurationMonths());
        loan.setGuarantorOneId(guarantorOneId);
        loan.setGuarantorTwoId(guarantorTwoId);
        loan.setGuarantorOneStatus(initialGuarantorStatus);
        loan.setGuarantorTwoStatus(initialGuarantorStatus);
        loan.setStatus(LoanStatus.PENDING);
        return loanRepository.save(loan);
    }

    /** Catches a re-submitted application before it's created - same member, type, amount, and pair of
     *  guarantors (in either order) as one already awaiting a decision. This is what a re-uploaded batch
     *  file, or a double form submission, looks like: not a validation failure so much as "you already
     *  did this". */
    private void rejectIfDuplicatePending(Member member, LoanType type, long requestedAmount,
                                          Long guarantorOneId, Long guarantorTwoId) {
        boolean duplicate = loanRepository.findByMemberIdOrderByAppliedAtDesc(member.getId()).stream()
                .anyMatch(l -> l.getStatus() == LoanStatus.PENDING
                        && type.getId().equals(l.getLoanTypeId())
                        && l.getRequestedAmount() == requestedAmount
                        && sameGuarantorPair(l, guarantorOneId, guarantorTwoId));
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An identical pending " + type.getName() + " application for this amount and these guarantors already exists");
        }
    }

    private static boolean sameGuarantorPair(Loan loan, Long guarantorOneId, Long guarantorTwoId) {
        return (guarantorOneId.equals(loan.getGuarantorOneId()) && guarantorTwoId.equals(loan.getGuarantorTwoId()))
                || (guarantorOneId.equals(loan.getGuarantorTwoId()) && guarantorTwoId.equals(loan.getGuarantorOneId()));
    }

    /** Pending loans where the given member is named as either guarantor - their "guarantee requests". */
    public List<Loan> guaranteeRequestsFor(Long memberId) {
        List<Loan> result = new ArrayList<>();
        for (Loan loan : loanRepository.findByStatusOrderByAppliedAtAsc(LoanStatus.PENDING)) {
            if (memberId.equals(loan.getGuarantorOneId()) || memberId.equals(loan.getGuarantorTwoId())) {
                result.add(loan);
            }
        }
        return result;
    }

    @Transactional
    public Loan respondToGuarantee(Member guarantor, Long loanId, boolean accept) {
        Loan loan = require(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This loan is no longer awaiting a decision");
        }
        GuaranteeStatus newStatus = accept ? GuaranteeStatus.ACCEPTED : GuaranteeStatus.REJECTED;
        if (guarantor.getId().equals(loan.getGuarantorOneId())) {
            loan.setGuarantorOneStatus(newStatus);
        } else if (guarantor.getId().equals(loan.getGuarantorTwoId())) {
            loan.setGuarantorTwoStatus(newStatus);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a guarantor on this loan");
        }
        return loanRepository.save(loan);
    }

    /** Lets an applicant swap out a guarantor who hasn't accepted yet - still PENDING, or has REJECTED -
     *  for someone else. A guarantor who already ACCEPTED can't be swapped out from under them. */
    @Transactional
    public Loan changeGuarantor(Member applicant, Long loanId, int slot, Long newGuarantorId) {
        Loan loan = require(loanId);
        if (!loan.getMemberId().equals(applicant.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your loan application");
        }
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This loan is no longer awaiting a decision");
        }
        if (slot != 1 && slot != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid guarantor slot");
        }
        GuaranteeStatus currentStatus = slot == 1 ? loan.getGuarantorOneStatus() : loan.getGuarantorTwoStatus();
        if (currentStatus == GuaranteeStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This guarantor already accepted and cannot be changed");
        }
        if (newGuarantorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A new guarantor is required");
        }
        if (newGuarantorId.equals(applicant.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot list yourself as a guarantor");
        }
        Long otherGuarantorId = slot == 1 ? loan.getGuarantorTwoId() : loan.getGuarantorOneId();
        if (newGuarantorId.equals(otherGuarantorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The two guarantors must be different members");
        }
        requireActiveMember(newGuarantorId, "New guarantor");

        if (slot == 1) {
            loan.setGuarantorOneId(newGuarantorId);
            loan.setGuarantorOneStatus(GuaranteeStatus.PENDING);
        } else {
            loan.setGuarantorTwoId(newGuarantorId);
            loan.setGuarantorTwoStatus(GuaranteeStatus.PENDING);
        }
        return loanRepository.save(loan);
    }

    private void requireActiveMember(Long memberId, String label) {
        Member guarantor = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " is not a valid member"));
        if (!guarantor.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " must be an active member");
        }
    }

    @Transactional
    public Loan approve(Member admin, Long loanId, String note) {
        Loan loan = require(loanId);
        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a pending loan can be approved");
        }
        if (!loan.bothGuarantorsAccepted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Both guarantors must accept before this loan can be approved");
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
        // Goes straight to RUNNING (not a separate DISBURSED-but-not-yet-running state) so a freshly
        // approved loan reads the same way as an imported legacy one, which never has that distinction.
        loan.setStatus(LoanStatus.RUNNING);
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

        // Booked as the full repayable amount, not the net cash paid out - the member's monthly
        // repayments add up to totalRepayable, so the loan balance must start there too, or an
        // AT_SOURCE loan (interest deducted up front) would drift negative before the term ends.
        ledgerService.post(loan.getMemberId(), totalRepayable, LocalDate.now(),
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

    /** The member's total monthly repayment obligation, summed across every loan currently being
     *  repaid - a member can have more than one running loan at once (e.g. a legacy loan still
     *  RUNNING alongside a newly disbursed one), and each contributes to the deduction. */
    public long activeMonthlyRepayment(Long memberId) {
        long total = 0;
        for (Loan loan : loanRepository.findByMemberIdOrderByAppliedAtDesc(memberId)) {
            if (loan.getStatus() == LoanStatus.DISBURSED || loan.getStatus() == LoanStatus.RUNNING) {
                Long monthly = loan.getMonthlyRepaymentAmount();
                total += monthly == null ? 0 : monthly;
            }
        }
        return total;
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

    /** A single loan's own outstanding balance, same DR-minus-CR logic as outstandingBalance() but
     *  scoped to just this loan's ledger entries rather than everything the member owes. */
    public long balanceFor(Long loanId) {
        long balance = 0;
        for (var e : ledgerService.forLoan(loanId)) {
            if (e.getTransCat() != TransCat.LOAN) continue;
            balance += e.getDrCrStatus() == DrCr.DR ? e.getAmount() : -e.getAmount();
        }
        return balance;
    }
}
