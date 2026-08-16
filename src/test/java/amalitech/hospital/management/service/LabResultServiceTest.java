package amalitech.hospital.management.service;

import amalitech.hospital.management.aop.EventBus;
import amalitech.hospital.management.dto.lab.LabResultRequest;
import amalitech.hospital.management.dto.lab.LabResultResponse;
import amalitech.hospital.management.event.LabResultRecordedEvent;
import amalitech.hospital.management.exception.runtime.ConflictException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.lab.LabOrder;
import amalitech.hospital.management.model.lab.LabResult;
import amalitech.hospital.management.repository.lab.LabOrderRepository;
import amalitech.hospital.management.repository.lab.LabResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabResultServiceTest {

    @Mock private LabResultRepository labResultRepository;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private EventBus eventBus;

    private LabResultService labResultService;

    private LabOrder existingLabOrder;
    private LabResult existingResult;

    @BeforeEach
    void setUp() {
        labResultService = new LabResultService(labResultRepository, labOrderRepository, eventBus);

        existingLabOrder = new LabOrder();
        existingLabOrder.setLabOrderId("lab-1");

        existingResult = new LabResult();
        existingResult.setLabResultId("result-1");
        existingResult.setLabOrder(existingLabOrder);
        existingResult.setResultValue("5.2");
        existingResult.setUnit("mmol/L");
        existingResult.setIsAbnormal(false);
    }

    @Test
    void getResult_returnsMappedResponse_whenFoundAndActive() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(existingResult));

        LabResultResponse response = labResultService.getResult("lab-1");

        assertThat(response.getLabResultId()).isEqualTo("result-1");
        assertThat(response.getResultValue()).isEqualTo("5.2");
    }

    @Test
    void getResult_throwsNotFound_whenLabOrderAbsent() {
        when(labOrderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labResultService.getResult("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getResult_throwsNotFound_whenLabOrderSoftDeleted() {
        existingLabOrder.setDeletedAt(LocalDateTime.now());
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));

        assertThatThrownBy(() -> labResultService.getResult("lab-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getResult_throwsNotFound_whenNoResultRecordedYet() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labResultService.getResult("lab-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getResult_throwsNotFound_whenSoftDeleted() {
        existingResult.setDeletedAt(LocalDateTime.now());
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(existingResult));

        assertThatThrownBy(() -> labResultService.getResult("lab-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createResult_throwsNotFound_whenLabOrderAbsent() {
        when(labOrderRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labResultService.createResult("missing", new LabResultRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createResult_throwsConflict_whenResultAlreadyExists() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(existingResult));

        assertThatThrownBy(() -> labResultService.createResult("lab-1", new LabResultRequest()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createResult_savesSuccessfully_defaultingIsAbnormalToFalse() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.empty());
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));
        LabResultRequest request = new LabResultRequest();
        request.setResultValue("7.1");

        LabResultResponse response = labResultService.createResult("lab-1", request);

        assertThat(response.getLabOrderId()).isEqualTo("lab-1");
        assertThat(response.getIsAbnormal()).isFalse();
        verify(eventBus).publish(any(LabResultRecordedEvent.class));
    }

    @Test
    void updateResult_throwsNotFound_whenNoResultRecordedYet() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> labResultService.updateResult("lab-1", new LabResultRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateResult_updatesFields() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(existingResult));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));
        LabResultRequest request = new LabResultRequest();
        request.setResultValue("9.9");
        request.setIsAbnormal(true);

        LabResultResponse response = labResultService.updateResult("lab-1", request);

        assertThat(response.getResultValue()).isEqualTo("9.9");
        assertThat(response.getIsAbnormal()).isTrue();
    }

    @Test
    void deleteResult_setsDeletedAt() {
        when(labOrderRepository.findById("lab-1")).thenReturn(Optional.of(existingLabOrder));
        when(labResultRepository.findByLabOrder_LabOrderId("lab-1")).thenReturn(Optional.of(existingResult));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));

        labResultService.deleteResult("lab-1");

        assertThat(existingResult.getDeletedAt()).isNotNull();
    }
}
