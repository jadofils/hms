package amalitech.hospital.management.service;

import amalitech.hospital.management.dto.log.SystemLogResponse;
import amalitech.hospital.management.exception.runtime.BadRequestException;
import amalitech.hospital.management.exception.runtime.NotFoundException;
import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemLogServiceTest {

    @Mock private SystemLogRepository systemLogRepository;

    private SystemLogService systemLogService;

    private SystemLog existingLog;

    @BeforeEach
    void setUp() {
        systemLogService = new SystemLogService(systemLogRepository);

        existingLog = new SystemLog();
        existingLog.setLogId("log-1");
        existingLog.setLogLevel("ERROR");
        existingLog.setSource("amalitech.hospital.management.service.RoleService.grantPermission");
        existingLog.setMessage("Role already has this permission");
        existingLog.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getSystemLogs_returnsEveryRow_whenNoFilterGiven() {
        when(systemLogRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(existingLog)));

        PagedModel<SystemLogResponse> result = systemLogService.getSystemLogs(PageRequest.of(0, 20), null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLogLevel()).isEqualTo("ERROR");
        verify(systemLogRepository, never()).findByLogLevel(any(), any());
    }

    @Test
    void getSystemLogs_filtersByLogLevel_whenOnlyLevelGiven() {
        when(systemLogRepository.findByLogLevel("ERROR", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(existingLog)));

        PagedModel<SystemLogResponse> result = systemLogService.getSystemLogs(PageRequest.of(0, 20), "ERROR", null);

        assertThat(result.getContent()).hasSize(1);
        verify(systemLogRepository).findByLogLevel("ERROR", PageRequest.of(0, 20));
    }

    @Test
    void getSystemLogs_throwsBadRequest_forAnUnrecognizedLogLevel() {
        assertThatThrownBy(() -> systemLogService.getSystemLogs(PageRequest.of(0, 20), "BOGUS", null))
                .isInstanceOf(BadRequestException.class);
        verify(systemLogRepository, never()).findByLogLevel(any(), any());
    }

    @Test
    void getSystemLogs_filtersBySourceContainingIgnoreCase_whenOnlySourceGiven() {
        when(systemLogRepository.findBySourceContainingIgnoreCase("RoleService", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(existingLog)));

        PagedModel<SystemLogResponse> result =
                systemLogService.getSystemLogs(PageRequest.of(0, 20), null, "RoleService");

        assertThat(result.getContent()).hasSize(1);
        verify(systemLogRepository).findBySourceContainingIgnoreCase("RoleService", PageRequest.of(0, 20));
    }

    @Test
    void getSystemLogs_combinesBothFilters_whenBothGiven() {
        when(systemLogRepository.findByLogLevelAndSourceContainingIgnoreCase(
                "ERROR", "RoleService", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(existingLog)));

        PagedModel<SystemLogResponse> result =
                systemLogService.getSystemLogs(PageRequest.of(0, 20), "ERROR", "RoleService");

        assertThat(result.getContent()).hasSize(1);
        verify(systemLogRepository)
                .findByLogLevelAndSourceContainingIgnoreCase("ERROR", "RoleService", PageRequest.of(0, 20));
    }

    @Test
    void getSystemLog_returnsMappedResponse_whenFound() {
        when(systemLogRepository.findById("log-1")).thenReturn(Optional.of(existingLog));

        SystemLogResponse response = systemLogService.getSystemLog("log-1");

        assertThat(response.getLogId()).isEqualTo("log-1");
        assertThat(response.getMessage()).isEqualTo("Role already has this permission");
        assertThat(response.getUserId()).isNull();
    }

    @Test
    void getSystemLog_includesUserId_whenTheLogHasALinkedUser() {
        User user = new User();
        user.setUserId("user-1");
        existingLog.setUser(user);
        when(systemLogRepository.findById("log-1")).thenReturn(Optional.of(existingLog));

        SystemLogResponse response = systemLogService.getSystemLog("log-1");

        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    void getSystemLog_throwsNotFound_whenAbsent() {
        when(systemLogRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemLogService.getSystemLog("missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
