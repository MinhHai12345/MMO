# Football Data Analysis System - Database Schema

## Database Setup

```sql
-- Create database
CREATE DATABASE IF NOT EXISTS fb_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mmo_db;
```

## Core Entities

### fb_leagues
```sql
CREATE TABLE fb_leagues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(100),
    popularity_score DECIMAL(3,2) DEFAULT 0.00,
    betting_market VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_country (country),
    INDEX idx_popularity (popularity_score),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_teams
```sql
CREATE TABLE fb_teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    short_name VARCHAR(50),
    founded_year INT,
    stadium_name VARCHAR(255),
    stadium_capacity INT,
    market_value DECIMAL(15,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_name (name),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_seasons
```sql
CREATE TABLE fb_seasons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    start_date DATE,
    end_date DATE,
    is_current BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (league_id) REFERENCES fb_leagues(id) ON DELETE CASCADE,
    INDEX idx_league (league_id),
    INDEX idx_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_matches
```sql
CREATE TABLE fb_matches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_id BIGINT NOT NULL,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    match_date TIMESTAMP NOT NULL,
    round_info VARCHAR(50),
    venue VARCHAR(255),
    attendance INT,
    referee VARCHAR(255),
    weather VARCHAR(100),
    home_score INT,
    away_score INT,
    match_status VARCHAR(50) DEFAULT 'SCHEDULED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (season_id) REFERENCES fb_seasons(id) ON DELETE CASCADE,
    FOREIGN KEY (home_team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    FOREIGN KEY (away_team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    INDEX idx_season (season_id),
    INDEX idx_home_team (home_team_id),
    INDEX idx_away_team (away_team_id),
    INDEX idx_match_date (match_date),
    INDEX idx_status (match_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## External ID Mapping

### fb_league_external_ids
```sql
CREATE TABLE fb_league_external_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (league_id) REFERENCES fb_leagues(id) ON DELETE CASCADE,
    UNIQUE KEY uk_league_provider (league_id, provider),
    INDEX idx_provider_external (provider, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_team_external_ids
```sql
CREATE TABLE fb_team_external_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    UNIQUE KEY uk_team_provider (team_id, provider),
    INDEX idx_provider_external (provider, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_season_external_ids
```sql
CREATE TABLE fb_season_external_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    season_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (season_id) REFERENCES fb_seasons(id) ON DELETE CASCADE,
    UNIQUE KEY uk_season_provider (season_id, provider),
    INDEX idx_provider_external (provider, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_match_external_ids
```sql
CREATE TABLE fb_match_external_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE CASCADE,
    UNIQUE KEY uk_match_provider (match_id, provider),
    INDEX idx_provider_external (provider, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_player_external_ids
```sql
CREATE TABLE fb_player_external_ids (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES fb_players(id) ON DELETE CASCADE,
    UNIQUE KEY uk_player_provider (player_id, provider),
    INDEX idx_provider_external (provider, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Player Analytics

### fb_players
```sql
CREATE TABLE fb_players (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT,
    name VARCHAR(255) NOT NULL,
    position VARCHAR(50),
    age INT,
    nationality VARCHAR(100),
    height INT,
    weight INT,
    market_value DECIMAL(15,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES fb_teams(id) ON DELETE SET NULL,
    INDEX idx_team (team_id),
    INDEX idx_name (name),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_player_match_stats
```sql
CREATE TABLE fb_player_match_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_id BIGINT NOT NULL,
    match_id BIGINT NOT NULL,
    minutes_played INT,
    goals INT DEFAULT 0,
    assists INT DEFAULT 0,
    shots INT DEFAULT 0,
    shots_on_target INT DEFAULT 0,
    xg DECIMAL(5,3) DEFAULT 0.000,
    xa DECIMAL(5,3) DEFAULT 0.000,
    passes INT DEFAULT 0,
    pass_accuracy DECIMAL(5,2),
    key_passes INT DEFAULT 0,
    tackles INT DEFAULT 0,
    interceptions INT DEFAULT 0,
    duels_won INT DEFAULT 0,
    dribbles INT DEFAULT 0,
    fouls_drawn INT DEFAULT 0,
    fouls_committed INT DEFAULT 0,
    yellow_cards INT DEFAULT 0,
    red_cards INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_id) REFERENCES fb_players(id) ON DELETE CASCADE,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE CASCADE,
    UNIQUE KEY uk_player_match (player_id, match_id),
    INDEX idx_match (match_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Advanced Analytics

### fb_match_advanced_stats
```sql
CREATE TABLE fb_match_advanced_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    team_id BIGINT NOT NULL,
    ppda DECIMAL(8,2),
    ppda_att INT,
    ppda_def INT,
    field_tilt DECIMAL(5,2),
    xg_chain DECIMAL(5,3),
    xg_buildup DECIMAL(5,3),
    possession DECIMAL(5,2),
    deep_completions INT,
    final_third_entries INT,
    shots_from_box INT,
    shots_outside_box INT,
    set_piece_xg DECIMAL(5,3),
    counter_attack_xg DECIMAL(5,3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    UNIQUE KEY uk_match_team (match_id, team_id),
    INDEX idx_team (team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_team_form
```sql
CREATE TABLE fb_team_form (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    form_type VARCHAR(50) NOT NULL,
    form_data VARCHAR(50),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES fb_seasons(id) ON DELETE CASCADE,
    UNIQUE KEY uk_team_season_type (team_id, season_id, form_type),
    INDEX idx_season (season_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_h2h_records
```sql
CREATE TABLE fb_h2h_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    home_team_id BIGINT NOT NULL,
    away_team_id BIGINT NOT NULL,
    total_matches INT DEFAULT 0,
    home_wins INT DEFAULT 0,
    away_wins INT DEFAULT 0,
    draws INT DEFAULT 0,
    home_goals INT DEFAULT 0,
    away_goals INT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (home_team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    FOREIGN KEY (away_team_id) REFERENCES fb_teams(id) ON DELETE CASCADE,
    UNIQUE KEY uk_h2h (home_team_id, away_team_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Betting

### fb_bookmakers
```sql
CREATE TABLE fb_bookmakers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    website VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_match_odds
```sql
CREATE TABLE fb_match_odds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    bookmaker_id BIGINT NOT NULL,
    market_name VARCHAR(50) NOT NULL,
    home_win_odds DECIMAL(8,3),
    draw_odds DECIMAL(8,3),
    away_win_odds DECIMAL(8,3),
    over_25_odds DECIMAL(8,3),
    under_25_odds DECIMAL(8,3),
    btts_yes_odds DECIMAL(8,3),
    btts_no_odds DECIMAL(8,3),
    asian_handicap_home DECIMAL(5,2),
    asian_handicap_away DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE CASCADE,
    FOREIGN KEY (bookmaker_id) REFERENCES fb_bookmakers(id) ON DELETE CASCADE,
    INDEX idx_match (match_id),
    INDEX idx_bookmaker (bookmaker_id),
    INDEX idx_market (market_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_odds_history
```sql
CREATE TABLE fb_odds_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_odds_id BIGINT NOT NULL,
    home_win_odds DECIMAL(8,3),
    draw_odds DECIMAL(8,3),
    away_win_odds DECIMAL(8,3),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_odds_id) REFERENCES fb_match_odds(id) ON DELETE CASCADE,
    INDEX idx_match_odds (match_odds_id),
    INDEX idx_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Predictions

### fb_match_predictions
```sql
CREATE TABLE fb_match_predictions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,
    predicted_home_xg DECIMAL(5,3),
    predicted_away_xg DECIMAL(5,3),
    win_home_prob DECIMAL(5,4),
    draw_prob DECIMAL(5,4),
    win_away_prob DECIMAL(5,4),
    predicted_result VARCHAR(10),
    prediction_status VARCHAR(50) DEFAULT 'PENDING',
    is_premium BOOLEAN DEFAULT FALSE,
    value_bet_type VARCHAR(50),
    confidence_score DECIMAL(5,2),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE CASCADE,
    UNIQUE KEY uk_match (match_id),
    INDEX idx_status (prediction_status),
    INDEX idx_premium (is_premium)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_prediction_performance
```sql
CREATE TABLE fb_prediction_performance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prediction_id BIGINT NOT NULL,
    actual_home_score INT,
    actual_away_score INT,
    actual_result VARCHAR(10),
    is_correct BOOLEAN,
    accuracy_score DECIMAL(5,4),
    evaluated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (prediction_id) REFERENCES fb_match_predictions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_prediction (prediction_id),
    INDEX idx_correct (is_correct)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Content

### fb_content_templates
```sql
CREATE TABLE fb_content_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    variables JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_platform (platform),
    INDEX idx_type (template_type),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_social_posts
```sql
CREATE TABLE fb_social_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT,
    platform VARCHAR(50) NOT NULL,
    post_id VARCHAR(255),
    content TEXT NOT NULL,
    media_urls JSON,
    post_status VARCHAR(50) DEFAULT 'DRAFT',
    scheduled_at TIMESTAMP,
    published_at TIMESTAMP,
    engagement_stats JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (match_id) REFERENCES fb_matches(id) ON DELETE SET NULL,
    INDEX idx_match (match_id),
    INDEX idx_platform (platform),
    INDEX idx_status (post_status),
    INDEX idx_scheduled (scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_content_queue
```sql
CREATE TABLE fb_content_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    social_post_id BIGINT NOT NULL,
    queue_status VARCHAR(50) DEFAULT 'PENDING',
    priority INT DEFAULT 5,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (social_post_id) REFERENCES fb_social_posts(id) ON DELETE CASCADE,
    INDEX idx_status (queue_status),
    INDEX idx_priority (priority),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Monetization

### fb_subscription_plans
```sql
CREATE TABLE fb_subscription_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    billing_cycle VARCHAR(50) NOT NULL,
    stripe_price_id VARCHAR(255),
    features JSON,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_subscribers
```sql
CREATE TABLE fb_subscribers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    subscription_plan_id BIGINT NOT NULL,
    telegram_id BIGINT,
    twitter_handle VARCHAR(255),
    email VARCHAR(255),
    subscription_status VARCHAR(50) DEFAULT 'ACTIVE',
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP,
    auto_renew BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (subscription_plan_id) REFERENCES fb_subscription_plans(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user (user_id),
    INDEX idx_status (subscription_status),
    INDEX idx_plan (subscription_plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_tip_transactions
```sql
CREATE TABLE fb_tip_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscriber_id BIGINT NOT NULL,
    match_prediction_id BIGINT NOT NULL,
    delivery_status VARCHAR(50) DEFAULT 'PENDING',
    delivery_platform VARCHAR(50),
    delivered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (subscriber_id) REFERENCES fb_subscribers(id) ON DELETE CASCADE,
    FOREIGN KEY (match_prediction_id) REFERENCES fb_match_predictions(id) ON DELETE CASCADE,
    INDEX idx_subscriber (subscriber_id),
    INDEX idx_prediction (match_prediction_id),
    INDEX idx_status (delivery_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_affiliate_tracking
```sql
CREATE TABLE fb_affiliate_tracking (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    affiliate_code VARCHAR(50) NOT NULL UNIQUE,
    subscriber_id BIGINT,
    click_count INT DEFAULT 0,
    conversion_count INT DEFAULT 0,
    commission_rate DECIMAL(5,4),
    total_commission DECIMAL(15,2) DEFAULT 0.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (subscriber_id) REFERENCES fb_subscribers(id) ON DELETE SET NULL,
    INDEX idx_code (affiliate_code),
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## System Monitoring

### fb_crawler_health
```sql
CREATE TABLE fb_crawler_health (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    crawler_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    last_run TIMESTAMP,
    last_success TIMESTAMP,
    error_count INT DEFAULT 0,
    last_error_message TEXT,
    success_rate DECIMAL(5,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_crawler (crawler_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_data_quality_metrics
```sql
CREATE TABLE fb_data_quality_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_type VARCHAR(100) NOT NULL,
    metric_name VARCHAR(255) NOT NULL,
    metric_value DECIMAL(15,4),
    threshold DECIMAL(15,4),
    status VARCHAR(50) DEFAULT 'OK',
    details JSON,
    measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_type (metric_type),
    INDEX idx_status (status),
    INDEX idx_measured (measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### fb_notification_queue
```sql
CREATE TABLE fb_notification_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    error_message TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_type (notification_type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Provider Enum Values

The `provider` field in external ID mapping tables should use one of these values:
- SOFA_SCORE
- UNDERSTATS
- API_FOOTBALL

## Execution Instructions

To execute this schema:

```bash
# Save as DATABASE_SCHEMA.sql
mysql -u root -p mmo_db < DATABASE_SCHEMA.sql
```

Or execute directly in MySQL client:

```sql
SOURCE DATABASE_SCHEMA.sql;
```
