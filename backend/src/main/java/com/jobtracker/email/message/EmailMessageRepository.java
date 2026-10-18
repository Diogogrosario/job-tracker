package com.jobtracker.email.message;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    boolean existsByProviderMessageId(String providerMessageId);

    long count();

    long countByProcessedFalse();

    Optional<EmailMessage> findFirstByProcessedFalseOrderByReceivedAtAscIdAsc();

    Optional<EmailMessage> findFirstByThreadIdAndApplicationIsNotNullAndIdNotOrderByReceivedAtAsc(
            String threadId, Long id);

    List<EmailMessage> findByApplication_IdOrderByReceivedAtDesc(Long applicationId);

    void deleteAllInBatch();
}
