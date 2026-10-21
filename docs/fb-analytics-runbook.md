# FB Analytics Runbook

## 1. Verify source data before predictions

Call `GET /api/fb/analytics/data-readiness`.

Review each season:
- `finishedWithScores`: finished matches with both actual scores.
- `finishedWithExpectedGoals`: finished matches with expected goals for both teams.
- `scoreCoverage` and `expectedGoalsCoverage`: coverage ratios against finished fixtures.
- `goalsModelReadiness` and `expectedGoalsModelReadiness`: minimum-history indicators, not a guarantee of model quality.

Do not interpret `READY` as proof of predictive performance. It only indicates that the minimum history threshold is met.

## 2. Run a walk-forward backtest

Call `GET /api/fb/analytics/walk-forward-backtest?from=2026-01-01T00:00:00Z&to=2026-07-01T00:00:00Z`.

Both parameters must be ISO-8601 instants; `from` is inclusive and `to` is exclusive. Choose a fixed test window and retain the returned report for comparison between code/model versions.

The evaluator uses predictions calculated before kickoff and groups matches with identical kickoff timestamps so outcomes from a simultaneous kickoff cannot enter each other's history. Outcomes are considered available after a conservative four-hour lag because the schema does not currently store the final-whistle timestamp. This lag is a proxy, not a replacement for a provider result timestamp.

## 3. Interpret metrics

- **Brier score**: lower is better for probability accuracy.
- **Log loss**: lower is better; heavily penalizes confident incorrect probabilities.
- **Accuracy**: fraction of matches whose highest-probability outcome is correct. Do not use it alone.
- **Expected calibration error (ECE)**: lower generally indicates better probability calibration; it depends on binning and sample size.
- **Baseline vs raw vs calibrated**: compare on the same test window. A calibrated model should not be promoted merely because calibration ran; its out-of-sample metrics should improve or remain acceptable.
- **Dixon–Coles comparisons**: compare global-rho and league-specific-rho metrics on the matched cohort only. Check the sample count before interpreting them.
- **Breakdown by league/season**: use to identify weak or under-sampled competitions; small groups have high variance.

## 4. Promotion criteria

Before selecting a model for production:
1. Use a chronological test window not used for tuning.
2. Compare against the empirical baseline and current production model on the same eligible fixtures.
3. Require adequate sample size and report uncertainty; do not rank models using a tiny league/season group.
4. Check Brier score and log loss together with calibration and class-wise performance.
5. Verify no feature uses post-kickoff information (final scores, post-match stats, or standings updated after the tested kickoff).
6. Save the model/version, window bounds, code revision, sample count and metrics alongside any manually approved promotion.

## 5. Data ingestion checks

- Re-run bootstrap only when intended; regular updates should use the relevant sync jobs.
- Verify finished match scores and statuses after result sync.
- Verify standings by season and `StandingType`.
- Verify odds history changes over time rather than only the latest odds row.
- Respect provider quotas and back off on HTTP 429; do not retry in tight loops.
