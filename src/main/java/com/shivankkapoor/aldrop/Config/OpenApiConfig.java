package com.shivankkapoor.aldrop.Config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the two credentials Aldrop accepts, so the docs say which one belongs on which endpoint.
 *
 * <p>{@code platformAdmin} is the operator credential for managing platforms. {@code platformApiKey}
 * is the per-platform key a consuming site uses to act on behalf of its own users.
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Aldrop",
        version = "0.0.1",
        description = """
                Centralised authentication for other platforms. Each platform authenticates with its \
                own API key and gets an isolated pool of users, so the same username can exist \
                independently on different platforms.

                Sessions are opaque server-side tokens rather than JWTs, which is what makes \
                revocation immediate: deleting the row ends the session. Pass the token back to \
                /auth/validate on each request to check it.

                Endpoints under /platform are operator-only and use HTTP Basic. Endpoints under \
                /auth are for platforms and use the platform's API key as a bearer token."""))
@SecurityScheme(
        name = "platformAdmin",
        type = SecuritySchemeType.HTTP,
        scheme = "basic",
        description = "Operator credentials. Required by every /platform endpoint.")
@SecurityScheme(
        name = "platformApiKey",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        in = SecuritySchemeIn.HEADER,
        description = "A platform's API key, sent as `Authorization: Bearer <apiKey>`. "
                + "Issued by POST /platform/create and replaced by PATCH /platform/{id}/rotate-key. "
                + "Required by every /auth endpoint, and it determines which platform's user pool is used.")
public class OpenApiConfig {
}
