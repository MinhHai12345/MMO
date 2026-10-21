# FB Analytics Runbook

## Data readiness

Call `GET /api/fb/analytics/data-readiness`. Review score coverage and expected-goals coverage by season. A `READY` label means the minimum sample threshold is met; it does not prove predictive quality.

## Walk-forward evaluation

Call `GET /api/fb/analytics/walk-forward-backtest?from=2026-01-01T00:00:00Z&to=2026-07-01T00:00:00Z`.

The `from` instant is inclusive and `to` is exclusive. Run a fixed window when comparing model versions. The evaluator only accepts predictions calculated before kickoff and uses matured historical results for calibration. It groups matches with identical kickoff times so they share the same historical prior. A conservative four-hour result availability lag is currently used because final-whistle timestamps are not stored; treat this as a proxy.

## Metrics

- Brier score and log loss: lower is better.
- Accuracy: useful but insufficient on its own.
- Expected calibration error (ECE): lower generally indicates better calibration, but depends on binning and sample size.
- Compare baseline, raw and calibrated predictions on the same eligible test cohort.
- Compare Dixon-Coles variants only on their matched cohort.
- Treat league/season metrics from small samples as noisy.

## Model promotion checklist

1. Use chronological out-of-sample evaluation.
2. Compare against baseline and current production model on identical fixtures.
3. Record code/model revision, date bounds, eligible sample count and metrics.
4. Inspect class-wise performance and calibration, not just accuracy.
5. Verify no post-kickoff information leaks into features.
6. Do not promote a model based on a small sample or a single aggregate metric.

## Ingestion operations

Check finished scores after result sync, standings by season and `StandingType`, and odds history over time. Respect provider quotas and back off on HTTP 429 responses; avoid tight retry loops.
