package amalitech.hospital.management.aop;

import amalitech.hospital.management.model.user.logs.SystemLog;
import amalitech.hospital.management.repository.user.logs.SystemLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SystemLogWriterTest {

    @Mock private SystemLogRepository systemLogRepository;

    private SystemLogWriter systemLogWriter;

    @BeforeEach
    void setUp() {
        systemLogWriter = new SystemLogWriter(systemLogRepository);
    }

    @Test
    void record_savesEntryWithProvidedFields() {
        systemLogWriter.record("ERROR", "UserService.createUser", "Something broke");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        SystemLog saved = captor.getValue();
        assertThat(saved.getLogLevel()).isEqualTo("ERROR");
        assertThat(saved.getSource()).isEqualTo("UserService.createUser");
        assertThat(saved.getMessage()).isEqualTo("Something broke");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void record_fallsBackToPlaceholderMessage_whenMessageIsNull() {
        systemLogWriter.record("ERROR", "Foo.bar", null);

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("(no message)");
    }

    @Test
    void record_fallsBackToPlaceholderMessage_whenMessageIsBlank() {
        systemLogWriter.record("ERROR", "Foo.bar", "   ");

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).isEqualTo("(no message)");
    }
}
