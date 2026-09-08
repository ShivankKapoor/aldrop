package com.shivankkapoor.aldrop.Validation;

/**
 * Maximum accepted lengths for request fields.
 *
 * <p>Without these, an oversized value is only stopped by whatever consumes it: a multi-megabyte
 * password would be handed to Argon2, which allocates 19MiB per hash, and an unbounded username
 * becomes an unbounded rate-limiter cache key. The limits are generous enough that no legitimate
 * client should meet them.
 */
public final class FieldLimits {

    /** Long enough for an email address, which is what most platforms will use as a username. */
    public static final int USERNAME_MAX = 255;

    public static final int PASSWORD_MIN = 8;

    /** Well past any human or password manager, while still bounding Argon2's input. */
    public static final int PASSWORD_MAX = 128;

    /** Session and TOTP challenge tokens are 32 random bytes, so about 43 characters. */
    public static final int TOKEN_MAX = 128;

    /** A TOTP code is 6 digits and a backup code is 8 characters. */
    public static final int CODE_MAX = 16;

    /** The longest possible IPv6 address, an IPv4-mapped one, is 45 characters. */
    public static final int IP_ADDRESS_MAX = 45;

    public static final int USER_AGENT_MAX = 512;

    public static final int PLATFORM_NAME_MAX = 100;

    private FieldLimits() {
    }
}
