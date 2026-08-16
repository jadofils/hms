package amalitech.hospital.management.service;

import amalitech.hospital.management.model.user.User;
import amalitech.hospital.management.repository.user.UserRepository;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock private SystemLogRepository systemLogRepository;
    @Mock private UserRepository userRepository;

    private MaintenanceService maintenanceService;

    @BeforeEach
    void setUp() {
        maintenanceService = new MaintenanceService(systemLogRepository, userRepository, 30L, 90L);
    }

    @Test
    void cleanupOldLogs_deletesRowsOlderThanTheRetentionWindow() {
        maintenanceService.cleanupOldLogs();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(systemLogRepository).deleteByCreatedAtBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    @Test
    void deactivateIdleUsers_deactivatesEveryUserTheQueryReturns() {
        User idleUser1 = new User();
        idleUser1.setUserId("user-1");
        idleUser1.setIsActive(true);
        User idleUser2 = new User();
        idleUser2.setUserId("user-2");
        idleUser2.setIsActive(true);
        when(userRepository.findActiveUsersIdleSince(any(LocalDateTime.class)))
                .thenReturn(List.of(idleUser1, idleUser2));

        maintenanceService.deactivateIdleUsers();

        assertThat(idleUser1.getIsActive()).isFalse();
        assertThat(idleUser2.getIsActive()).isFalse();
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    void deactivateIdleUsers_doesNothing_whenNoIdleUsersFound() {
        when(userRepository.findActiveUsersIdleSince(any(LocalDateTime.class))).thenReturn(List.of());

        maintenanceService.deactivateIdleUsers();

        verify(userRepository, never()).save(any());
    }

    @Test
    void deactivateIdleUsers_usesTheConfiguredIdleThreshold() {
        when(userRepository.findActiveUsersIdleSince(any(LocalDateTime.class))).thenReturn(List.of());

        maintenanceService.deactivateIdleUsers();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).findActiveUsersIdleSince(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusDays(89));
    }
}
