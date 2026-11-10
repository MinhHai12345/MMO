# Production Runtime Configuration

This document defines the runtime configuration contract for deploying MMO with MySQL and Quartz JDBC.

## Profiles and schema ownership

- The application defaults to the `dev` profile only when no profile is explicitly selected.
- Production deployments must set `SPRING_PROFILES_ACTIVE=prod`.
- Hibernate schema management is `validate` in the shared defaults and production profile. Development overrides it with `update`.
- Production schema changes must be applied as an explicit database deployment step. The application must not mutate production schema at startup.
- Quartz JDBC schema initialization is disabled (`spring.quartz.jdbc.initialize-schema=never`) in every profile. Provision the Quartz tables explicitly using the MySQL InnoDB schema script shipped with the exact Quartz version resolved by the application. Do not run a destructive schema script against an existing scheduler database.

## Required environment

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` |
| `DB_URL` | MySQL JDBC URL |
| `DB_USERNAME` | MySQL application user |
| `DB_PASSWORD` | MySQL application password |
| `MMO_MYSQL_TEST_URL` | MySQL integration-test URL (test environment only) |
| `MMO_MYSQL_TEST_USERNAME` | MySQL integration-test user |
| `MMO_MYSQL_TEST_PASSWORD` | MySQL integration-test password |

Use `JPA_DDL_AUTO=validate` (the default) unless a deployment explicitly overrides it. Do not set it to `update` or `create` in production.

## Quartz JDBC cluster

All application instances participating in the same scheduler cluster must use:

- The same MySQL Quartz tables and table prefix (`QRTZ_`).
- The same scheduler instance name (`MMOQuartzScheduler`).
- A unique instance identifier (`AUTO`).
- Cluster mode enabled (`QUARTZ_CLUSTERED=true`).
- Synchronized host clocks and a stable database connection.

The default cluster check-in interval is 15 seconds. The default misfire threshold is 60 seconds. These can be overridden with `QUARTZ_CLUSTER_CHECKIN_INTERVAL` and `QUARTZ_MISFIRE_THRESHOLD`.

Do not point independent environments at the same Quartz tables. Use a separate database/schema per environment.

## Startup initialization and scheduling boundary

`app.initial-data.auto-import` currently gates the `DataInitializer` collection. The only production initializer is `FootballQuartzJobInitializer`, which registers durable CronJob definitions and does not execute those jobs inline. Keep this enabled unless the initialization lifecycle is refactored to separate scheduler registration from data initialization.

The end-to-end `FootballWorkflowOrchestrationQuartzJob` remains manual-only with its far-future trigger. Do not change its cron expression to a recurring schedule until P0.6 runtime acceptance is complete.

Other registered jobs retain their existing schedules; this configuration change does not alter their cadence.

## Workflow lease heartbeat

The workflow heartbeat defaults to `PT1M` and is configured with `mmo.workflow.lease.heartbeat-interval`. Ensure the interval is comfortably shorter than the workflow execution lease duration. Do not tune it without checking the lease duration in `WorkflowCheckpointService`.

## Deployment verification checklist

1. Confirm `SPRING_PROFILES_ACTIVE=prod` is visible in the runtime environment.
2. Confirm the application DB user has only the required application privileges and cannot perform schema migration unless explicitly needed.
3. Confirm Quartz tables exist before application startup.
4. Start one application instance and verify Quartz reports a clustered scheduler with a unique instance ID.
5. Start a second instance against the same Quartz tables and verify both instances join the same cluster.
6. Restart one instance and verify the other continues operating and persisted triggers remain available.
7. Verify the end-to-end workflow job remains manual-only.
8. Verify no recurring workflow trigger is enabled as part of deployment.

Quartz clustering and MySQL runtime acceptance must be demonstrated in integration/deployment tests before recurring workflow scheduling is enabled.
