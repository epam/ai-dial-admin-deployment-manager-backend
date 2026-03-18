package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.web.security.FullAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disposable")
@LogExecution
@RequiredArgsConstructor
public class DisposableResourceController {

    private final DisposableResourceCleaner cleaner;

    @FullAdminOnly
    @PostMapping("/clean")
    public void clean() {
        cleaner.cleanAllCleanable();
    }
}
