package com.jobtracker.email;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final EmailSyncService syncService;
    private final MailboxService mailboxService;

    public EmailController(EmailSyncService syncService, MailboxService mailboxService) {
        this.syncService = syncService;
        this.mailboxService = mailboxService;
    }

    @PostMapping("/sync")
    public SyncResult sync() {
        return syncService.sync();
    }

    @GetMapping("/sync/status")
    public MailboxService.SyncStatusResponse syncStatus() {
        return mailboxService.syncStatus(syncService.isSyncing());
    }

    @DeleteMapping("/messages")
    public ResponseEntity<Void> deleteMessages() {
        mailboxService.deleteImportedMessages();
        return ResponseEntity.noContent().build();
    }
}
