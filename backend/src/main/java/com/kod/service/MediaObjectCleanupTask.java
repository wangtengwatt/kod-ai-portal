package com.kod.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MediaObjectCleanupTask {
    private final MediaObjectService service;

    @Scheduled(fixedDelayString = "${kod.media-storage.cleanup-interval-millis:300000}", initialDelay = 60_000)
    public void purgePendingObjects() {
        int deleted = service.purgePending(100);
        if (deleted > 0) log.info("Purged {} account-deletion media objects", deleted);
    }
}
