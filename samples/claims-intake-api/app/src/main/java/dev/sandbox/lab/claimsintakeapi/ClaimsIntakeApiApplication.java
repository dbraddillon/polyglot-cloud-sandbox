package dev.sandbox.lab.claimsintakeapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on Spring's @Scheduled method support - without it, the Kinesis
// consumer's @Scheduled poll() method just never runs. Closest .NET parallel: registering a
// BackgroundService/IHostedService in Program.cs, except here it's one annotation instead of an
// explicit services.AddHostedService<T>() call.
@EnableScheduling
@SpringBootApplication
public class ClaimsIntakeApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClaimsIntakeApiApplication.class, args);
    }
}
