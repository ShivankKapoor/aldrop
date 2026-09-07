package com.shivankkapoor.aldrop.DTO.Request;

import com.shivankkapoor.aldrop.Validation.IpAddressPattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ValidateSessionRequestDTO {
    @NotBlank
    private String token;

    @Pattern(regexp = IpAddressPattern.REGEX)
    private String ipAddress;

    private String userAgent;
}
