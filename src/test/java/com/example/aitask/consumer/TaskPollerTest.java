package com.example.aitask.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.storage.AiTaskRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class TaskPollerTest {
    @Test
    void rejectsPollingIntervalsThatWouldRoundDownToZero() {
        for (Duration minimum : List.of(Duration.ofMillis(-1), Duration.ZERO, Duration.ofNanos(1))) {
            assertThatThrownBy(() -> new SchedulerProperties(true, minimum, null, 1, 1, null, null, 1, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("min-poll-interval must be at least 1 ms");
        }
    }

    @Test
    void rejectsNonpositiveTaskLeases() {
        for (Duration lease : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThatThrownBy(() -> new SchedulerProperties(true, null, null, 1, 1, lease, null, 1, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lease-duration must be positive");
        }
    }

    @Test
    void queuedPollDoesNotClaimTasksAfterStop() {
        SchedulerProperties properties = new SchedulerProperties(true, null, null, 1, 1, null, null, 1, 1);
        AiTaskRepository repository = mock(AiTaskRepository.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ExecutorService executor = mock(ExecutorService.class);
        TaskPoller poller = new TaskPoller(properties, repository, mock(RunnerIdentity.class),
                mock(TaskRunner.class), scheduler, executor, new Semaphore(1));

        poller.stop();
        poller.poll();

        verifyNoInteractions(repository, scheduler, executor);
    }
}
