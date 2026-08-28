package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequestDTO {
    @NotBlank
    private String username;

    @NotBlank
    @Size(min = 8)
    private String password;
}
