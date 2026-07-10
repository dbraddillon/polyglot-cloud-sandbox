package dev.sandbox.lab.claimsintakeapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sandbox.lab.claimsintakeapi.domain.BatchSummary;
import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

// The "we have limited resources but this is small enough not to bother with a queue" branch of
// the decision tree. Runs entirely on the request thread: reads the upload one line at a time
// (BufferedReader, never `file.getBytes()` / never buffering the whole CSV as a String or List),
// validates each row, and writes valid ones straight to the output file as it goes. Memory use
// stays flat whether the file is 10 rows or 10 million - the only reason this branch is reserved
// for *small* files is response latency (the caller's HTTP request blocks until this returns),
// not memory pressure. That's the actual boundary condition: bounded memory is free either way,
// but a slow synchronous write-out is only acceptable while it's also fast in wall-clock time.
@Service
public class StreamingBatchProcessor {
    private final ObjectMapper mapper;
    private final Path outputDir;

    public StreamingBatchProcessor(ObjectMapper mapper, @Value("${claims-intake.output-dir}") String outputDir) {
        this.mapper = mapper;
        this.outputDir = Path.of(outputDir);
    }

    public BatchSummary process(String batchId, InputStream csv) throws IOException {
        long rowsRead = 0;
        long invalidRows = 0;
        BigDecimal totalBilledAmount = BigDecimal.ZERO;

        Path outputPath = outputDir.resolve(batchId + ".jsonl");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
             ClaimOutputWriter output = new ClaimOutputWriter(mapper, outputPath)) {
            reader.readLine(); // header

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rowsRead++;
                Optional<ClaimRow> row = ClaimRowParser.parse(line);
                if (row.isEmpty()) {
                    invalidRows++;
                    continue;
                }
                output.writeRow(row.get());
                totalBilledAmount = totalBilledAmount.add(row.get().billedAmount());
            }

            return BatchSummary.streamed(batchId, rowsRead, output.rowsWritten(), invalidRows,
                    totalBilledAmount, outputPath.toString());
        }
    }
}
