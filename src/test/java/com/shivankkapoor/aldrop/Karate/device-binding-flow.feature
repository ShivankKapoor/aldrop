Feature: device binding enforcement against a live aldrop instance

Background:
    * url baseUrl
    * def Base64 = Java.type('java.util.Base64')
    * def credentials = adminUsername + ':' + adminPassword
    * def adminAuth = 'Basic ' + Base64.getEncoder().encodeToString(credentials.getBytes())
    * def randomSuffix = java.util.UUID.randomUUID() + ''
    * def ipAddress = '203.0.113.5'
    * def userAgent = 'karate-test-agent'

    # each scenario gets its own throwaway platform, with device binding required, deleted in afterScenario below
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-device-" + randomSuffix)', requireDeviceBinding: true }
    When method post
    Then status 201
    * def platformId = response.id
    * def platformAuth = 'Bearer ' + response.apiKey

    * def username = 'oscar-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    * configure afterScenario =
        """
        function(){
            karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: platformId });
        }
        """

Scenario: login rejects missing device info when the platform requires device binding
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 400

Scenario: login succeeds with device info and validate succeeds with matching device info
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    And match response.token == '#present'
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    And match response.userId == '#present'

Scenario: validate rejects missing device info even for an otherwise-valid session
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 400

Scenario: validate rejects a mismatched ip address
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)', ipAddress: '198.51.100.9', userAgent: '#(userAgent)' }
    When method post
    Then status 401

Scenario: validate rejects a mismatched user agent
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)', ipAddress: '#(ipAddress)', userAgent: 'a-different-agent' }
    When method post
    Then status 401

Scenario: login rejects a malformed ip address
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: 'not-an-ip', userAgent: '#(userAgent)' }
    When method post
    Then status 400

Scenario: login and validate succeed without device info when the platform does not require device binding
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-nodevice-" + randomSuffix)' }
    When method post
    Then status 201
    * def noBindingPlatformId = response.id
    * def noBindingAuth = 'Bearer ' + response.apiKey

    Given path 'auth/register'
    And header Authorization = noBindingAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = noBindingAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = noBindingAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: noBindingPlatformId })

Scenario: verify-totp requires device info and the resulting session enforces it on validate
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

    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-device-totp-" + randomSuffix)', requireDeviceBinding: true, totpAvailable: true }
    When method post
    Then status 201
    * def totpPlatformId = response.id
    * def totpPlatformAuth = 'Bearer ' + response.apiKey

    Given path 'auth/register'
    And header Authorization = totpPlatformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    # totp is not enabled yet, so this first login still needs device info to create a session
    Given path 'auth/login'
    And header Authorization = totpPlatformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def enableToken = response.token

    Given path 'auth/totp/enable'
    And header Authorization = totpPlatformAuth
    And request { token: '#(enableToken)' }
    When method post
    Then status 200
    * def secret = response.secret

    Given path 'auth/totp/confirm'
    And header Authorization = totpPlatformAuth
    And request { token: '#(enableToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 200

    Given path 'auth/login'
    And header Authorization = totpPlatformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def totpToken = response.totpToken

    # verify-totp is the call that actually creates the session, so it needs device info too
    Given path 'auth/login/verify-totp'
    And header Authorization = totpPlatformAuth
    And request { totpToken: '#(totpToken)', code: '#(totpCode(secret))' }
    When method post
    Then status 400

    Given path 'auth/login'
    And header Authorization = totpPlatformAuth
    And request { username: '#(username)', password: '#(password)', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def totpTokenTwo = response.totpToken

    Given path 'auth/login/verify-totp'
    And header Authorization = totpPlatformAuth
    And request { totpToken: '#(totpTokenTwo)', code: '#(totpCode(secret))', ipAddress: '#(ipAddress)', userAgent: '#(userAgent)' }
    When method post
    Then status 200
    * def sessionToken = response.token

    Given path 'auth/validate'
    And header Authorization = totpPlatformAuth
    And request { token: '#(sessionToken)', ipAddress: '198.51.100.9', userAgent: '#(userAgent)' }
    When method post
    Then status 401

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: totpPlatformId })
