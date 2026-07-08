package dev.sandbox.lab.searchapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// @Configuration + @Bean is Spring's version of registering a typed client in Program.cs
// (services.AddHttpClient(...) or a manually built HttpClient singleton). RestClient itself
// (Spring Framework 6.1+) is Spring's modern synchronous HTTP client - conceptually close to
// .NET's HttpClient, with a more fluent, WebClient-flavored builder API.
@Configuration
public class OpenSearchClientConfig {
    @Bean
    public RestClient openSearchClient(@Value("${opensearch.base-url}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
