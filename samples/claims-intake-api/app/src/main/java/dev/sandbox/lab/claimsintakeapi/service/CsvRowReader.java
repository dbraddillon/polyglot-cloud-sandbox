package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// The read-loop shared by both processing modes: skip the header, read one line at a time
// (never buffering the whole file), parse/validate each row, and hand valid ones to a callback
// while accumulating the running counts. StreamingBatchProcessor's callback writes the row
// straight to its output file; KinesisBatchProducer's callback batches it onto Kinesis - the
// loop and the accounting are identical either way, only "what happens to a valid row" differs.
final class CsvRowReader {
    private CsvRowReader() {
    }

    // A functional interface just for this callback - C#'s closest equivalent is a delegate
    // type (e.g. `Action<ClaimRow>`), except Java has no built-in one that declares a checked
    // exception, which ClaimOutputWriter.writeRow needs to propagate (it does real file I/O).
    @FunctionalInterface
    interface ValidRowHandler {
        void onValidRow(ClaimRow row) throws IOException;
    }

    record Result(long rowsRead, long validRows, long invalidRows, BigDecimal totalBilledAmount) {
    }

    static Result read(InputStream csv, ValidRowHandler handler) throws IOException {
        long rowsRead = 0;
        long invalidRows = 0;
        BigDecimal totalBilledAmount = BigDecimal.ZERO;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
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
                handler.onValidRow(row.get());
                totalBilledAmount = totalBilledAmount.add(row.get().billedAmount());
            }
        }

        return new Result(rowsRead, rowsRead - invalidRows, invalidRows, totalBilledAmount);
    }
}
