package dev.sandbox.lab.claimsintakeapi.service;

import dev.sandbox.lab.claimsintakeapi.domain.ClaimRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// ClaimRowParser is a pure static method (no collaborators to mock), so this is plain JUnit +
// AssertJ - no MockitoExtension needed, unlike EventConsumerTest/PlanServiceTest.
class ClaimRowParserTest {
    @Test
    void validRowIsParsedWithStrippedFields() {
        Optional<ClaimRow> result = ClaimRowParser.parse(
                " CLM-000001 , MBR-0001 ,2024-01-15, Annual wellness visit ,  182.50 ");

        assertThat(result).isPresent();
        ClaimRow row = result.get();
        assertThat(row.claimId()).isEqualTo("CLM-000001");
        assertThat(row.memberId()).isEqualTo("MBR-0001");
        assertThat(row.serviceDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(row.serviceDescription()).isEqualTo("Annual wellness visit");
        assertThat(row.billedAmount()).isEqualByComparingTo("182.50");
    }

    @Test
    void billedAmountExactlyAtTheSaneMaximumIsValid() {
        Optional<ClaimRow> result = ClaimRowParser.parse("CLM-1,MBR-1,2024-01-01,Checkup,50000.00");

        assertThat(result).isPresent();
        assertThat(result.get().billedAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void billedAmountOneCentOverTheSaneMaximumIsRejected() {
        assertThat(ClaimRowParser.parse("CLM-1,MBR-1,2024-01-01,Checkup,50000.01")).isEmpty();
    }

    @Test
    void serviceDateOfTodayIsValid() {
        String today = LocalDate.now().toString();

        assertThat(ClaimRowParser.parse("CLM-1,MBR-1," + today + ",Checkup,50.00")).isPresent();
    }

    @Test
    void futureServiceDateIsRejected() {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        assertThat(ClaimRowParser.parse("CLM-1,MBR-1," + tomorrow + ",Checkup,50.00")).isEmpty();
    }

    // Regression test for the fix in commit cf07b52: "1e-2147483600" used to parse successfully
    // (BigDecimal's constructor accepts it, and compareTo()'s fast path doesn't need to
    // materialize the value, so the old code let it through as "valid") but crashed the whole
    // request later with an unchecked ArithmeticException the moment anything tried to
    // .add() it to a running total. This must now be rejected here, at parse time, before it
    // can ever reach arithmetic code downstream.
    @Test
    void pathologicalScaleThatWouldOverflowLaterArithmeticIsRejected() {
        assertThat(ClaimRowParser.parse("CLM-1,MBR-1,2024-01-01,Checkup,1e-2147483600")).isEmpty();
    }

    // @ParameterizedTest + @MethodSource is JUnit 5's equivalent of xUnit's [Theory] with
    // [MemberData] - one test body run once per supplied case, instead of xUnit's per-case
    // [InlineData] attributes on the method itself.
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("rejectedRows")
    void invalidRowsAreRejected(String reason, String csvLine) {
        assertThat(ClaimRowParser.parse(csvLine)).as(reason).isEmpty();
    }

    private static Stream<Arguments> rejectedRows() {
        return Stream.of(
                Arguments.of("too few columns", "CLM-1,MBR-1,2024-01-01,Checkup"),
                Arguments.of("too many columns", "CLM-1,MBR-1,2024-01-01,Checkup,50.00,extra"),
                Arguments.of("blank claimId", ",MBR-1,2024-01-01,Checkup,50.00"),
                Arguments.of("whitespace-only claimId", "   ,MBR-1,2024-01-01,Checkup,50.00"),
                Arguments.of("blank memberId", "CLM-1,,2024-01-01,Checkup,50.00"),
                Arguments.of("blank serviceDescription", "CLM-1,MBR-1,2024-01-01,,50.00"),
                Arguments.of("unparseable date", "CLM-1,MBR-1,not-a-date,Checkup,50.00"),
                Arguments.of("unparseable billedAmount", "CLM-1,MBR-1,2024-01-01,Checkup,not-a-number"),
                Arguments.of("zero billedAmount", "CLM-1,MBR-1,2024-01-01,Checkup,0.00"),
                Arguments.of("negative billedAmount", "CLM-1,MBR-1,2024-01-01,Checkup,-10.00")
        );
    }
}
