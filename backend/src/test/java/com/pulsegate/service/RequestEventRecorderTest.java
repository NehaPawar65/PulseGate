package com.pulsegate.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import com.pulsegate.repository.RequestEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class RequestEventRecorderTest {

    @Test
    void databaseFailureIsSwallowedSoTheRequestOutcomeIsUnchanged() {
        RequestEventRepository repository = mock(RequestEventRepository.class);
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        RequestEventRecorder recorder = new RequestEventRecorder(repository);

        RequestEvent event = new RequestEvent(1L, "/api/protected/resource", Decision.REJECTED_RATE_LIMIT,
                429, 50, 0L, Instant.now());

        assertThatCode(() -> recorder.record(event)).doesNotThrowAnyException();
        verify(repository).save(event);
    }
}
