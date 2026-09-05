Feature: auth lifecycle against a live aldrop instance

Background:
    * url baseUrl
    * def Base64 = Java.type('java.util.Base64')
    * def credentials = adminUsername + ':' + adminPassword
    * def adminAuth = 'Basic ' + Base64.getEncoder().encodeToString(credentials.getBytes())
    * def randomSuffix = java.util.UUID.randomUUID() + ''

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
