package com.store.huyen.importer.controller;

import com.store.huyen.importer.DynamicImportJobConfig;
import com.store.huyen.importer.model.ImportContext;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/import")
public class ImportController {
    private final JobLauncher jobLauncher;
    private final DynamicImportJobConfig jobConfig;


    @PostMapping("/run")
    public String runJob(@RequestParam String type, @RequestParam String path) throws Exception {
        ImportContext context = new ImportContext(type.toUpperCase(), path);
        Job job = jobConfig.buildJob(context);
        jobLauncher.run(job, new JobParametersBuilder()
                .addString("type", type)
                .addString("path", path)
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters());
        return "Job started for type: " + type;
    }
}
