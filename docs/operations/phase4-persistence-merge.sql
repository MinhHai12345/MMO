-- Phase 4: canonical field provenance and append-only source snapshots.
CREATE TABLE IF NOT EXISTS fb_match_field_provenance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    match_id BIGINT NOT NULL,
    field_key VARCHAR(80) NOT NULL,
    dataset_key VARCHAR(40) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    source_updated_at DATETIME(6) NULL,
    fetched_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fb_match_field_provenance UNIQUE (match_id, field_key),
    INDEX idx_fb_match_field_source (provider, dataset_key),
    CONSTRAINT fk_fb_match_field_provenance_match FOREIGN KEY (match_id) REFERENCES fb_matches(id)
);

-- Snapshot rows are audit records. Keep the latest snapshot per provider/team queryable,
-- but do not overwrite the prior fetch when a provider sends a new payload.
ALTER TABLE fb_match_statistics_source_snapshots
    DROP INDEX uk_fb_match_stats_snapshot_source,
    ADD INDEX idx_fb_match_stats_snapshot_latest (match_id, team_id, provider, fetched_at);
