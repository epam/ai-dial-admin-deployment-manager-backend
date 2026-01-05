package com.epam.aidial.deployment.manager.kubernetes;

import java.util.List;

@FunctionalInterface
public interface NewLogJobCallback extends JobCallback {

    @Override
    void onNewLog(List<String> logs);

}
