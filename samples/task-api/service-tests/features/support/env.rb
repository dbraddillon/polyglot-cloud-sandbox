require "httparty"

# Cucumber's "World" module - instance methods here become available inside every step
# definition, the same role a JUnit/Cucumber-JVM test's shared instance fields play, or a
# SpecFlow ScenarioContext in the closest C# equivalent (SpecFlow *is* Cucumber for .NET - same
# Gherkin .feature files, step definitions in C# instead of Ruby).
module TaskApiWorld
  BASE_URL = ENV.fetch("TASK_API_URL", "http://localhost:8080")

  attr_accessor :response, :task_id
end

World(TaskApiWorld)
