require "httparty"
require "rspec/expectations"
require "json"

BASE_URL = TaskApiWorld::BASE_URL

When("I create a task titled {string}") do |title|
  self.response = HTTParty.post("#{BASE_URL}/tasks",
                                 body: { title: title }.to_json,
                                 headers: { "Content-Type" => "application/json" })
  self.task_id = JSON.parse(response.body)["id"] if response.code == 201
end

# Given/When are interchangeable in Cucumber (both just call the matching regex/pattern step) -
# this file reuses "I create a task titled ..." under a Given wording purely for scenario
# readability, the same way SpecFlow lets a C# step definition's attribute apply to
# Given/When/And/But interchangeably too.
Given("I have created a task titled {string}") do |title|
  step %(I create a task titled "#{title}")
end

When("I move the task to status {string}") do |status|
  self.response = HTTParty.patch("#{BASE_URL}/tasks/#{task_id}/status",
                                  body: { status: status }.to_json,
                                  headers: { "Content-Type" => "application/json" })
end

Given("I have moved the task to status {string}") do |status|
  step %(I move the task to status "#{status}")
end

When("I attempt to move the task to status {string}") do |status|
  step %(I move the task to status "#{status}")
end

When("I fetch a task with an unknown id") do
  self.response = HTTParty.get("#{BASE_URL}/tasks/00000000-0000-0000-0000-000000000000")
end

When("I delete the task") do
  self.response = HTTParty.delete("#{BASE_URL}/tasks/#{task_id}")
end

Then("the task status should be {string}") do |status|
  expect([200, 201]).to include(response.code)
  expect(JSON.parse(response.body)["status"]).to eq(status)
end

Then("the request should fail with status {int}") do |code|
  expect(response.code).to eq(code)
end

Then("fetching the task should fail with status {int}") do |code|
  self.response = HTTParty.get("#{BASE_URL}/tasks/#{task_id}")
  expect(response.code).to eq(code)
end
