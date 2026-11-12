package com.example.aitask.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.aitask.config.SchedulerProperties;
import com.example.aitask.storage.AiTaskRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class TaskPollerTest {
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
