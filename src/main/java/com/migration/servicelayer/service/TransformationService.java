package com.migration.servicelayer.service;

import com.migration.servicelayer.scheduler.TransformationScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class TransformationService {

    private final TransformationScheduler scheduler;

    @Async
    public void runAsync() {
        log.info("Transformacao manual iniciada em background.");
        scheduler.processAllTenants();
    }
}
