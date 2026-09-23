package ng.asuu.thrift.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterMemberRequest(
        @NotBlank String regno, @NotBlank String fullName, String phone, String email, String sex,
        String deptCode, String factCode, String payPoint, String role
) {
}
