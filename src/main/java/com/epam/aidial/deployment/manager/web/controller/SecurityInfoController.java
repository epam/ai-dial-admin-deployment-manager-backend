package com.epam.aidial.deployment.manager.web.controller;

import com.epam.aidial.deployment.manager.configuration.logging.LogExecution;
import com.epam.aidial.deployment.manager.service.security.SecurityClaimsExtractor;
import com.epam.aidial.deployment.manager.web.dto.SecurityInfoDto;
import com.epam.aidial.deployment.manager.web.dto.UserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/security-info")
@LogExecution
@RequiredArgsConstructor
public class SecurityInfoController {

    private final SecurityClaimsExtractor securityClaimsExtractor;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public SecurityInfoDto getSecurityInfo() {
        UserInfoDto userInfoDto = new UserInfoDto(
                securityClaimsExtractor.getAuthor(),
                securityClaimsExtractor.getEmail(),
                securityClaimsExtractor.getRoles());
        return new SecurityInfoDto(userInfoDto);
    }
}
