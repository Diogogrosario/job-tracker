package com.jobtracker.email.mailbox;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailboxConnectionRepository extends JpaRepository<MailboxConnection, Long> {
    Optional<MailboxConnection> findByProvider(String provider);
}
