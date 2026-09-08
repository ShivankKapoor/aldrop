package com.shivankkapoor.aldrop.DTO.Request;

import com.shivankkapoor.aldrop.Validation.FieldLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequestDTO {
    @NotBlank
    @Size(max = FieldLimits.USERNAME_MAX)
    private String username;

    @NotBlank
    @Size(min = FieldLimits.PASSWORD_MIN, max = FieldLimits.PASSWORD_MAX)
    private String password;
}
