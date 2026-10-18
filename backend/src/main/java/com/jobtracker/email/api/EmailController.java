package com.jobtracker.email.api;

import com.jobtracker.email.mailbox.MailboxService;
import com.jobtracker.email.processing.EmailProcessingService;
import com.jobtracker.email.sync.EmailSyncService;
import com.jobtracker.email.sync.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final EmailSyncService syncService;
    private final MailboxService mailboxService;
    private final EmailProcessingService processingService;

    public EmailController(
            EmailSyncService syncService,
            MailboxService mailboxService,
            EmailProcessingService processingService) {
        this.syncService = syncService;
        this.mailboxService = mailboxService;
        this.processingService = processingService;
    }

    @PostMapping("/sync")
    public SyncResult sync(@RequestParam(defaultValue = "false") boolean force) {
        return syncService.sync(force);
    }

    @GetMapping("/sync/status")
    public MailboxService.SyncStatusResponse syncStatus() {
        return mailboxService.syncStatus(syncService.isSyncing());
    }

    @PostMapping("/process")
    public ResponseEntity<EmailProcessingService.ProcessStatus> process() {
        return ResponseEntity.accepted().body(processingService.trigger());
    }

    @GetMapping("/process/status")
    public EmailProcessingService.ProcessStatus processStatus() {
        return processingService.status();
    }

    @DeleteMapping("/messages")
    public ResponseEntity<Void> deleteMessages() {
        mailboxService.deleteImportedMessages();
        return ResponseEntity.noContent().build();
    }
}
