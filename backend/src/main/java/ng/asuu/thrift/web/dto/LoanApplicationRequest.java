package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LoanApplicationRequest(@NotNull Long loanTypeId, @Positive long requestedAmount, String reason, Integer durationMonths) {
}
