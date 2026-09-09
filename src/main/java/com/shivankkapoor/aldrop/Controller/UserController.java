package com.shivankkapoor.aldrop.Controller;

import java.util.UUID;

import com.shivankkapoor.aldrop.Data.User;
import com.shivankkapoor.aldrop.DTO.Request.ConfirmTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.EnableTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LoginRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutAllRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.LogoutRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.RegisterUserRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.ValidateSessionRequestDTO;
import com.shivankkapoor.aldrop.DTO.Request.VerifyTotpRequestDTO;
import com.shivankkapoor.aldrop.DTO.Response.ConfirmTotpResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.EnableTotpResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.LoginResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.RegisterUserResponseDTO;
import com.shivankkapoor.aldrop.DTO.Response.ValidateSessionResponseDTO;
import com.shivankkapoor.aldrop.Filter.PlatformApiKeyAuthFilter;
import com.shivankkapoor.aldrop.Service.AuthService;
import com.shivankkapoor.aldrop.Service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication",
        description = "Called by a platform on behalf of its own users. The API key on the request "
                + "decides which platform's user pool is used, so a username only ever refers to a "
                + "user within the calling platform.")
@SecurityRequirement(name = "platformApiKey")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "Register a user on the calling platform",
            description = "Usernames are lowercased and are unique per platform, so the same "
                    + "username can exist independently on other platforms. The password is stored "
                    + "as an Argon2 hash and is never recoverable.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "The user was created."),
            @ApiResponse(responseCode = "400", description = "Validation failed; the password must be at least 8 characters.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Missing, unknown, or deactivated platform API key.", content = @Content),
            @ApiResponse(responseCode = "409", description = "That username already exists on this platform.", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponseDTO> register(
            @Valid @RequestBody RegisterUserRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        log.info("User registration requested, platformId={}, username={}", platformId, request.getUsername());
        User user = userService.register(platformId, request);

        RegisterUserResponseDTO response = new RegisterUserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Log a user in",
            description = """
                    Returns one of two shapes, and the caller has to check which. Normally the \
                    response carries `token` (a session token) and `totpToken` is null. If the user \
                    has two-factor enabled, it is the other way round: `token` is null and \
                    `totpToken` holds a short-lived challenge that has to be completed at \
                    /auth/login/verify-totp within 5 minutes.

                    Send `ipAddress` and `userAgent` if the platform was created with device \
                    binding on; without them the call is rejected. They are recorded either way.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Either a session token, or a TOTP challenge to complete."),
            @ApiResponse(responseCode = "400", description = "Validation failed, or device binding is required and ipAddress/userAgent were missing.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Wrong username or password, or the account is deactivated. The two are deliberately not distinguished.", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many failed attempts for this username; 5 per 15 minutes. A successful login clears the counter.", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        log.info("Login requested, platformId={}, username={}", platformId, request.getUsername());
        LoginResponseDTO response = authService.login(platformId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check whether a session token is still valid",
            description = "Call this on each request that needs authentication. It returns the "
                    + "user id, username, and the session's expiry. Because sessions are server-side rows "
                    + "rather than JWTs, a logged-out or revoked session fails here immediately "
                    + "rather than staying valid until it expires.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session is valid; the response carries the user id, username, and expiry."),
            @ApiResponse(responseCode = "400", description = "Device binding is required and ipAddress/userAgent were missing.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unknown, expired, or revoked token; a token belonging to another platform; a deactivated user; or a device-binding mismatch. All report the same way.", content = @Content)
    })
    @PostMapping("/validate")
    public ResponseEntity<ValidateSessionResponseDTO> validate(
            @Valid @RequestBody ValidateSessionRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        ValidateSessionResponseDTO response = authService.validate(platformId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "End one session",
            description = "Deletes the session row, so the token stops working immediately. "
                    + "Succeeds even when the token is unknown or already expired, so a client can "
                    + "log out without first checking whether the session was still live.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "The session is gone, whether or not it existed."),
            @ApiResponse(responseCode = "401", description = "Missing, unknown, or deactivated platform API key.", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        authService.logout(platformId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "End every session for the token's user",
            description = "Revokes all of that user's sessions on this platform, including the one "
                    + "making the call. Use it after a password change or when a user reports their "
                    + "account may be compromised.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Every session for that user on this platform was revoked."),
            @ApiResponse(responseCode = "401", description = "Missing, unknown, or deactivated platform API key.", content = @Content)
    })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @Valid @RequestBody LogoutAllRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        authService.logoutAll(platformId, request);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Complete a two-factor login",
            description = """
                    Second half of the login started at /auth/login. Send the `totpToken` from that \
                    response together with either a current authenticator code or one of the user's \
                    backup codes; a backup code is consumed and cannot be reused.

                    A code that has already been accepted is refused even though it is still inside \
                    its 30 second window, so a code observed by someone else cannot be replayed. A \
                    rejected code counts as a failed attempt but leaves the challenge usable; the \
                    challenge is spent only on success, after 5 failed attempts, or after 5 minutes.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Two-factor passed; the response carries a session token."),
            @ApiResponse(responseCode = "400", description = "Device binding is required and ipAddress/userAgent were missing.", content = @Content),
            @ApiResponse(responseCode = "401", description = "Wrong, reused, or expired code, or a challenge that is expired, already used, or out of attempts.", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many verification attempts for this user; 5 per 15 minutes.", content = @Content)
    })
    @PostMapping("/login/verify-totp")
    public ResponseEntity<LoginResponseDTO> verifyTotp(
            @Valid @RequestBody VerifyTotpRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        LoginResponseDTO response = authService.verifyTotp(platformId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Start two-factor enrolment",
            description = "Takes an active session token, so the user must already be logged in. "
                    + "Generates a secret and returns it with an otpauth:// URI for a QR code. "
                    + "Two-factor is not switched on until the user proves they can generate a "
                    + "code, at /auth/totp/confirm.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Enrolment started; the response carries the secret and otpauth URI."),
            @ApiResponse(responseCode = "401", description = "Unknown, expired, or revoked session token, or a deactivated user.", content = @Content),
            @ApiResponse(responseCode = "409", description = "This platform does not offer two-factor, or the user already has it enabled.", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many enrolment attempts for this user; 5 per 15 minutes.", content = @Content)
    })
    @PostMapping("/totp/enable")
    public ResponseEntity<EnableTotpResponseDTO> enableTotp(
            @Valid @RequestBody EnableTotpRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        EnableTotpResponseDTO response = authService.enableTotp(platformId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Finish two-factor enrolment",
            description = "Verifies a code generated from the secret issued by /auth/totp/enable, "
                    + "switches two-factor on, and returns 8 single-use backup codes. Show them to "
                    + "the user once: they are the only way back in if the authenticator is lost, "
                    + "and there is currently no way to regenerate them or turn two-factor off.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Two-factor is on; the response carries the backup codes."),
            @ApiResponse(responseCode = "401", description = "Wrong or already-used code, or an invalid session token.", content = @Content),
            @ApiResponse(responseCode = "409", description = "The user already has two-factor enabled.", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many confirmation attempts for this user; 5 per 15 minutes.", content = @Content)
    })
    @PostMapping("/totp/confirm")
    public ResponseEntity<ConfirmTotpResponseDTO> confirmTotp(
            @Valid @RequestBody ConfirmTotpRequestDTO request,
            HttpServletRequest servletRequest){
        UUID platformId = (UUID) servletRequest.getAttribute(PlatformApiKeyAuthFilter.PLATFORM_ID_ATTRIBUTE);
        ConfirmTotpResponseDTO response = authService.confirmTotp(platformId, request);

        return ResponseEntity.ok(response);
    }
}
