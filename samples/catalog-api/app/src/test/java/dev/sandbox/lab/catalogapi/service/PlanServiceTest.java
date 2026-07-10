package dev.sandbox.lab.catalogapi.service;

import dev.sandbox.lab.catalogapi.domain.Plan;
import dev.sandbox.lab.catalogapi.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// PlanRepository is an interface, the more familiar shape for a C# dev used to mocking an
// interface with Moq - @Mock works the same way here as it does against ProcessedEventStore's
// concrete class in the events-api tests.
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {
    @Mock
    private PlanRepository repository;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(repository);
    }

    @Test
    void createSavesANewPlanWithAGeneratedId() {
        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Plan created = service.create("Bronze", new BigDecimal("250.00"));

        assertThat(created.getId()).isNotBlank();
        assertThat(captor.getValue().getName()).isEqualTo("Bronze");
        assertThat(captor.getValue().getMonthlyPremium()).isEqualByComparingTo("250.00");
    }

    @Test
    void updateOnAnExistingPlanReturnsTheUpdatedPlan() {
        when(repository.updateIfExists(any(Plan.class)))
                .thenReturn(Optional.of(new Plan("plan-1", "Silver", new BigDecimal("300.00"))));

        Plan result = service.update("plan-1", "Silver", new BigDecimal("300.00"));

        assertThat(result.getId()).isEqualTo("plan-1");
        assertThat(result.getName()).isEqualTo("Silver");
        assertThat(result.getMonthlyPremium()).isEqualByComparingTo("300.00");
    }

    // Regression guard for the old get-then-save shape: update() now resolves existence from
    // the same conditional write instead of a separate findById lookup beforehand, so this
    // should throw off a single repository call, not two.
    @Test
    void updateOnAMissingPlanThrowsWithoutASeparateExistenceCheck() {
        when(repository.updateIfExists(any(Plan.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("missing", "Silver", new BigDecimal("300.00")))
                .isInstanceOf(PlanNotFoundException.class);

        verify(repository, never()).findById(any());
    }

    @Test
    void deleteChecksExistenceThenRemoves() {
        when(repository.findById("plan-1"))
                .thenReturn(Optional.of(new Plan("plan-1", "Bronze", new BigDecimal("250.00"))));

        service.delete("plan-1");

        verify(repository).deleteById("plan-1");
    }

    @Test
    void deleteOnMissingPlanThrowsAndNeverCallsDeleteById() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(PlanNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }
}
