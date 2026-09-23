package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BankAccountRequest(@NotNull Long bankId, @NotBlank String accountNo) {
}
