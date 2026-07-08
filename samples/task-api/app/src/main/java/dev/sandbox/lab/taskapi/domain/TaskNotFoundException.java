package dev.sandbox.lab.taskapi.domain;

import java.util.UUID;

// Extends RuntimeException (unchecked) on purpose. Java also has *checked* exceptions (extend
// Exception directly) that force every method up the call stack to either catch them or declare
// `throws SomeException` - C# has nothing like it, every C# exception behaves like Java's
// unchecked ones. Checked exceptions are one of the more infamous surprises coming from C#, and
// most modern Java (Spring included) avoids them wherever it can.
public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) {
        super("Task not found: " + id);
    }
}
