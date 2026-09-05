package com.shivankkapoor.aldrop.DTO.Response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmTotpResponseDTO {
    private List<String> backupCodes;
}
