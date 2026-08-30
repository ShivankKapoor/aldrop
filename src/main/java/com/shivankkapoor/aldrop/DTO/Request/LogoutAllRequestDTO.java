package com.shivankkapoor.aldrop.DTO.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutAllRequestDTO {
    @NotBlank
    private String token;
}
