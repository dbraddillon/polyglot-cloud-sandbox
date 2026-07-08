package dev.sandbox.lab.taskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication bundles component scanning + autoconfiguration + a bean definition for
// this class itself - roughly the same territory as `WebApplication.CreateBuilder(args)` wiring
// in ASP.NET Core's Program.cs, condensed into one annotation plus this one-line main method.
@SpringBootApplication
public class TaskApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
