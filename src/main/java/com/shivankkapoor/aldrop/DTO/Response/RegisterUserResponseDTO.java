package com.shivankkapoor.aldrop.DTO.Response;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterUserResponseDTO {
    private UUID id;
    private String username;
    private OffsetDateTime createdAt;
}
