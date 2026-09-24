package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Same shape as LoanApplicationRequest, plus the memberId - admin is keying this in on behalf of
 *  someone who applied offline with a signed paper form, not for themselves. */
public record AdminLoanApplicationRequest(@NotNull Long memberId, @NotNull Long loanTypeId,
                                           @Positive long requestedAmount, String reason,
                                           @NotNull Long guarantorOneId, @NotNull Long guarantorTwoId) {
}
