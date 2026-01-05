package com.epam.aidial.deployment.manager.kubernetes;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ServiceState {
    READY("Ready"),
    NOT_READY("NotReady"),
    FAILED("Failed"),
    ;

    @Getter
    private final String stateName;

    public static ServiceState fromStateName(String stateName) {
        for (ServiceState state : ServiceState.values()) {
            if (state.stateName.equalsIgnoreCase(stateName)) {
                return state;
            }
        }
        return null;
    }

}
