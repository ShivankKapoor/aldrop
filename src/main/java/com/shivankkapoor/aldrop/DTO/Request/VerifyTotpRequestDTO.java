package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyTotpRequestDTO {
    @NotBlank
    private String totpToken;

    @NotBlank
    private String code;
}
