package dev.sandbox.lab.taskapi.domain;

// Enums work like C#'s here, with one real difference: Java enums are full classes under the
// hood (each constant can carry its own fields/methods) and don't implicitly convert to/from
// int the way C# enums do - no accidental `(TaskStatus) 3` style bugs.
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
