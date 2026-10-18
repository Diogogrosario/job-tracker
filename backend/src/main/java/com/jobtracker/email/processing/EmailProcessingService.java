package com.jobtracker.email.processing;

import com.jobtracker.config.properties.AppProperties;
import com.jobtracker.email.message.EmailMessage;
import com.jobtracker.email.message.EmailMessageRepository;
import com.jobtracker.email.review.ReviewItemRepository;
import com.jobtracker.email.review.ReviewStatus;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import jakarta.annotation.PreDestroy;

@Service
public class EmailProcessingService {
    private final AppProperties properties;
    private final EmailMessageRepository messageRepository;
    private final ReviewItemRepository reviewRepository;
    private final OllamaEmailClassifier classifier;
    private final EmailProcessingWorker worker;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "email-processing");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Progress progress = new Progress(0, 0, 0, 0);
    private volatile RunSummary lastRunSummary;
    private volatile String lastError;

    public EmailProcessingService(
            AppProperties properties,
            EmailMessageRepository messageRepository,
            ReviewItemRepository reviewRepository,
            OllamaEmailClassifier classifier,
            EmailProcessingWorker worker) {
        this.properties = properties;
        this.messageRepository = messageRepository;
        this.reviewRepository = reviewRepository;
        this.classifier = classifier;
        this.worker = worker;
    }

    public ProcessStatus trigger() {
        if (!running.compareAndSet(false, true)) {
            return status();
        }
        progress = new Progress(0, 0, 0, 0);
        lastError = null;
        try {
            executor.execute(this::run);
        } catch (RuntimeException exception) {
            running.set(false);
            lastError = "Could not start processing run";
            throw exception;
        }
        return status();
    }

    public ProcessStatus status() {
        return new ProcessStatus(
                messageRepository.countByProcessedFalse(),
                reviewRepository.countByStatus(ReviewStatus.PENDING),
                running.get(),
                progress,
                lastRunSummary,
                lastError);
    }

    @PreDestroy
    public void stopExecutor() {
        executor.shutdownNow();
    }

    private void run() {
        try {
            int max = Math.max(0, properties.getProcessing().getMaxPerRun());
            for (int index = 0; index < max; index++) {
                EmailMessage message = messageRepository.findFirstByProcessedFalseOrderByReceivedAtAscIdAsc()
                        .orElse(null);
                if (message == null) {
                    break;
                }
                if (EmailProcessingRules.isNoise(message.getSubject())) {
                    worker.markNoise(message.getId());
                    increment(1, 0, 0, 0);
                    continue;
                }
                EmailCategory hint = EmailProcessingRules.ruleHint(message.getSubject(), message.getBodyText());
                ClassificationResult classification;
                try {
                    classification = classifier.classify(message);
                } catch (RestClientException exception) {
                    lastError = "Ollama request failed (" + exception.getClass().getSimpleName() + ")";
                    increment(0, 0, 0, 1);
                    break;
                }
                try {
                    EmailProcessingWorker.ProcessingOutcome outcome = worker.process(
                            message.getId(), classification, hint,
                            properties.getProcessing().isAutoApply(),
                            properties.getProcessing().getAutoApplyMinConfidence());
                    if (outcome.error()) {
                        lastError = "One or more messages have processing errors; see their processing details";
                    }
                    increment(outcome.processed() ? 1 : 0, outcome.proposal() ? 1 : 0,
                            outcome.autoApplied() ? 1 : 0, outcome.error() ? 1 : 0);
                } catch (RuntimeException exception) {
                    worker.markFailed(message.getId(),
                            "Processing failed (" + exception.getClass().getSimpleName() + ")");
                    lastError = "A message failed processing (" + exception.getClass().getSimpleName() + ")";
                    increment(1, 0, 0, 1);
                }
            }
        } catch (RuntimeException exception) {
            lastError = "Processing run failed (" + exception.getClass().getSimpleName() + ")";
            increment(0, 0, 0, 1);
        } finally {
            lastRunSummary = new RunSummary(
                    progress.processed(), progress.proposals(), progress.autoApplied(), progress.errors(),
                    OffsetDateTime.now());
            running.set(false);
        }
    }

    private synchronized void increment(int processed, int proposals, int autoApplied, int errors) {
        Progress current = progress;
        progress = new Progress(current.processed() + processed, current.proposals() + proposals,
                current.autoApplied() + autoApplied, current.errors() + errors);
    }

    public record Progress(int processed, int proposals, int autoApplied, int errors) {
    }

    public record RunSummary(int processed, int proposals, int autoApplied, int errors, OffsetDateTime completedAt) {
    }

    public record ProcessStatus(
            long unprocessedCount,
            long pendingReviewCount,
            boolean running,
            Progress progress,
            RunSummary lastRunSummary,
            String lastError) {
    }
}
