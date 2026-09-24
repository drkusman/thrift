package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotNull;

public record ChangeGuarantorRequest(@NotNull Integer slot, @NotNull Long newGuarantorId) {
}
