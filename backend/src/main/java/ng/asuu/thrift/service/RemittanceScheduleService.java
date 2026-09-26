package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.web.dto.RemittanceRowDto;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * The monthly remittance schedule an admin hands to the university's payroll office: for one pay
 * point, what to deduct from each active member's salary this period - their standing monthly savings
 * amount plus whatever loan repayment they currently owe (see LoanService.activeMonthlyRepayment,
 * which already sums correctly across legacy loans that predate a per-installment schedule and any
 * newly disbursed ones running alongside them).
 */
@Service
public class RemittanceScheduleService {
    private final MemberRepository memberRepository;
    private final LoanService loanService;

    public RemittanceScheduleService(MemberRepository memberRepository, LoanService loanService) {
        this.memberRepository = memberRepository;
        this.loanService = loanService;
    }

    /** Every distinct pay point among active members, for the picker. */
    public List<String> payPoints() {
        return memberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(Member::getPayPoint)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public List<RemittanceRowDto> generate(String payPoint) {
        return memberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .filter(m -> payPoint.equals(m.getPayPoint()))
                .sorted(Comparator.comparing(Member::getFullName))
                .map(this::toRow)
                .toList();
    }

    private RemittanceRowDto toRow(Member m) {
        long savings = m.getMonthlySavingsAmount();
        long loanRepayment = loanService.activeMonthlyRepayment(m.getId());
        return new RemittanceRowDto(m.getRegno(), m.getFullName(), savings, loanRepayment, savings + loanRepayment);
    }
}
