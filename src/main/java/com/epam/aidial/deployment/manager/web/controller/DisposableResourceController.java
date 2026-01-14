package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.cleanup.resource.DisposableResourceCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/disposable")
@RequiredArgsConstructor
public class DisposableResourceController {

    private final DisposableResourceCleaner cleaner;

    @PostMapping("/clean")
    public void clean() {
        cleaner.cleanAllCleanable();
    }
}
