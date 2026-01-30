package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.service.GlobalDomainWhitelistService;
import com.epam.aidial.deployment.manager.web.validation.ValidDomainList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/global-whitelist")
@RequiredArgsConstructor
public class GlobalDomainWhitelistController {

    private final GlobalDomainWhitelistService globalDomainWhitelistService;

    @GetMapping(path = "/image-build",
                produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    public List<String> getDomainWhitelistForImageBuild() {
        return globalDomainWhitelistService.getDomainWhitelist();
    }

    @PostMapping(path = "/image-build",
            produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<String> updateDomainWhitelistForImageBuild(@RequestBody @NotNull @ValidDomainList List<String> allowedDomains) {
        log.info("Updating global domain whitelist for image build. Allowed domains: {}", allowedDomains);
        var updatedAllowedDomains = globalDomainWhitelistService.updateDomainWhitelist(allowedDomains);
        log.info("Successfully updated global domain whitelist for image build.");
        return updatedAllowedDomains;
    }
}
