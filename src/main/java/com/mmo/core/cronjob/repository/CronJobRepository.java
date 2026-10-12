package com.mmo.core.cronjob.repository;

import com.mmo.core.cronjob.entity.CronJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CronJobRepository extends JpaRepository<CronJob, Long> {

    Optional<CronJob> findByJobNameAndJobGroup(String jobName, String jobGroup);
}
