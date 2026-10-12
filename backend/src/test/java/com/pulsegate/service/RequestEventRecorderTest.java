package com.pulsegate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pulsegate.entity.Decision;
import com.pulsegate.entity.RequestEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class RequestEventRecorderTest {

    private static RequestEvent event(long clientId) {
        return new RequestEvent(clientId, "/api/protected/resource", Decision.ALLOWED, 200, 50, 4L, Instant.now());
    }

    /** Records every batch it is given. */
    private static class CollectingWriter extends EventBatchWriter {
        final List<List<RequestEvent>> batches = Collections.synchronizedList(new ArrayList<>());

        CollectingWriter() {
            super(null);
        }

        @Override
        public void write(List<RequestEvent> events) {
            batches.add(List.copyOf(events));
        }

        int total() {
            synchronized (batches) {
                return batches.stream().mapToInt(List::size).sum();
            }
        }
    }

    @Test
    void syncMode_databaseFailureIsSwallowedSoTheRequestOutcomeIsUnchanged() {
        EventBatchWriter writer = mock(EventBatchWriter.class);
        doThrow(new DataAccessResourceFailureException("db down")).when(writer).write(any());
        RequestEventRecorder recorder = new RequestEventRecorder(writer, false, 10, 10);

        RequestEvent e = event(1);
        assertThatCode(() -> recorder.record(e)).doesNotThrowAnyException();
        verify(writer).write(List.of(e));
        assertThat(recorder.droppedEvents()).isEqualTo(1);
    }

    @Test
    void asyncMode_writesEveryEventInBatches() throws Exception {
        CollectingWriter writer = new CollectingWriter();
        RequestEventRecorder recorder = new RequestEventRecorder(writer, true, 10_000, 100);

        for (int i = 0; i < 1_000; i++) recorder.record(event(i));
        recorder.shutdown(); // drains the queue before returning

        assertThat(writer.total()).isEqualTo(1_000);
        assertThat(writer.batches).allSatisfy(b -> assertThat(b).hasSizeLessThanOrEqualTo(100));
        assertThat(writer.batches.size()).isLessThan(1_000); // batched, not one write per event
        assertThat(recorder.droppedEvents()).isZero();
    }

    @Test
    void asyncMode_requestThreadNeverBlocksWhenTheQueueIsFull() throws Exception {
        CountDownLatch databaseStuck = new CountDownLatch(1);
        CollectingWriter slowWriter = new CollectingWriter() {
            @Override
            public void write(List<RequestEvent> events) {
                try {
                    databaseStuck.await(5, TimeUnit.SECONDS); // simulate a hung database
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                super.write(events);
            }
        };
        RequestEventRecorder recorder = new RequestEventRecorder(slowWriter, true, 5, 1);

        long start = System.nanoTime();
        for (int i = 0; i < 50; i++) recorder.record(event(i));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(500);           // no request waited on the database
        assertThat(recorder.droppedEvents()).isGreaterThan(0); // overflow was counted, not hidden

        databaseStuck.countDown();
        recorder.shutdown();
        assertThat(slowWriter.total() + recorder.droppedEvents()).isEqualTo(50); // every event accounted for
    }

    @Test
    void asyncMode_failedBatchIsCountedAndWritingContinues() throws Exception {
        CollectingWriter flaky = new CollectingWriter() {
            boolean failedOnce;

            @Override
            public void write(List<RequestEvent> events) {
                if (!failedOnce) {
                    failedOnce = true;
                    throw new DataAccessResourceFailureException("db blip");
                }
                super.write(events);
            }
        };
        RequestEventRecorder recorder = new RequestEventRecorder(flaky, true, 100, 1);

        for (int i = 0; i < 10; i++) recorder.record(event(i));
        recorder.shutdown();

        assertThat(recorder.droppedEvents()).isEqualTo(1);
        assertThat(flaky.total()).isEqualTo(9);
    }
}
