package dev.sandbox.lab.taskapi.web;

import jakarta.validation.constraints.NotBlank;

// Java records (16+) are close to C# records: list the fields once and get a constructor,
// accessors, equals/hashCode/toString for free. The one real gap: no built-in `with`
// expression - a copy-with-a-change is a method you write yourself if you need one.
public record CreateTaskRequest(@NotBlank String title) {
}
