package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.web.security.FullAdminOnly;
import com.epam.aidial.deployment.manager.web.validation.ValidDomainList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/global-whitelist")
@LogExecution
@RequiredArgsConstructor
public class GlobalDomainWhitelistController {

    private final GlobalDomainWhitelistService globalDomainWhitelistService;

    @GetMapping(path = "/image-build",
                produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<String> getDomainWhitelistForImageBuild() {
        return globalDomainWhitelistService.getDomainWhitelist();
    }

    @GetMapping(path = "/image-build/revision/{revision}",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<String> getDomainWhitelistSnapshot(@PathVariable Integer revision) {
        return globalDomainWhitelistService.getDomainWhitelistSnapshot(revision);
    }

    @FullAdminOnly
    @PostMapping(path = "/image-build",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<String> updateDomainWhitelistForImageBuild(@RequestBody @NotNull @ValidDomainList List<String> allowedDomains) {
        log.info("Updating global domain whitelist for image build. Allowed domains: {}", allowedDomains);
        var updatedAllowedDomains = globalDomainWhitelistService.updateDomainWhitelist(allowedDomains);
        log.info("Successfully updated global domain whitelist for image build.");
        return updatedAllowedDomains;
    }

    @FullAdminOnly
    @PostMapping(path = "/image-build/revision/{revision}/rollback",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Roll back the global image-build whitelist to a past revision",
            description = "Replaces the current image-build whitelist entries with the set that existed at the given revision. "
                    + "Full replacement — does NOT merge with current entries.")
    @ApiResponse(responseCode = "200", description = "Rollback applied or identical-state no-op")
    @ApiResponse(responseCode = "400", description = "Snapshot entries fail current validation")
    @ApiResponse(responseCode = "403", description = "Read-only role")
    @ApiResponse(responseCode = "404", description = "Revision not found or predates whitelist")
    public List<String> rollbackDomainWhitelistForImageBuild(@PathVariable Integer revision) {
        log.info("Rolling back global domain whitelist for image build to revision {}", revision);
        var rolledBack = globalDomainWhitelistService.rollback(revision);
        log.info("Successfully rolled back global domain whitelist for image build to revision {}", revision);
        return rolledBack;
    }
}
