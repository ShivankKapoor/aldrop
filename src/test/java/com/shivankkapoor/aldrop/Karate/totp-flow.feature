Feature: TOTP two-factor flow against a live aldrop instance

Background:
    * url baseUrl
    * def Base64 = Java.type('java.util.Base64')
    * def credentials = adminUsername + ':' + adminPassword
    * def adminAuth = 'Basic ' + Base64.getEncoder().encodeToString(credentials.getBytes())
    * def randomSuffix = java.util.UUID.randomUUID() + ''

    * def System = Java.type('java.lang.System')
    * def DefaultCodeGenerator = Java.type('dev.samstevens.totp.code.DefaultCodeGenerator')
    * def codeGenerator = new DefaultCodeGenerator()
    * def issuedCounters = {}
    # A TOTP code is only unique per 30 second time step, and the server rejects a code it has
    # already accepted (RFC 6238 section 5.2). Asking for a second code inside the same step would
    # hand back the same digits and be refused as a replay, so step forward instead: the server
    # allows a discrepancy of one step, so a code for the next step is already valid. Where a
    # scenario does not care which second factor it uses, prefer a backup code over calling this
    # twice. Near a step boundary, wait out the remainder first, otherwise the next step would have
    # become the current one by the time the server checks and the code would be two steps ahead.
    * def totpCode =
        """
        function(secret){
            var counter = Math.floor(System.currentTimeMillis() / 1000 / 30);
            if (issuedCounters[secret] === counter) {
                var millisLeftInStep = ((counter + 1) * 30000) - System.currentTimeMillis();
                if (millisLeftInStep < 2000) {
                    java.lang.Thread.sleep(millisLeftInStep + 250);
                    counter = Math.floor(System.currentTimeMillis() / 1000 / 30);
                } else {
                    counter = counter + 1;
                }
            }
            issuedCounters[secret] = counter;
            return codeGenerator.generate(secret, counter);
        }
        """
    * def wrongCode =
        """
        function(secret){
            var counter = Math.floor(System.currentTimeMillis() / 1000 / 30);
            var correct = parseInt(codeGenerator.generate(secret, counter), 10);
            var wrong = (correct + 1) % 1000000;
            var padded = '' + wrong;
            while (padded.length < 6) { padded = '0' + padded; }
            return padded;
        }
        """

    # each scenario gets its own throwaway platform, with totp available, deleted in afterScenario below
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-totp-" + randomSuffix)', maxSessionsPerUser: 2, totpAvailable: true }
    When method post
    Then status 201
    * def platformId = response.id
    * def platformAuth = 'Bearer ' + response.apiKey

    * configure afterScenario =
        """
        function(){
            karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: platformId });
        }
        """

Scenario: a totp code cannot be used twice
    * def username = 'nina-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    # hold on to the exact code confirm consumes, so it can be replayed verbatim below
    * def reusedCode = totpCode(secret)

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(reusedCode)' }
    When method post
    Then status 200

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken = response.totpToken

    # the same code is still inside its validity window, but has already been accepted once
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(reusedCode)' }
    When method post
    Then status 401

    # a replay is a failed attempt, not a consumed challenge, so a fresh code still completes login
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    And match response.token == '#present'

Scenario: enable, confirm and login challenge issues a session after verify-totp
    * def username = 'erin-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    And match response.secret == '#present'
    And match response.otpAuthUri == '#present'
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    And match response.backupCodes == '#[8]'

    # totp is now enabled, so a plain login returns a challenge instead of a session
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    And match response.token == '#null'
    And match response.totpToken == '#present'
    * def totpToken = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    And match response.token == '#present'
    * def sessionToken = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(sessionToken)' }
    When method post
    Then status 200

    # the totp challenge token is single-use, reusing it is rejected
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 401

Scenario: confirm rejects a wrong code and totp stays disabled
    * def username = 'frank-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    # confirm never succeeded, so login still returns a direct session, no challenge
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    And match response.token == '#present'
    And match response.totpToken == '#null'

Scenario: login verify-totp rejects a wrong code without consuming the challenge
    * def username = 'grace-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    * def backupCode = response.backupCodes[0]

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    # a wrong attempt does not consume the challenge, a valid second factor still works after.
    # this scenario is about the challenge surviving, not about which factor completes it, so a
    # backup code is used rather than a second code from the step confirm already consumed.
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(backupCode)' }
    When method post
    Then status 200
    And match response.token == '#present'

Scenario: a backup code completes login once and is then rejected on reuse
    * def username = 'henry-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    * def backupCode = response.backupCodes[0]

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpTokenOne = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpTokenOne)', code: '#(backupCode)' }
    When method post
    Then status 200
    And match response.token == '#present'

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpTokenTwo = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpTokenTwo)', code: '#(backupCode)' }
    When method post
    Then status 401

Scenario: enable is rejected when the platform does not have totp available
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-totp-unavailable-" + randomSuffix)', totpAvailable: false }
    When method post
    Then status 201
    * def noTotpPlatformId = response.id
    * def noTotpAuth = 'Bearer ' + response.apiKey

    * def username = 'ivan-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = noTotpAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = noTotpAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/totp/enable'
    And header Authorization = noTotpAuth
    And request { token: '#(token)' }
    When method post
    Then status 409

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: noTotpPlatformId })

Scenario: enable is rejected once totp is already enabled
    * def username = 'jane-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(totpCode(secret))' }
    When method post
    Then status 200

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 409

Scenario: verify-totp locks out the challenge after five failed attempts
    * def username = 'kate-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    # locked out now, even the correct code is rejected
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 401

Scenario: starting a new login invalidates the previous unconsumed totp challenge
    * def username = 'rosa-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200
    * def backupCodeOne = response.backupCodes[0]
    * def backupCodeTwo = response.backupCodes[1]

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpTokenOne = response.totpToken

    # a second login before verifying the first invalidates it
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpTokenTwo = response.totpToken

    # backup codes rather than totp codes: this scenario is about which challenge is still live,
    # and a replayed totp code would return 401 on the first call for the wrong reason, hiding
    # the invalidation it exists to prove. backup codes are single use, so two are needed.
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpTokenOne)', code: '#(backupCodeOne)' }
    When method post
    Then status 401

    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpTokenTwo)', code: '#(backupCodeTwo)' }
    When method post
    Then status 200

Scenario: verify-totp per-user rate limit persists across separate login challenges
    * def username = 'sam-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def firstToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(firstToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200

    # five wrong attempts, each against its own fresh challenge, still count against
    # the per-user limit even though no single challenge sees more than one attempt
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken1 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken1)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken2 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken2)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken3 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken3)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken4 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken4)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken5 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken5)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    # a sixth fresh challenge is still rate limited, even with the correct code
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def totpToken6 = response.totpToken
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken6)', code: '#(totpCode(secret))' }
    When method post
    Then status 429

Scenario: totp enable and confirm reject an invalid session token
    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#("garbage-token-" + randomSuffix)' }
    When method post
    Then status 401

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#("garbage-token-" + randomSuffix)', code: '123456' }
    When method post
    Then status 401

Scenario: totp enable is rate limited after five calls
    * def username = 'liam-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    # sixth call within the window is rate limited
    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 429

Scenario: totp confirm is rate limited after five attempts
    * def username = 'mona-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/totp/enable'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(wrongCode(secret))' }
    When method post
    Then status 401

    # sixth attempt within the window is rate limited, even with the correct code
    Given path 'auth/totp/confirm'
    And header Authorization = platformAuth
    And request { token: '#(token)', code: '#(totpCode(secret))' }
    When method post
    Then status 429
