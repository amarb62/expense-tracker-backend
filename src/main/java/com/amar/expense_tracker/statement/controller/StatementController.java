package com.amar.expense_tracker.statement.controller;

import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import com.amar.expense_tracker.statement.dto.StatementFile;
import com.amar.expense_tracker.statement.dto.StatementResponse;
import com.amar.expense_tracker.statement.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Tag(name = "Statements", description = "Bank/credit-card PDF statement upload and async processing status")
public class StatementController {

    private final StatementService statementService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Upload a PDF statement for async parsing",
            description = "Validates and stores the file, publishes a processing event, and returns "
                    + "immediately with status PROCESSING -- parsing happens asynchronously. "
                    + "If the PDF is password-protected, pass its password; it is used once to decrypt "
                    + "and is never stored or logged.")
    public StatementResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam UUID accountId,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String password) {
        return statementService.upload(user.userId(), accountId, file, password);
    }

    @GetMapping
    @Operation(summary = "List the current user's uploaded statements")
    public List<StatementResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return statementService.list(user.userId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a statement's processing status by ID")
    public StatementResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return statementService.get(user.userId(), id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download the originally-uploaded PDF statement file")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        StatementFile file = statementService.downloadFile(user.userId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .body(file.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a statement, its transactions, and its stored file, recalculating any affected months")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        statementService.delete(user.userId(), id);
    }
}
