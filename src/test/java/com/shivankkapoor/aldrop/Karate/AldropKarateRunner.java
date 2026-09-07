package com.shivankkapoor.aldrop.Karate;

import com.intuit.karate.junit5.Karate;

/**
 * Deliberately not named *Test/*Tests so Surefire's default include
 * pattern skips it during `mvn test` (CI). These hit a live aldrop
 * instance on localhost:4000 plus the real LAN Postgres/Redis, so
 * run manually (e.g. `./mvnw test -Dtest=AldropKarateRunner`) with
 * the app already started. Both feature paths are run from a single
 * Karate.run(...) call, deliberately, so they share one Suite and
 * karate-summary.html/json aggregates both instead of the last one
 * run overwriting the other's summary.
 */
class AldropKarateRunner {

    @Karate.Test
    Karate allFlows() {
        return Karate.run("auth-flow", "totp-flow", "device-binding-flow").relativeTo(getClass());
    }
}
