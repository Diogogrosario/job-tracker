package com.jobtracker.email;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/mailbox")
public class MailboxController {
    private final GoogleOAuthService oauthService;
    private final MailboxService mailboxService;
    private final EmailSyncService syncService;

    public MailboxController(
            GoogleOAuthService oauthService,
            MailboxService mailboxService,
            EmailSyncService syncService) {
        this.oauthService = oauthService;
        this.mailboxService = mailboxService;
        this.syncService = syncService;
    }

    @GetMapping("/connect")
    public ResponseEntity<Void> connect() {
        URI authorizationUri = oauthService.createAuthorizationUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        oauthService.handleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(UriComponentsBuilder.fromUriString(mailboxService.frontendUrl())
                        .build().toUri())
                .build();
    }

    @GetMapping("/status")
    public MailboxService.MailboxStatusResponse status() {
        return mailboxService.status();
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        mailboxService.disconnect();
        return ResponseEntity.noContent().build();
    }
}
