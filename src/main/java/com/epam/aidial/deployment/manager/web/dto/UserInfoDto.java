package com.epam.aidial.deployment.manager.web.dto;

import java.util.Set;

public record UserInfoDto(String id, String email, Set<String> roles) {}
