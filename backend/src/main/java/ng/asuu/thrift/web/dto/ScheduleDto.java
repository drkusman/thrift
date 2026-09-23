package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.LoanRepaymentSchedule;

public record ScheduleDto(Long id, Long loanId, int installmentNo, String dueDate, long amountDue, long amountPaid, String status) {
    public static ScheduleDto of(LoanRepaymentSchedule s) {
        return new ScheduleDto(s.getId(), s.getLoanId(), s.getInstallmentNo(), s.getDueDate().toString(),
                s.getAmountDue(), s.getAmountPaid(), s.getStatus().name());
    }
}
