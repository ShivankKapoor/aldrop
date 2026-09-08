package com.shivankkapoor.aldrop.Controller;

import com.shivankkapoor.aldrop.Data.Platform;
import com.shivankkapoor.aldrop.DTO.Request.CreatePlatformRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.UpdatePlatformStatusRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.CreatePlatformResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.RotatePlatformKeyResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.UpdatePlatformStatusResponseDTO;
import com.shivankkapoor.aldrop.Service.PlatformService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/platform")
@Tag(name = "Platforms", description = "Operator-only management of the platforms that use Aldrop.")
@SecurityRequirement(name = "platformAdmin")
public class PlatformController {

    private static final Logger log = LoggerFactory.getLogger(PlatformController.class);

    @Autowired
    private PlatformService platformService;

    @Operation(summary = "Register a new platform",
            description = "Creates a platform and returns its API key. The key is shown in this "
                    + "response and never again, so store it when you receive it. If it is lost, "
                    + "use the rotate-key endpoint to issue a replacement.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Platform created; the response carries its API key."),
            @ApiResponse(responseCode = "400", description = "The request body failed validation.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or wrong operator credentials.", content = @Content),
            @ApiResponse(responseCode = "409", description = "A platform with that name already exists.", content = @Content)
    })
    @PostMapping("/create")
    public ResponseEntity<CreatePlatformResponseDTO> create(@Valid @RequestBody CreatePlatformRequestDTO request){
        log.info("Create platform requested, name={}", request.getName());
        Platform platform = platformService.create(request);

        CreatePlatformResponseDTO response = new CreatePlatformResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.getApiKey(),
                platform.getSessionTtl().getSeconds(),
                platform.getMaxSessionsPerUser(),
                platform.isTotpAvailable(),
                platform.isRequireDeviceBinding()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Activate or deactivate a platform",
            description = "A deactivated platform's API key stops being accepted, so none of its "
                    + "users can log in or validate a session. Existing session rows are left in "
                    + "place, so reactivating restores any that have not expired in the meantime.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The platform's active flag was updated."),
            @ApiResponse(responseCode = "400", description = "The request body failed validation.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing or wrong operator credentials.", content = @Content),
            @ApiResponse(responseCode = "404", description = "No platform with that id.", content = @Content)
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<UpdatePlatformStatusResponseDTO> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlatformStatusRequestDTO request){
        log.info("Update platform status requested, id={}, isActive={}", id, request.getIsActive());
        Platform platform = platformService.updateActiveStatus(id, request.getIsActive());

        UpdatePlatformStatusResponseDTO response = new UpdatePlatformStatusResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.isActive()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Issue a new API key for a platform",
            description = "Replaces the platform's API key and returns the new one. The previous "
                    + "key stops working immediately, so the platform's own configuration has to be "
                    + "updated before it can call /auth again. User sessions are unaffected.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A new API key was issued and is in the response."),
            @ApiResponse(responseCode = "401", description = "Missing or wrong operator credentials.", content = @Content),
            @ApiResponse(responseCode = "404", description = "No platform with that id.", content = @Content)
    })
    @PatchMapping("/{id}/rotate-key")
    public ResponseEntity<RotatePlatformKeyResponseDTO> rotateKey(@PathVariable UUID id){
        log.info("Rotate platform API key requested, id={}", id);
        Platform platform = platformService.rotateApiKey(id);

        RotatePlatformKeyResponseDTO response = new RotatePlatformKeyResponseDTO(
                platform.getId(),
                platform.getName(),
                platform.getApiKey()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete a platform",
            description = "Permanently deletes the platform along with every user, session and TOTP "
                    + "challenge belonging to it, by cascade. There is no undo and no soft-delete; "
                    + "to take a platform offline reversibly, deactivate it instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The platform and all of its data were deleted."),
            @ApiResponse(responseCode = "401", description = "Missing or wrong operator credentials.", content = @Content),
            @ApiResponse(responseCode = "404", description = "No platform with that id.", content = @Content)
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        log.info("Delete platform requested, id={}", id);
        platformService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
