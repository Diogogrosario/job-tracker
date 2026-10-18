package com.jobtracker.email;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    boolean existsByProviderMessageId(String providerMessageId);

    long count();

    void deleteAllInBatch();
}
