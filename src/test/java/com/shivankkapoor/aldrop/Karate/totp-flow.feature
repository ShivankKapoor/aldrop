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
    * def totpCode =
        """
        function(secret){
            var counter = Math.floor(System.currentTimeMillis() / 1000 / 30);
            return codeGenerator.generate(secret, counter);
        }
        """
    * def wrongCode =
        """
        function(secret){
            var correct = parseInt(totpCode(secret), 10);
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

    # a wrong attempt does not consume the challenge, the correct code still works after
    Given path 'auth/login/verify-totp'
    And header Authorization = platformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
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
