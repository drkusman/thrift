package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.Positive;

public record SavingsChangeAmountRequest(@Positive long amount) {
}
