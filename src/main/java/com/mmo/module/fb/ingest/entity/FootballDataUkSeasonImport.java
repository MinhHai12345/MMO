package com.mmo.module.fb.ingest.entity;

import com.mmo.core.entity.AbstractEntity;
import com.mmo.module.fb.league.entity.Season;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Completion marker for the one-time Football-Data.co.uk historical bootstrap per season. */
@Entity
@Table(name = "fb_football_data_uk_imports",
        uniqueConstraints = @UniqueConstraint(name = "uk_fb_fd_uk_import_season", columnNames = "season_id"))
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FootballDataUkSeasonImport extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false, unique = true)
    private Season season;

    @Column(name = "csv_rows", nullable = false)
    private int csvRows;

    @Column(name = "imported_team_rows", nullable = false)
    private int importedTeamRows;

    @Column(name = "unmatched_rows", nullable = false)
    private int unmatchedRows;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
