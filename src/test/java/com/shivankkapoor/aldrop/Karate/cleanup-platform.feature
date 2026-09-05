Feature: delete a platform created by another feature, for test cleanup

Scenario:
    Given url baseUrl
    And path 'platform', platformId
    And header Authorization = adminAuth
    When method delete
    Then status 204
