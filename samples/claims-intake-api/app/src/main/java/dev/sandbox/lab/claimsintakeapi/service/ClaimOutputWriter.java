package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Shared by both processing modes: the streaming path writes rows to this as it reads the
// upload directly; the Kinesis consumer writes rows to this as it drains the stream. Same
// output shape either way - one JSON object per line (JSON Lines / NDJSON), not a single JSON
// array, so a downstream reader can process it a line at a time without holding the whole file
// in memory either. A real analytics pipeline would likely want Parquet here instead (columnar,
// compressed, schema-carrying) - skipped in this sandbox to avoid pulling in a Parquet library
// purely for the output-format demo; JSON Lines proves out the same bounded-memory read/write
// story without the extra dependency.
class ClaimOutputWriter implements AutoCloseable {
    private final ObjectMapper mapper;
    private final BufferedWriter writer;
    private long rowsWritten = 0;

    ClaimOutputWriter(ObjectMapper mapper, Path outputPath) throws IOException {
        this.mapper = mapper;
        Files.createDirectories(outputPath.getParent());
        this.writer = Files.newBufferedWriter(outputPath);
    }

    void writeRow(ClaimRow row) throws IOException {
        writer.write(mapper.writeValueAsString(row));
        writer.newLine();
        rowsWritten++;
    }

    long rowsWritten() {
        return rowsWritten;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
