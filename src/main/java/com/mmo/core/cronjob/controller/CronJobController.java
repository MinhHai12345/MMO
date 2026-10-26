package com.mmo.core.cronjob.controller;

import com.mmo.core.cronjob.entity.CronJob;
import com.mmo.core.cronjob.service.CronJobService;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cronjobs")
public class CronJobController {

    @Resource
    private CronJobService cronJobService;

    /**
     * Pause a job.
     *
     * @param jobId Long
     * @return CronjobData
     */
    @PostMapping("/{id}/pause")
    @ResponseStatus(code = HttpStatus.OK)
    public CronJob pause(@PathVariable("id") final Long jobId) {
        return cronJobService.pauseJob(jobId);
    }

    /**
     * Resume a job.
     *
     * @param jobId Long
     * @return CronjobData
     */
    @PostMapping("/{id}/resume")
    @ResponseStatus(code = HttpStatus.OK)
    public CronJob resume(@PathVariable("id") final Long jobId) {
        return cronJobService.resumeJob(jobId);
    }

    /**
     * Stop a job.
     *
     * @param jobId Long
     * @return CronjobData
     */
    @PostMapping("/{id}/stop")
    @ResponseStatus(code = HttpStatus.OK)
    public CronJob stop(@PathVariable("id") final Long jobId) {
        return cronJobService.stopJob(jobId);
    }

    /**
     * Start a job immediately.
     *
     * @param jobId Long
     * @return CronjobData
     */
    @PostMapping("/{id}/start")
    @ResponseStatus(code = HttpStatus.OK)
    public CronJob start(
            @PathVariable("id") final Long jobId,
            @RequestBody(required = false) Map<String, String> parameters) {
        return cronJobService.startJobNow(jobId, parameters);
    }
}
