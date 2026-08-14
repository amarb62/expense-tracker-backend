package com.amar.expense_tracker.statement.controller;

import com.amar.expense_tracker.auth.security.AuthenticatedUser;
import com.amar.expense_tracker.statement.dto.StatementResponse;
import com.amar.expense_tracker.statement.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class StatementController {

    private final StatementService statementService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatementResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam UUID accountId,
                                     @RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) String password) {
        return statementService.upload(user.userId(), accountId, file, password);
    }

    @GetMapping
    public List<StatementResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return statementService.list(user.userId());
    }

    @GetMapping("/{id}")
    public StatementResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return statementService.get(user.userId(), id);
    }
}
