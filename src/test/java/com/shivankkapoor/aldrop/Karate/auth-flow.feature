Feature: auth lifecycle against a live aldrop instance

Background:
    * url baseUrl
    * def Base64 = Java.type('java.util.Base64')
    * def credentials = adminUsername + ':' + adminPassword
    * def adminAuth = 'Basic ' + Base64.getEncoder().encodeToString(credentials.getBytes())
    * def randomSuffix = java.util.UUID.randomUUID() + ''
    * def Thread = Java.type('java.lang.Thread')

    # each scenario gets its own throwaway platform, deleted in afterScenario below
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-" + randomSuffix)', maxSessionsPerUser: 2 }
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

Scenario: register, login, validate and logout
    * def username = 'alice-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201
    And match response.username == username

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    And match response.token == '#present'
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200
    And match response.userId == '#present'

    Given path 'auth/logout'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 204

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 401

Scenario: login rejects a wrong password
    * def username = 'bob-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: 'wrongpassword' }
    When method post
    Then status 401

Scenario: logout-all revokes every session for the user
    * def username = 'carol-' + randomSuffix
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
    * def tokenOne = response.token

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def tokenTwo = response.token

    Given path 'auth/logout-all'
    And header Authorization = platformAuth
    And request { token: '#(tokenOne)' }
    When method post
    Then status 204

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(tokenOne)' }
    When method post
    Then status 401

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(tokenTwo)' }
    When method post
    Then status 401

Scenario: max sessions per platform evicts the oldest session
    * def username = 'dave-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    # platform was created with maxSessionsPerUser = 2
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def tokenOne = response.token

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def tokenTwo = response.token

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def tokenThree = response.token

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(tokenOne)' }
    When method post
    Then status 401

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(tokenTwo)' }
    When method post
    Then status 200

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(tokenThree)' }
    When method post
    Then status 200

Scenario: register rejects a duplicate username on the same platform
    * def username = 'eve-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: 'someotherpassword' }
    When method post
    Then status 409

Scenario: register rejects a blank username and a too-short password
    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '', password: 'correcthorse123' }
    When method post
    Then status 400

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#("frank-" + randomSuffix)', password: 'short' }
    When method post
    Then status 400

Scenario: platform create rejects a duplicate name
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-" + randomSuffix)' }
    When method post
    Then status 409

Scenario: auth endpoints reject a missing or garbage platform api key
    Given path 'auth/register'
    And request { username: '#("grace-" + randomSuffix)', password: 'correcthorse123' }
    When method post
    Then status 401

    Given path 'auth/register'
    And header Authorization = 'Bearer garbage-key-' + randomSuffix
    And request { username: '#("grace-" + randomSuffix)', password: 'correcthorse123' }
    When method post
    Then status 401

Scenario: platform admin endpoints reject missing or wrong admin auth
    Given path 'platform/create'
    And request { name: '#("karate-noauth-" + randomSuffix)' }
    When method post
    Then status 401

    Given path 'platform/create'
    And header Authorization = 'Basic ' + Base64.getEncoder().encodeToString(('wrong:' + randomSuffix).getBytes())
    And request { name: '#("karate-wrongauth-" + randomSuffix)' }
    When method post
    Then status 401

Scenario: a platform's api key cannot validate another platform's session token
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-other-" + randomSuffix)' }
    When method post
    Then status 201
    * def otherPlatformId = response.id
    * def otherPlatformAuth = 'Bearer ' + response.apiKey

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
    * def token = response.token

    Given path 'auth/validate'
    And header Authorization = otherPlatformAuth
    And request { token: '#(token)' }
    When method post
    Then status 401

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: otherPlatformId })

Scenario: login and validate are rejected while the platform is deactivated
    * def username = 'ivy-' + randomSuffix
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

    Given path 'platform', platformId, 'status'
    And header Authorization = adminAuth
    And request { isActive: false }
    When method patch
    Then status 200

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 401

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 401

Scenario: validate rejects a token after the session ttl has expired
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-ttl-" + randomSuffix)', sessionTtl: 'PT1S' }
    When method post
    Then status 201
    * def ttlPlatformId = response.id
    * def ttlPlatformAuth = 'Bearer ' + response.apiKey

    * def username = 'jack-' + randomSuffix
    * def password = 'correcthorse123'

    Given path 'auth/register'
    And header Authorization = ttlPlatformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth/login'
    And header Authorization = ttlPlatformAuth
    And request { username: '#(username)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.token

    * Thread.sleep(1300)

    Given path 'auth/validate'
    And header Authorization = ttlPlatformAuth
    And request { token: '#(token)' }
    When method post
    Then status 401

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: ttlPlatformId })

Scenario: validate rejects a blank token and an unknown token
    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '' }
    When method post
    Then status 400

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#("garbage-token-" + randomSuffix)' }
    When method post
    Then status 401

Scenario: logout and logout-all are idempotent for an unknown token
    Given path 'auth/logout'
    And header Authorization = platformAuth
    And request { token: '#("garbage-token-" + randomSuffix)' }
    When method post
    Then status 204

    Given path 'auth/logout-all'
    And header Authorization = platformAuth
    And request { token: '#("garbage-token-" + randomSuffix)' }
    When method post
    Then status 204

Scenario: a platform's api key cannot logout another platform's session token
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-other-" + randomSuffix)' }
    When method post
    Then status 201
    * def otherPlatformId = response.id
    * def otherPlatformAuth = 'Bearer ' + response.apiKey

    * def username = 'kim-' + randomSuffix
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

    Given path 'auth/logout'
    And header Authorization = otherPlatformAuth
    And request { token: '#(token)' }
    When method post
    Then status 204

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: otherPlatformId })

Scenario: register is rejected while the platform is deactivated
    Given path 'platform', platformId, 'status'
    And header Authorization = adminAuth
    And request { isActive: false }
    When method patch
    Then status 200

    Given path 'auth/register'
    And header Authorization = platformAuth
    And request { username: '#("liam-" + randomSuffix)', password: 'correcthorse123' }
    When method post
    Then status 401

Scenario: platform status update reactivates, rejects an unknown id and an invalid body
    Given path 'platform', platformId, 'status'
    And header Authorization = adminAuth
    And request { isActive: false }
    When method patch
    Then status 200
    And match response.active == false

    Given path 'platform', platformId, 'status'
    And header Authorization = adminAuth
    And request { isActive: true }
    When method patch
    Then status 200
    And match response.active == true

    Given path 'platform', java.util.UUID.randomUUID() + '', 'status'
    And header Authorization = adminAuth
    And request { isActive: true }
    When method patch
    Then status 404

    Given path 'platform', platformId, 'status'
    And header Authorization = adminAuth
    And request {}
    When method patch
    Then status 400

Scenario: delete rejects an unknown platform id
    Given path 'platform', java.util.UUID.randomUUID() + ''
    And header Authorization = adminAuth
    When method delete
    Then status 404

Scenario: monitor endpoint is reachable
    Given path 'monitor'
    When method get
    Then status 200
    And match response.status == 'Up'

Scenario: login rejects a nonexistent username
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#("nobody-" + randomSuffix)', password: 'correcthorse123' }
    When method post
    Then status 401

Scenario: login rejects a blank username and a blank password
    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '', password: 'correcthorse123' }
    When method post
    Then status 400

    Given path 'auth/login'
    And header Authorization = platformAuth
    And request { username: '#("mia-" + randomSuffix)', password: '' }
    When method post
    Then status 400

Scenario: platform create rejects a blank name
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '' }
    When method post
    Then status 400

Scenario: a platform's api key cannot logout-all another platform's session token
    Given path 'platform/create'
    And header Authorization = adminAuth
    And request { name: '#("karate-other-" + randomSuffix)' }
    When method post
    Then status 201
    * def otherPlatformId = response.id
    * def otherPlatformAuth = 'Bearer ' + response.apiKey

    * def username = 'noah-' + randomSuffix
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

    Given path 'auth/logout-all'
    And header Authorization = otherPlatformAuth
    And request { token: '#(token)' }
    When method post
    Then status 204

    Given path 'auth/validate'
    And header Authorization = platformAuth
    And request { token: '#(token)' }
    When method post
    Then status 200

    * karate.call('cleanup-platform.feature', { baseUrl: baseUrl, adminAuth: adminAuth, platformId: otherPlatformId })
