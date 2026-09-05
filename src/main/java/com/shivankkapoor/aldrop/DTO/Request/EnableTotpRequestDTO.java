package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnableTotpRequestDTO {
    @NotBlank
    private String token;
}
