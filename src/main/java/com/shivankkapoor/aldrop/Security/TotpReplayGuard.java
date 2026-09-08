package com.shivankkapoor.aldrop.Security;

import java.util.UUID;

/**
 * Guards against replay of a TOTP code within its acceptance window.
 *
 * <p>A code stays valid for roughly 90 seconds (30 second time step, plus or minus one step of
 * allowed discrepancy), so an observed code can otherwise be submitted again on a fresh login.
 * RFC 6238 section 5.2 requires a previously accepted code to be rejected.
 *
 * <p>Implementations must make the check and the claim a single atomic operation, otherwise two
 * concurrent requests carrying the same code can both succeed.
 */
public interface TotpReplayGuard {

    /**
     * Atomically records a code as used for this user.
     *
     * @return true if the code was claimed (it had not been used), false if it was already used
     */
    boolean claimCode(UUID userId, String code);
}
