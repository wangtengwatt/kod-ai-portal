package com.kod.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudSandboxRecoveryTask {
    private final CloudSandboxService service;

    @Scheduled(
            fixedDelayString = "${kod.cloud-sandbox.recovery-interval-millis:30000}",
            initialDelayString = "${kod.cloud-sandbox.recovery-initial-delay-millis:60000}")
    public void recoverLostWorkers() {
        service.recoverLostWorkers();
    }
}
