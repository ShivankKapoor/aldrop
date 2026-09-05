package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfirmTotpRequestDTO {
    @NotBlank
    private String token;

    @NotBlank
    private String code;
}
