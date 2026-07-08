package dev.sandbox.lab.searchapi.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// These mirror just enough of OpenSearch's own JSON response shapes to deserialize what we
// need. @JsonIgnoreProperties(ignoreUnknown = true) means Jackson won't blow up on the dozen
// other fields OpenSearch returns that we don't care about - closer to System.Text.Json's
// default "ignore extra members" behavior than to a strict contract type.
//
// Kept separate from the DTOs in the `web` package on purpose: what a third-party API hands
// back and what your own API promises callers are two different contracts, even when today
// they happen to look almost identical.
final class OpenSearchModels {
    private OpenSearchModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DocumentSource(String title, String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetResponse(@JsonProperty("_source") DocumentSource source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hit(@JsonProperty("_id") String id, @JsonProperty("_source") DocumentSource source) {
    }

    // Yes, "hits.hits" really is the shape OpenSearch/Elasticsearch use - not a typo here.
    // The outer object carries metadata (total count, max score); the inner list is the
    // actual matches.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record HitsWrapper(List<Hit> hits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(HitsWrapper hits) {
    }
}
