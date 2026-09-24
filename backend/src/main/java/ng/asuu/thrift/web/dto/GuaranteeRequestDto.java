package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.Member;

public record GuaranteeRequestDto(Long loanId, String loanCode, Long applicantMemberId, String applicantName,
                                   String applicantRegno, long requestedAmount, String reason, String appliedAt,
                                   int mySlot, String myStatus, String otherGuarantorStatus) {
    public static GuaranteeRequestDto of(Loan loan, Member applicant, Long viewerMemberId) {
        boolean isSlotOne = viewerMemberId.equals(loan.getGuarantorOneId());
        return new GuaranteeRequestDto(
                loan.getId(), loan.getLoanCode(), applicant.getId(), applicant.getFullName(), applicant.getRegno(),
                loan.getRequestedAmount(), loan.getReason(),
                loan.getAppliedAt() == null ? null : loan.getAppliedAt().toString(),
                isSlotOne ? 1 : 2,
                (isSlotOne ? loan.getGuarantorOneStatus() : loan.getGuarantorTwoStatus()).name(),
                (isSlotOne ? loan.getGuarantorTwoStatus() : loan.getGuarantorOneStatus()).name());
    }
}
