package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String regno, @NotBlank String password) {
}
