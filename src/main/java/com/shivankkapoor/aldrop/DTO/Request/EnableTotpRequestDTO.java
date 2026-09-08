package com.shivankkapoor.aldrop.DTO.Request;

import com.shivankkapoor.aldrop.Validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EnableTotpRequestDTO {
    @NotBlank
    @Size(max = FieldLimits.TOKEN_MAX)
    private String token;
}
