Feature: Task status workflow
  Service-level tests against a running task-api instance - black-box, over HTTP, the same way
  a QA suite would exercise a deployed service. Not a replacement for the JUnit unit tests under
  app/src/test; those check the code from the inside, these check the API from the outside.

  Scenario: Creating a task starts it in TODO
    When I create a task titled "Schedule annual wellness visit"
    Then the task status should be "TODO"

  Scenario: A task can move from TODO to IN_PROGRESS
    Given I have created a task titled "Refill prescription"
    When I move the task to status "IN_PROGRESS"
    Then the task status should be "IN_PROGRESS"

  Scenario: A task cannot skip directly from TODO to DONE
    Given I have created a task titled "Refill prescription"
    When I attempt to move the task to status "DONE"
    Then the request should fail with status 409

  Scenario: A task can move from IN_PROGRESS to DONE
    Given I have created a task titled "Refill prescription"
    And I have moved the task to status "IN_PROGRESS"
    When I move the task to status "DONE"
    Then the task status should be "DONE"

  Scenario: Fetching an unknown task returns 404
    When I fetch a task with an unknown id
    Then the request should fail with status 404

  Scenario: Deleting a task removes it
    Given I have created a task titled "Temporary task"
    When I delete the task
    Then fetching the task should fail with status 404
