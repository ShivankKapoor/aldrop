package com.shivankkapoor.aldrop.Karate;

import com.intuit.karate.junit5.Karate;

/**
 * Deliberately not named *Test/*Tests so Surefire's default include
 * pattern skips it during `mvn test` (CI). These hit a live aldrop
 * instance on localhost:4000 plus the real LAN Postgres/Redis, so
 * run manually (e.g. `./mvnw test -Dtest=AuthFlowKarateRunner`)
 * with the app already started.
 */
class AuthFlowKarateRunner {

    @Karate.Test
    Karate authFlow() {
        return Karate.run("auth-flow").relativeTo(getClass());
    }
}
