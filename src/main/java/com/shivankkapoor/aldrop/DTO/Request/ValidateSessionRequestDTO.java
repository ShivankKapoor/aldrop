package com.shivankkapoor.aldrop.DTO.Request;

import com.shivankkapoor.aldrop.Validation.FieldLimits;
import com.shivankkapoor.aldrop.Validation.IpAddressPattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ValidateSessionRequestDTO {
    @NotBlank
    @Size(max = FieldLimits.TOKEN_MAX)
    private String token;

    @Pattern(regexp = IpAddressPattern.REGEX)
    @Size(max = FieldLimits.IP_ADDRESS_MAX)
    private String ipAddress;

    @Size(max = FieldLimits.USER_AGENT_MAX)
    private String userAgent;
}
