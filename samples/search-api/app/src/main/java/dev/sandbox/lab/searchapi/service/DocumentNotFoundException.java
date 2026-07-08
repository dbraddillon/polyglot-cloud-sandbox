package dev.sandbox.lab.searchapi.service;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String id) {
        super("Document not found: " + id);
    }
}
