package com.epam.aidial.deployment.manager.kubernetes;

import java.util.List;

public interface JobCallback {

    default void onJobPhaseChange(JobPhase phase) {
    }

    default void onNewLog(List<String> logs) {
    }

}
