package com.shivankkapoor.aldrop.DTO.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnableTotpResponseDTO {
    private String secret;
    private String otpAuthUri;
}
