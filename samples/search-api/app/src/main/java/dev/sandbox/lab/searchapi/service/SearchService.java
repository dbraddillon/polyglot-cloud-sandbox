package dev.sandbox.lab.searchapi.service;

import dev.sandbox.lab.searchapi.web.DocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchService {
    private static final String INDEX = "documents";

    private final RestClient client;

    public SearchService(RestClient client) {
        this.client = client;
    }

    public DocumentResponse index(String title, String body) {
        String id = UUID.randomUUID().toString();
        // PUT .../documents/_doc/{id} - OpenSearch (like Elasticsearch) auto-creates the index
        // and infers a field mapping from the first document it sees ("dynamic mapping"). No
        // schema migration step, unlike the claims-api sample's Postgres/JPA setup - the
        // tradeoff is you find out about type mismatches at query time, not at deploy time.
        client.put()
                .uri("/{index}/_doc/{id}", INDEX, id)
                .body(new OpenSearchModels.DocumentSource(title, body))
                .retrieve()
                .toBodilessEntity();
        return new DocumentResponse(id, title, body);
    }

    public DocumentResponse get(String id) {
        try {
            OpenSearchModels.GetResponse response = client.get()
                    .uri("/{index}/_doc/{id}", INDEX, id)
                    .retrieve()
                    .body(OpenSearchModels.GetResponse.class);
            return new DocumentResponse(id, response.source().title(), response.source().body());
        } catch (HttpClientErrorException.NotFound e) {
            throw new DocumentNotFoundException(id);
        }
    }

    public List<DocumentResponse> search(String query) {
        // multi_match searches across both fields at once - the search-engine equivalent of a
        // SQL `WHERE title ILIKE '%q%' OR body ILIKE '%q%'`, but backed by a real inverted-index
        // text search instead of a table scan.
        Map<String, Object> searchBody = Map.of(
                "query", Map.of(
                        "multi_match", Map.of(
                                "query", query,
                                "fields", List.of("title", "body"))));

        OpenSearchModels.SearchResponse response = client.post()
                .uri("/{index}/_search", INDEX)
                .body(searchBody)
                .retrieve()
                .body(OpenSearchModels.SearchResponse.class);

        if (response == null) {
            return List.of();
        }
        return response.hits().hits().stream()
                .map(hit -> new DocumentResponse(hit.id(), hit.source().title(), hit.source().body()))
                .toList();
    }
}
