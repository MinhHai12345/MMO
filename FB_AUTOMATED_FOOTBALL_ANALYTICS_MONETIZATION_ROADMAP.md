# FB — Lộ trình xây dựng hệ thống phân tích bóng đá tự động và kiếm tiền

> **Mục tiêu:** phát triển dự án FB (Java 17, Spring Boot 3.4.x) thành hệ thống thu thập và kiểm định dữ liệu bóng đá, tạo phân tích trước trận có thể giải thích, đánh giá mô hình theo thời gian, tự động tạo nội dung và thử nghiệm mô hình doanh thu.
>
> **Lưu ý:** không có lộ trình kỹ thuật nào bảo đảm tạo ra thu nhập. Doanh thu phụ thuộc nhu cầu thị trường, chất lượng nội dung/sản phẩm, phân phối, chi phí, quyền dữ liệu và chính sách nền tảng. Định hướng là phân tích bóng đá, không quảng bá chắc thắng hoặc khuyến khích cá cược.

## 1. Tóm tắt hiện trạng và định nghĩa hoàn thành

### Hiện trạng cần dùng làm điểm xuất phát
- Đã chạy `syncCoreData` và có dữ liệu core trong database.
- Dự án có nền tảng Spring Boot, domain entities, provider clients, ingestion và analytics đang được phát triển.
- Cần audit coverage lịch sử, kết quả, standings theo mùa, mappings và nguồn thống kê trước khi thiết kế thêm.
- Xác minh chính xác provider/gói API đang dùng, quota, coverage, quyền lưu trữ và quyền sử dụng thương mại.

### Hệ thống chỉ được coi là vận hành tốt khi
1. Dữ liệu có nguồn gốc, timestamp và quality metrics.
2. Sync/backfill chạy lặp lại an toàn, không sinh duplicate.
3. Feature chỉ dùng dữ liệu có thể biết trước thời điểm dự đoán.
4. Có walk-forward backtest, calibration và kết quả tái lập được.
5. Có quy trình tạo, kiểm tra, duyệt và xuất bản nội dung.
6. Có kênh phân phối và thử nghiệm mô hình doanh thu.
7. Có monitoring, cảnh báo, backup/restore, bảo mật và kiểm soát chi phí.
8. Đo doanh thu thực tế riêng với dự báo; không tuyên bố hệ thống kiếm tiền khi chưa có bằng chứng.

## 2. Kiến trúc đích

```text
Providers
  -> Provider clients / rate limits / retry
  -> Ingestion orchestration / checkpoints
  -> Mapping + normalization
  -> Canonical database + data lineage
  -> Coverage audit / data quality
  -> Historical backfill / standings / statistics
  -> Point-in-time feature pipeline
  -> Dataset builder
  -> Baseline models
  -> Walk-forward backtesting + calibration
  -> Match analysis API
  -> Content engine + factual validation
  -> Human review / policy checks
  -> Publishing adapters
  -> Audience analytics / revenue experiments
```

Nguyên tắc:
- Provider-specific logic nằm ở adapter/client; analytics chỉ dùng domain chuẩn.
- Mapping external ID phải rõ provider/resource, không chỉ match theo tên.
- Mọi job phải idempotent, có giới hạn retry, timeout, quota và trạng thái.
- Ghi rõ thời điểm dữ liệu có thể biết; tránh leakage.
- Gắn version cho dataset, feature, model, prediction và content.
- Tách ingestion, analytics, prediction, content, publishing và monetization.
- Tạo bản nháp và duyệt thủ công trước khi bật auto-publish.
- Không thêm entity/field khi chưa có use case, định nghĩa, nguồn và test.

# PHẦN A — DATA FOUNDATION

## Giai đoạn 1 — Data Coverage Audit (P0, làm ngay)

**Mục tiêu:** biết chính xác database có gì, thiếu gì và đã đủ cho analytics chưa.

### Việc cần làm
1. Kiểm kê League, Season, Team, Match, Standing, mapping và các bảng analytics hiện có.
2. Báo cáo số record theo provider, league, season, năm và trạng thái trận.
3. Đếm trận đã kết thúc, trận có score hợp lệ, trận thiếu score, trận chưa đá, hoãn/hủy/abandoned.
4. Tìm match thiếu league/season/team/kickoff hoặc mapping external ID.
5. Kiểm tra duplicate theo external ID và các trường hợp nghi trùng theo league, kickoff, home/away.
6. Báo cáo standings theo từng league-season: có/không, số đội, loại bảng, lần sync gần nhất.
7. Xác minh `app.football-data.seasons` có bind đúng và season-year có cùng quy ước với provider.
8. Phân loại thiếu dữ liệu: chưa sync, provider không hỗ trợ, response rỗng, lỗi request, validation reject.
9. Làm report read-only qua SQL/CLI/job/endpoint nội bộ; không làm thay đổi dữ liệu.
10. Lưu snapshot report nếu cần theo dõi xu hướng.

### Test
- DB rỗng; core-only; season không có match; match chưa đá/đã đá; thiếu score; standings thiếu đội; provider rỗng/lỗi; chạy audit hai lần.

### Nghiệm thu
- Có report theo league-season và missing-data reasons.
- Lỗi có thể hành động, không chỉ ghi “sync success”.
- Audit không mutate domain data.

**Đầu ra:** `docs/operations/data-coverage-audit.md`, báo cáo coverage và backlog ingestion có thứ tự ưu tiên.

## Giai đoạn 2 — Data contract và validation

1. Viết data dictionary: định nghĩa, đơn vị, nullable, source, timestamp, cập nhật.
2. Chuẩn hóa trạng thái trận và mapping status của từng provider.
3. Phân biệt full-time, half-time, extra-time và penalty score nếu provider hỗ trợ.
4. Không coi trận chưa kết thúc là kết quả cuối; không tự suy ra score thiếu.
5. Chuẩn hóa kickoff/timezone, postponed, cancelled, abandoned và unknown.
6. Chốt uniqueness cho external mappings và chiến lược source-of-truth khi provider xung đột.
7. Đặt validation ở mapper/service boundary và database constraints phù hợp.
8. Thêm error categories: transient, permanent, unsupported, invalid data.
9. Metrics: fetched/inserted/updated/skipped/rejected, retries, latency và job duration.
10. Không log token, auth header hoặc secrets.

**Nghiệm thu:** các rule nghiệp vụ có tests; upsert chạy lại không duplicate; null không bị đổi thành 0 nếu hai giá trị khác nghĩa.

## Giai đoạn 3 — Historical seasons và backfill

1. Chọn nhóm giải đầu tiên dựa trên coverage, nhu cầu audience và quota.
2. Cấu hình seasons qua properties/environment variables, không hard-code năm.
3. Xác minh provider biểu diễn season bằng start year, end year hay ID.
4. Sync league/competition và season trước fixtures.
5. Backfill fixtures theo league-season; xử lý pagination.
6. Đồng bộ score/result cho trận đã kết thúc.
7. Hỗ trợ checkpoint/resume theo batch.
8. Tách lịch backfill lịch sử khỏi sync trận sắp diễn ra và kết quả mới.
9. Retry timeout/429/5xx theo backoff; không retry vô hạn lỗi permanent.
10. Tôn trọng quota/concurrency; dành quota cho các job quan trọng.
11. Đánh dấu season unsupported/unavailable khi provider không cung cấp; không giả làm lỗi nội bộ.
12. Đối soát coverage giữa response và database theo từng season.
13. Đảm bảo re-run update record hiện tại thay vì tạo duplicate.
14. Theo dõi lịch kickoff thay đổi và mapping home/away.
15. Báo cáo tiến độ, lỗi cuối và thời điểm sync cuối.

**Nghiệm thu:** backfill có thể resume, re-run an toàn, có coverage report và không vượt quota cấu hình.

## Giai đoạn 4 — Standings chính xác theo season

1. Kiểm tra response schema thực tế của provider.
2. Xác định response có TOTAL, HOME, AWAY và/hoặc group.
3. Chọn persist TOTAL-only nếu chỉ cần bảng tổng; hoặc lưu type/group nếu cần phân tích phân loại.
4. Không map nhiều loại bảng vào cùng một row mà không có discriminator.
5. Chốt uniqueness theo league-season-team và các dimension type/group thực sự dùng.
6. Không để response HOME/AWAY ghi đè TOTAL.
7. Sync tất cả season đã cấu hình nếu provider hỗ trợ.
8. Xử lý season chưa bắt đầu, group standings, response rỗng và giải không có standings.
9. Đối soát played, won/drawn/lost, goals for/against, goal difference và points.
10. Ghi metadata freshness và tests cho nhiều response type.

**Nghiệm thu:** không ghi đè chéo; dữ liệu gắn đúng league-season; chạy lại không duplicate; report phát hiện season thiếu standings.

## Giai đoạn 5 — Provider capability matrix và match statistics

1. Lập ma trận provider/resource: competitions, seasons, fixtures/results, standings, statistics, lineups, player stats, injuries, events và odds nếu thật sự cần.
2. Với từng trường, ghi coverage, số mùa lịch sử, quota, chi phí, caching/retention/commercial-use terms.
3. Ưu tiên nguồn chính thức, điều khoản rõ ràng; không scrape để vượt bảo vệ hoặc vi phạm điều khoản.
4. Tách capability khỏi provider selection; service phải biết resource có được hỗ trợ.
5. Trước khi thêm provider thứ hai, thiết kế external ID mapping và entity resolution.
6. Không ghép đội/trận chỉ bằng tên; ambiguous mappings cần review.
7. Chỉ thêm field/entity khi có source, use case, định nghĩa, update cadence và tests.
8. Không tự điền dữ liệu thiếu bằng giá trị giả.
9. Lưu raw payload chỉ khi terms cho phép; nếu không, dùng metadata/hash/fixtures kiểm thử phù hợp.
10. Xác định fallback và quy tắc khi provider bất đồng.

**Nghiệm thu:** có capability matrix; analytics không giả định trường không tồn tại; mappings không tạo duplicate.

# PHẦN B — FEATURE VÀ DATASET

## Giai đoạn 6 — Feature specification và point-in-time correctness

### Feature khởi đầu
- Team strength/rating theo thời gian.
- Form 5/10 trận trước kickoff.
- Home/away performance.
- Goals for/against và league scoring baseline.
- Rest days nếu lịch sử đủ chính xác.
- Opponent strength.
- Lineup/injury chỉ khi nguồn đáng tin và đã có trước kickoff.
- Flags cho sample nhỏ, missingness và uncertainty.

### Việc cần làm
1. Tạo feature dictionary: công thức, cửa sổ, cutoff, missing policy và version.
2. Mỗi feature phải phản ánh dữ liệu có thể biết tại prediction timestamp.
3. Chỉ dùng trận đã hoàn tất trước cutoff.
4. Không dùng standings cuối mùa để dự đoán trận diễn ra đầu mùa.
5. Xử lý season mới bằng prior/shrinkage, không dùng tương lai.
6. Tránh rating cực đoan từ sample nhỏ.
7. Viết mutation test: thay đổi kết quả trong tương lai không đổi feature lịch sử.
8. Test cùng ngày, hoãn trận và timezone.
9. Gắn version cho feature pipeline.
10. Chọn compute-on-demand hoặc materialize dựa trên hiệu năng/tính tái lập.

**Nghiệm thu:** mọi feature có test; feature vector tái tạo được; không leakage.

## Giai đoạn 7 — Dataset builder và split

1. Chọn prediction timestamp nhất quán (ví dụ trước kickoff 24 giờ).
2. Chốt target riêng: 1X2, home/away goals hoặc total goals.
3. Không trộn nhãn khác nghĩa.
4. Dùng chronological train/validation/test; không random split làm kết quả đánh giá chính.
5. Mỗi sample có match ID, cutoff, feature/target version và snapshot/reference.
6. Loại hoặc gắn nhãn match bị hủy/abandoned/score thiếu theo rule định trước.
7. Tránh cùng một trận xuất hiện ở train/test qua duplicate/provider IDs.
8. Định nghĩa sample minimum cho từng league/season.
9. Ghi số sample bị loại và lý do.
10. Ghi giới hạn coverage và format giải.

**Nghiệm thu:** dataset deterministic, có exclusion reasons, không duplicate/leakage.

# PHẦN C — MODEL, BACKTEST VÀ CALIBRATION

## Giai đoạn 8 — Baseline model

1. Bắt đầu với Poisson cho home/away goals, có attack/defence strength và home advantage.
2. Có thể thêm Elo-style rating làm baseline độc lập.
3. Xác suất 1X2 phải được suy ra và kiểm tra rõ.
4. Có prior/fallback cho đội ít dữ liệu và season mới.
5. Trả về phân phối xác suất, không chỉ một nhãn.
6. Lưu model version, fit cutoff, tham số và dataset version.
7. So sánh với baseline đơn giản.
8. Không dùng deep learning trước khi baseline và dataset đáng tin.
9. Kiểm tra xác suất hợp lệ, tổng xác suất xấp xỉ 1 và missing-data cases.
10. Không kết luận từ một vài trận mẫu.

**Nghiệm thu:** kết quả tái lập; output hợp lệ; baseline dùng làm chuẩn cho mọi cải tiến.

## Giai đoạn 9 — Walk-forward backtesting

1. Chọn cửa sổ train ban đầu.
2. Fit chỉ bằng dữ liệu có trước cutoff T.
3. Dự đoán khoảng thời gian kế tiếp.
4. Ghi prediction snapshot trước khi outcome xảy ra.
5. Khi kết quả về, tính metrics.
6. Tiến cutoff và lặp lại theo thời gian.
7. Báo cáo theo league, season, time range và data-quality segment.
8. Ghi dataset/feature/model/config version cho mỗi run.
9. So sánh model với baseline trên đúng cùng các trận.
10. Kiểm tra drift qua các mùa.
11. Không tune trên test set rồi gọi kết quả đó là test độc lập.
12. Ghi sample size và uncertainty; không diễn giải chênh lệch nhỏ quá mức.

### Metrics
- Log loss: đánh giá chất lượng xác suất.
- Brier score: sai số bình phương xác suất.
- Calibration/reliability curve và ECE với sample đủ lớn.
- Accuracy là metric phụ, không đủ để đánh giá xác suất.
- Coverage, sample size và tỷ lệ bị loại.
- Metrics theo league/season để tránh một giải lớn che khuất giải yếu.

Không lấy lợi nhuận cá cược làm metric chính hoặc biến backtest thành lời hứa thu nhập.

**Nghiệm thu:** không leakage; có baseline comparison, sample counts, out-of-time metrics và khả năng tái lập.

## Giai đoạn 10 — Calibration, registry và drift

1. Vẽ reliability diagram cho từng outcome.
2. Thử calibration như isotonic/Platt/temperature scaling nếu phù hợp.
3. Fit calibrator trên calibration/validation data, không phải test set.
4. Version model và calibrator riêng.
5. Quản lý champion/candidate, promote và rollback.
6. Theo dõi metrics khi outcomes thực tế về.
7. Theo dõi drift input, missingness, league coverage và output distribution.
8. Nếu quality gate không đạt, dùng fallback hoặc không xuất bản dự đoán.
9. Không tự promote vì accuracy ngắn hạn tăng.
10. Mỗi prediction phải truy ra model/calibrator version.

**Nghiệm thu:** có calibration report, rollback và quality gate.

# PHẦN D — ANALYSIS VÀ CONTENT

## Giai đoạn 11 — Match analysis API

1. Tạo service interface/implementation theo convention repository.
2. Tách DTO khỏi JPA entities.
3. API phân tích theo match ID và danh sách trận sắp tới.
4. Trả form, home/away splits, xác suất, uncertainty, factors và data limitations.
5. Có timestamp, model version và data freshness.
6. Cache theo match/model/prediction timestamp khi phù hợp.
7. Tái tính khi dữ liệu mới đến theo policy; không âm thầm sửa lịch sử.
8. Trả `insufficient data` khi thiếu dữ liệu thay vì bịa.
9. Bảo vệ management endpoints và không expose raw payload/secrets.
10. Có contract tests và API documentation.

**Nghiệm thu:** API có version/timestamp, giải thích được, không dùng dữ liệu sau kickoff.

## Giai đoạn 12 — Content engine

### Định dạng thử nghiệm
- Phân tích trước trận.
- Tóm tắt form hai đội.
- Bài giải thích chỉ số/chiến thuật.
- Tổng kết sau trận dựa trên outcome thật.
- Video script dài và Shorts script.
- Bản tin tuần hoặc báo cáo giải.

### Công việc
1. Xây content schema với match, competition, kickoff, stats, prediction snapshot và source metadata.
2. Bắt đầu từ template; chỉ dùng LLM khi tăng chất lượng đáng kể.
3. Tách factual claims khỏi diễn giải.
4. Mọi số liệu phải trace về source/database và timestamp.
5. Không bịa quote, lineup, injuries, tactics hoặc nguồn.
6. Nếu thiếu dữ liệu, bỏ claim hoặc ghi rõ giới hạn.
7. Tạo voice/structure nhất quán và chống trùng lặp.
8. Sinh nhiều định dạng từ cùng một content package.
9. Validate tên đội, giải, timezone, score, links và disclaimer.
10. Lưu content version cùng data/model snapshot.
11. Có human review queue và approve/reject/edit.
12. Có quy trình sửa/gỡ khi phát hiện sai.
13. Chỉ bật auto-publish sau giai đoạn draft/review ổn định.

### Quality gate
- Số liệu/tên/thời gian chính xác.
- Không có claim không được hỗ trợ.
- Prediction khớp snapshot.
- Không hứa hẹn kết quả chắc chắn hoặc khuyến khích cá cược.
- Có quyền sử dụng hình ảnh, logo, video, nhạc và dữ liệu.
- Disclosure tài trợ/affiliate khi cần.

## Giai đoạn 13 — Publishing và distribution

1. Chọn một kênh chính và tối đa một kênh phụ lúc đầu.
2. Kiểm tra API publishing và eligibility từng nền tảng.
3. Không bypass anti-bot, quota hoặc policy.
4. Viết adapter riêng cho mỗi nền tảng.
5. State machine: draft, scheduled, published, failed, rejected.
6. Lưu external post ID, URL, status và timestamps.
7. Dùng idempotency để tránh đăng trùng khi retry.
8. Có rate limits, scheduling, queue và dead-letter handling.
9. Quản lý license của ảnh/video/font/music.
10. Có review gate, pause/resume và kill switch.
11. Gắn tracking/UTM cho website, newsletter và sản phẩm.
12. Đo impressions, watch time, completion, CTR, returning users và subscribers khi có dữ liệu.
13. Không dùng engagement giả hoặc spam.
14. Không để auto-reply phát ngôn vượt chính sách.

**Nghiệm thu:** không đăng trùng, có thể dừng toàn bộ publishing, truy về content/data version.

# PHẦN E — MONETIZATION

## Giai đoạn 14 — Chọn mô hình và kiểm chứng nhu cầu

### Các mô hình có thể thử
1. Quảng cáo website/video — cần audience và eligibility.
2. Newsletter miễn phí để xây audience; gói trả phí khi có nhu cầu.
3. Membership với phân tích chuyên sâu, dashboard và lịch sử model performance.
4. Digital products: báo cáo giải, tài liệu học phân tích dữ liệu bóng đá.
5. B2B/API chỉ khi terms cho phép redistribution và có khách hàng thật.
6. Affiliate phù hợp, minh bạch; hoa hồng không chi phối phân tích.
7. Sponsorship khi có audience và disclosure rõ.

### Thực hiện
1. Chọn audience cụ thể: fan một giải, người thích chiến thuật, người học football analytics hoặc độc giả thống kê.
2. Phỏng vấn/landing-page test trước khi xây tính năng trả phí lớn.
3. Tạo sản phẩm thử nhỏ: newsletter, report hoặc dashboard.
4. Theo dõi reach, CTR, signup conversion, returning users, free-to-paid conversion, churn, revenue/user và support/refund.
5. Đo willingness-to-pay bằng hành vi thực tế, không chỉ khảo sát ý thích.
6. Thử offer/price theo cohort có kiểm soát.
7. Có privacy, consent, unsubscribe và data deletion workflow phù hợp.
8. Theo dõi data, hosting, LLM, rendering, email và support costs.
9. Tính gross margin/contribution margin; dừng hướng có chi phí vượt giá trị.
10. Không coi view hoặc follower là doanh thu.

**Nghiệm thu:** có dữ liệu chuyển đổi/doanh thu thực tế, không chỉ forecast.

## Giai đoạn 15 — Revenue analytics và experimentation

1. Chuẩn hóa content/campaign IDs và UTM.
2. Theo dõi funnel từ post → site → signup → paid conversion.
3. Phân biệt organic, paid, referral và affiliate traffic.
4. Dashboard theo content type, league, topic, channel và cohort.
5. Theo dõi retention theo tuần/tháng.
6. Tính cost per content, cost per subscriber và contribution margin.
7. Thử title, length, format và schedule có kiểm soát.
8. Không kết luận từ mẫu quá nhỏ.
9. Alert khi conversion/retention giảm bất thường.
10. Monthly review: scale, iterate hoặc stop.

**Nghiệm thu:** funnel, attribution privacy-aware, chi phí và doanh thu được đo riêng.

# PHẦN F — RELIABILITY, SECURITY VÀ PRODUCTION

## Giai đoạn 16 — Job orchestration

1. Tách job: core sync, historical backfill, upcoming fixtures/results, standings, stats, features, predictions, content, publishing và metrics.
2. Khai báo dependency; feature chỉ chạy khi nguồn cần thiết đã sẵn sàng.
3. Dùng lock/distributed coordination nếu có nhiều instance.
4. Có idempotency, checkpoint, bounded retry/backoff và timeout.
5. Có quota budget cho từng provider; ưu tiên fixtures sắp tới và kết quả mới.
6. Phân loại transient/permanent errors.
7. Có job status, timestamps, processed records và error counts.
8. Có dead-letter handling và circuit breaker khi cần.
9. Có dry-run cho tác vụ nguy hiểm và kill switch cho ingestion/model/publishing.
10. Batch backfill để tránh memory pressure.
11. Không giữ transaction DB trong lúc chờ network lâu.
12. Theo dõi thread pool, DB pool, latency và job duration.

**Nghiệm thu:** job resume được, không chạy trùng nguy hiểm, có quota protection và cảnh báo stale data.

## Giai đoạn 17 — Observability, CI/CD, database ops

1. Structured logs với correlation/run ID.
2. Không log API keys hoặc auth headers.
3. Metrics cho latency, throughput, retry, rate limits, job success/failure, freshness và DB pool.
4. Dashboard ingestion/content health.
5. Alert khi data sắp tới bị stale hoặc provider outage kéo dài.
6. CI: compile, unit/integration tests, static checks và migration validation.
7. Smoke tests cho startup/repository queries quan trọng.
8. Versioned migrations; không thay schema production tùy tiện.
9. Backup định kỳ và thực sự thử restore.
10. Tách dev/staging/prod.
11. Secrets ngoài repository.
12. Pin dependencies và theo dõi security advisories.
13. Có release rollback plan và retention policy.

**Nghiệm thu:** CI xanh, restore được, có alerts và rollback.

## Giai đoạn 18 — Legal, privacy và platform policy

1. Rà soát provider terms: quota, attribution, caching, retention, commercial use và redistribution.
2. Không mặc định dữ liệu API được phép bán lại.
3. Xác minh quyền logo, ảnh, video highlights, nhạc và fonts.
4. Kiểm tra monetization policies tại thời điểm triển khai; chính sách thay đổi.
5. Tránh nội dung tự động hàng loạt ít giá trị hoặc lặp lại.
6. Disclosure sponsorship/affiliate khi cần.
7. Có privacy policy, consent, unsubscribe và deletion workflow phù hợp.
8. Bảo vệ admin endpoints, API keys, webhook secrets và phân quyền.
9. Có incident runbook cho token leak, spam publish hoặc factual error.
10. Có khả năng gỡ/sửa bài và dừng publishing ngay.

**Nghiệm thu:** quyền dữ liệu/assets rõ ràng; không bật commercial redistribution khi chưa có quyền.

# PHẦN G — MVP, SCALE VÀ KẾ HOẠCH PR

## Giai đoạn 19 — MVP

MVP cần chứng minh một vòng lặp nhỏ hoàn chỉnh:
1. Đồng bộ đáng tin cậy một nhóm giải.
2. Feature pipeline và baseline model.
3. Walk-forward report.
4. Một định dạng match analysis có chất lượng.
5. Một kênh xuất bản.
6. Analytics đo người xem và chuyển đổi.
7. Thử nghiệm audience/newsletter/sản phẩm.
8. Theo dõi chi phí và doanh thu thực tế.

Không làm ở MVP:
- Không thêm nhiều model phức tạp trước baseline.
- Không tích hợp nhiều provider cùng lúc.
- Không tự động đăng mọi nền tảng ngay.
- Không xây paywall lớn trước khi kiểm chứng nhu cầu.
- Không hứa chắc thắng hoặc bảo đảm thu nhập.
- Không bán lại dữ liệu khi quyền chưa rõ.

## Giai đoạn 20 — Scale sau khi có tín hiệu

### Kỹ thuật
- Thêm league theo coverage, audience và quota.
- Thêm provider chỉ khi cải thiện dữ liệu đủ để biện minh chi phí.
- Thử model/feature mới qua benchmark.
- Tự động hóa video/assets bằng tài nguyên được cấp phép.
- Tối ưu cache/queue/database theo metrics.
- Không microservice hóa sớm nếu tải chưa yêu cầu.

### Kinh doanh
- Tập trung format có retention tốt.
- Xây website/newsletter để giảm phụ thuộc một nền tảng.
- Thử sản phẩm trả phí với nhóm nhỏ.
- Chỉ mở affiliate/sponsorship khi phù hợp và minh bạch.
- Tính unit economics trước khi tăng chi phí.

## Kế hoạch PR đề xuất

| PR | Nội dung | Ưu tiên | Gate |
|---|---|---:|---|
| A | Coverage audit/report | P0 | Coverage theo league-season, missing reasons |
| B | Data contracts/validation | P0 | Rules có tests |
| C | Historical backfill/resume/idempotency | P0 | Backfill an toàn khi rerun |
| D | Standings correctness theo season/type | P0 | Không ghi đè TOTAL/HOME/AWAY |
| E | Provider capability matrix | P0 | Nguồn/giới hạn từng resource rõ |
| F | Feature dictionary + leakage tests | P0 | Không dùng dữ liệu tương lai |
| G | Dataset builder + chronological splits | P1 | Deterministic, exclusion reasons |
| H | Baseline model + evaluation harness | P1 | Baseline tái lập |
| I | Walk-forward backtesting | P1 | Out-of-time metrics và sample counts |
| J | Calibration + model registry | P1 | Calibration report và rollback |
| K | Match analysis API/DTO | P1 | Version/timestamp/uncertainty |
| L | Content engine + factual validation | P1 | Draft traceable về source |
| M | Review workflow + first publishing adapter | P1 | Idempotency, pause/kill switch |
| N | Audience analytics + landing/newsletter | P1 | Funnel cơ bản |
| O | Monetization experiment | P2 | Tín hiệu nhu cầu/chuyển đổi thực |
| P | Production hardening/security/backup | P0 trước production | CI, monitoring, restore, secrets |
| Q | Scale theo bằng chứng | P2 | Chất lượng và business signal đã có |

Trước mỗi PR, kiểm tra repository hiện tại để tránh viết lại chức năng đã tồn tại. PR nhỏ, một mục tiêu, test và tài liệu rõ ràng.

## Definition of Done cho mỗi PR

- [ ] Phạm vi rõ, không refactor không liên quan.
- [ ] Idempotency và duplicate behavior được kiểm tra.
- [ ] Unit tests cho logic nghiệp vụ.
- [ ] Integration/contract tests khi phù hợp.
- [ ] Error handling, structured logs, không lộ secrets.
- [ ] Config có default và docs.
- [ ] Không hard-code seasons, IDs, credentials hoặc môi trường.
- [ ] Migration nếu schema thay đổi.
- [ ] CI/build được chạy và ghi kết quả trung thực.
- [ ] Có rollback/disable path cho tính năng rủi ro.
- [ ] Không tuyên bố tests pass nếu chưa chạy.

# KPI VÀ QUALITY GATES

## Data
- Coverage theo league-season.
- Tỷ lệ trận kết thúc có score hợp lệ.
- Tỷ lệ match đủ league/season/home/away/kickoff.
- Unresolved external mappings.
- Data freshness theo resource.
- Duplicate và validation failures.
- Job success rate, duration và quota consumption.

## Model
- Log loss/Brier score so với baseline.
- Calibration theo outcome và confidence bucket.
- Out-of-time sample count.
- Metrics theo league/season.
- Missing-data exclusion rate.
- Input/output drift.
- Reproducibility.

## Content
- Draft approval rate.
- Factual errors mỗi bài.
- Thời gian từ data update tới draft.
- CTR, watch time, completion, return viewers và subscriber conversion.
- Tỷ lệ bài phải sửa/gỡ.
- Cost per content.

## Business
- Returning users.
- Subscriber growth/retention.
- Conversion và churn.
- Revenue theo channel.
- Gross/contribution margin.
- Provider, hosting, LLM, rendering và email cost.
- Refund/support rate.
- Revenue thực tế tách khỏi forecast.

## Stop rules
- Dừng auto-publishing khi có factual error nghiêm trọng hoặc policy violation.
- Không promote model nếu không vượt baseline trên out-of-time evaluation.
- Không mở rộng provider nếu cost/quota tăng mà coverage không cải thiện đủ.
- Dừng thử nghiệm doanh thu khi đã đạt sample/time window định trước mà không có tín hiệu.
- Không tiếp tục chi tiêu chỉ vì đã đầu tư nhiều công sức.

# Việc làm ngay sau `syncCoreData`

## Bước 1 — Audit database
- [ ] Đếm league, season, team, match.
- [ ] Đếm trận kết thúc có score hợp lệ.
- [ ] Kiểm tra season đã cấu hình có record trong DB.
- [ ] Kiểm tra standings từng league-season.
- [ ] Kiểm tra provider mappings và duplicate.
- [ ] Ghi lỗi dữ liệu có thể tái hiện.

**Gate:** chưa làm feature engineering cho tới khi biết coverage và lỗi.

## Bước 2 — Sửa lỗi theo ảnh hưởng
Ưu tiên: (1) duplicate/sai liên kết; (2) season mapping; (3) status/score; (4) standings type bị ghi đè; (5) thiếu lịch sử; (6) freshness/retry/recovery.

**Gate:** rerun sync không phá dữ liệu và audit xác nhận.

## Bước 3 — Backfill lịch sử
- [ ] Chọn giải/mùa theo quota.
- [ ] Chạy từng batch, resume khi lỗi.
- [ ] Đối soát kết quả.
- [ ] Rerun để kiểm tra idempotency.

**Gate:** đủ dữ liệu cho backtest hoặc giới hạn coverage được ghi rõ.

## Bước 4 — Feature contract
- [ ] Chốt target/cutoff.
- [ ] Viết feature dictionary.
- [ ] Test chống leakage.
- [ ] Xây dataset builder.

**Gate:** thay đổi kết quả tương lai không đổi feature lịch sử trước cutoff.

## Bước 5 — Baseline/backtest
- [ ] Xây baseline.
- [ ] Chạy walk-forward.
- [ ] So sánh baseline.
- [ ] Kiểm tra calibration.
- [ ] Lưu version và report.

**Gate:** có bằng chứng out-of-time trước khi xuất bản dự đoán thường xuyên.

## Bước 6 — Nội dung thử nghiệm
- [ ] Một template analysis.
- [ ] Một nhóm giải nhỏ.
- [ ] Validate factual claims.
- [ ] Review thủ công.
- [ ] Xuất bản một kênh.
- [ ] Đo phản hồi.

**Gate:** factual error thấp, quy trình sản xuất có thể lặp lại.

## Bước 7 — Kiểm chứng doanh thu
- [ ] Chọn audience cụ thể.
- [ ] Landing page/newsletter hoặc sản phẩm thử.
- [ ] Đo signup và returning users.
- [ ] Thử offer trả phí nhỏ khi có tín hiệu.
- [ ] Tính chi phí/margin.
- [ ] Scale, iterate hoặc stop theo số liệu.

**Gate:** chỉ gọi là mô hình doanh thu đã xác nhận khi có chuyển đổi/doanh thu thực.

# Quyết định cần xác minh trong repository

1. Provider được chọn cho từng resource.
2. Config seasons có bind và được dùng đúng ở mọi ingestion service không.
3. Season-year mapping và external IDs.
4. Status/score normalization.
5. Standings chỉ TOTAL hay có type/group.
6. Nguồn statistics có quyền sử dụng không.
7. Analytics services/features/models nào đã tồn tại.
8. Prediction history/snapshot/versioning đã có chưa.
9. CI chạy compile/test nào và test còn thiếu.
10. Migration/backup/restore đã có chưa.
11. Quyền sử dụng thương mại và redistribution cho data/assets.

Không thêm entity/service trước khi xác minh hạng mục liên quan.

# Rủi ro và biện pháp

| Rủi ro | Biện pháp |
|---|---|
| Thiếu lịch sử/sai season | Coverage audit và backfill đối soát |
| Data leakage | Point-in-time features, chronological split, mutation tests |
| Calibration kém | Reliability reports, uncertainty, fallback |
| Provider đổi schema/quota | Contract tests, capability matrix, retry/rate limit |
| Sai mapping | External IDs, review ambiguity |
| Nội dung sai/lặp | Human review, factual gate, idempotency, kill switch |
| Vi phạm quyền data/assets | Rà soát license/terms trước thương mại hóa |
| Chi phí vượt doanh thu | Unit economics, budget, thử nghiệm nhỏ |
| Phụ thuộc nền tảng | Website/newsletter và audience riêng |
| Nhầm views với doanh thu | Đo funnel, retention, conversion và revenue thực |

# Kết luận

Thứ tự tổng thể:

**Audit dữ liệu → sửa chất lượng → backfill lịch sử → standings/statistics → feature pipeline → dataset point-in-time → baseline → walk-forward backtest → calibration → match analysis API → content engine → publishing có kiểm soát → audience analytics → kiểm chứng doanh thu → scale.**

Việc cần làm ngay là **Data Coverage Audit**. Mục tiêu không chỉ là crawler hay model dự đoán, mà là một vòng lặp có thể đo lường:

**Dữ liệu đáng tin → phân tích hữu ích → nội dung nhất quán → người dùng quay lại → sản phẩm có giá trị → doanh thu được xác minh → tái đầu tư có kiểm soát.**
