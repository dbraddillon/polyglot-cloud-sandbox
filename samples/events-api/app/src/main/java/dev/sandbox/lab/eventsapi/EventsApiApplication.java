package dev.sandbox.lab.eventsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling turns on Spring's @Scheduled method support - without it, EventConsumer's
// @Scheduled poll() method just never runs. Closest .NET parallel: registering a
// BackgroundService/IHostedService in Program.cs, except here it's one annotation instead of an
// explicit services.AddHostedService<T>() call.
@EnableScheduling
@SpringBootApplication
public class EventsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventsApiApplication.class, args);
    }
}
