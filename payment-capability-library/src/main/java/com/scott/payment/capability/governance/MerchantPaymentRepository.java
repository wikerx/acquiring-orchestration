package com.scott.payment.capability.governance;

import com.scott.payment.capability.governance.MerchantPaymentModels.HistoryRow;
import com.scott.payment.capability.governance.MerchantPaymentModels.FeeReference;
import com.scott.payment.capability.governance.MerchantPaymentModels.MerchantOption;
import com.scott.payment.capability.governance.MerchantPaymentModels.Search;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MerchantPaymentRepository {
    private static final RowMapper<Item> ITEM = (row, index) -> new Item(
            row.getLong("id"), row.getString("application_no"), row.getString("merchant_id"),
            row.getString("item_key"), row.getString("operation"), row.getString("status"),
            row.getInt("round_no"), row.getString("source"), row.getLong("initial_submitter_user_id"),
            row.getLong("current_submitter_user_id"), row.getString("reason"),
            row.getLong("expected_grant_version"), row.getLong("version"), time(row, "submitted_at"));
    private static final RowMapper<Grant> GRANT = (row, index) -> new Grant(
            row.getLong("id"), row.getString("item_key"), row.getString("authorization_status"),
            row.getLong("version"), time(row, "effective_time"), time(row, "expire_time"), row.getString("source"));
    private final NamedParameterJdbcTemplate jdbc;

    public MerchantPaymentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 比较同一商户的新旧读视图，不把其他商户的审批流量带到主库。 */
    public ReadVersion readVersion(String merchant) {
        return jdbc.query("""
                SELECT m.payment_authorization_revision, m.payment_admission_mode, m.payment_baseline_batch_ref,
                       COALESCE((SELECT MAX(r.id) FROM merchant_review_record r
                                 WHERE r.review_scope = 'PAYMENT_METHOD' AND r.merchant_id = m.merchant_id), 0) review_id
                FROM base_merchant_info m WHERE m.merchant_id = :merchant AND m.deleted = 0
                """, Map.of("merchant", merchant), (row, index) -> new ReadVersion(
                row.getLong("payment_authorization_revision"), row.getLong("review_id"),
                row.getString("payment_admission_mode"), row.getString("payment_baseline_batch_ref")))
                .stream().findFirst().orElse(null);
    }

    public record ReadVersion(long authorization, long review, String mode, String baseline) {}

    public boolean merchantExists(String merchant) {
        return !jdbc.queryForList("""
                SELECT id FROM base_merchant_info WHERE merchant_id = :merchant AND deleted = 0
                """, Map.of("merchant", merchant), Long.class).isEmpty();
    }

    public List<MerchantOption> merchantOptions(MerchantPaymentScope scope, String keyword) {
        if (scope.empty()) {
            return List.of();
        }
        var parameters = new MapSqlParameterSource("keyword", keyword == null ? "" : keyword.trim());
        return jdbc.query("""
                SELECT merchant_id, merchant_name
                FROM base_merchant_info
                WHERE deleted = 0 AND (:keyword = '' OR merchant_id LIKE CONCAT('%', :keyword, '%')
                    OR merchant_name LIKE CONCAT('%', :keyword, '%')
                    OR merchant_short_name LIKE CONCAT('%', :keyword, '%'))
                """ + scopeFilter(scope, parameters) + " ORDER BY gmt_create DESC, id DESC LIMIT 50", parameters,
                (row, index) -> new MerchantOption(row.getString("merchant_id"), row.getString("merchant_name")));
    }

    public Optional<Long> lockMerchant(String merchant) {
        return query("""
                SELECT payment_authorization_revision FROM base_merchant_info
                WHERE merchant_id = :merchant AND deleted = 0 FOR UPDATE
                """, merchant, (row, index) -> row.getLong(1)).stream().findFirst();
    }

    public long advanceRevision(String merchant, long expected) {
        int count = jdbc.update("""
                UPDATE base_merchant_info SET payment_authorization_revision = payment_authorization_revision + 1
                WHERE merchant_id = :merchant AND deleted = 0 AND payment_authorization_revision = :expected
                """, Map.of("merchant", merchant, "expected", expected));
        if (count != 1) {
            throw new IllegalStateException("merchant authorization revision changed under lock");
        }
        return Math.incrementExact(expected);
    }

    public boolean verifiedMerchantAccount(String merchant, long account, long user) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys_account a
                JOIN sys_app p ON p.id = a.app_id AND p.app_code = 'MERCHANT' AND p.deleted = 0 AND p.status = 1
                JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0 AND u.status = 1
                WHERE a.id = :account AND a.user_id = :user AND a.merchant_id = :merchant
                  AND a.deleted = 0 AND a.status = 1 AND a.locked = 0 AND a.activation_status = 'ACTIVE'
                """, Map.of("merchant", merchant, "account", account, "user", user), Integer.class);
        return Integer.valueOf(1).equals(count);
    }

    public List<DirectoryEntry> directory(boolean lock) {
        String lockClause = lock ? " FOR SHARE" : "";
        if (lock) {
            jdbc.queryForList("""
                    SELECT id FROM sys_dict_type
                    WHERE dict_type IN ('acquiring_payment_method', 'card_brand') AND deleted = 0
                    ORDER BY id FOR SHARE
                    """, Map.of(), Long.class);
        }
        return jdbc.query("""
                SELECT d.dict_type, d.dict_value, d.dict_label, d.locale, d.dict_sort,
                       d.status = 1 AND t.status = 1 AS enabled
                FROM sys_dict_data d
                JOIN sys_dict_type t ON t.dict_type = d.dict_type AND t.deleted = 0
                WHERE d.dict_type IN ('acquiring_payment_method', 'card_brand') AND d.deleted = 0
                ORDER BY d.dict_sort, d.id
                """ + lockClause, Map.of(), (row, index) -> new DirectoryEntry(row.getString("dict_type"),
                row.getString("dict_value"), row.getString("dict_label"), row.getString("locale"), row.getBoolean("enabled")));
    }

    public Optional<Item> findItem(long id) {
        return jdbc.query("SELECT * FROM merchant_payment_application_item WHERE id = :id",
                Map.of("id", id), ITEM).stream().findFirst();
    }

    public Optional<Item> lockItem(String merchant, long id) {
        return jdbc.query("""
                SELECT * FROM merchant_payment_application_item
                WHERE merchant_id = :merchant AND id = :id FOR UPDATE
                """, Map.of("merchant", merchant, "id", id), ITEM).stream().findFirst();
    }

    public List<Item> items(String merchant) {
        return query("""
                SELECT a.* FROM merchant_payment_application_item a
                JOIN (SELECT item_key, MAX(id) id FROM merchant_payment_application_item
                      WHERE merchant_id = :merchant GROUP BY item_key) latest ON latest.id = a.id
                WHERE a.merchant_id = :merchant
                ORDER BY a.id DESC
                """, merchant, ITEM);
    }

    public List<Item> listBatches(List<Batch> batches) {
        if (batches.isEmpty()) {
            return List.of();
        }
        Map<String, String> scopes = batches.stream().collect(java.util.stream.Collectors.toMap(
                Batch::application, Batch::merchant));
        return jdbc.query("""
                SELECT * FROM merchant_payment_application_item
                WHERE application_no IN (:applications) AND merchant_id IN (:merchants) ORDER BY id
                """, Map.of("applications", scopes.keySet(), "merchants", scopes.values()), ITEM)
                .stream().filter(item -> item.merchant().equals(scopes.get(item.application()))).toList();
    }

    public List<ItemReview> itemReviews(String merchant, String application) {
        return jdbc.query("""
                SELECT id, application_item_id, review_action, from_status, to_status, review_comment, round_no, gmt_create
                FROM merchant_review_record
                WHERE review_scope = 'PAYMENT_METHOD' AND merchant_id = :merchant AND request_no = :application
                  AND application_item_id IS NOT NULL
                ORDER BY gmt_create DESC, id DESC
                """, Map.of("merchant", merchant, "application", application), (row, index) ->
                new ItemReview(row.getLong("application_item_id"), historyRow(row, index)));
    }

    public Optional<String> batchMerchant(MerchantPaymentScope scope, String application) {
        if (scope.empty()) {
            return Optional.empty();
        }
        var parameters = new MapSqlParameterSource("application", application);
        String scopeClause = scopeFilter(scope, parameters);
        return jdbc.queryForList("""
                SELECT merchant_id FROM merchant_payment_application_item
                WHERE application_no = :application
                """ + scopeClause + " LIMIT 1", parameters, String.class).stream().findFirst();
    }

    public PageResult<Batch> batches(MerchantPaymentScope scope, Search query) {
        if (scope.empty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), List.of());
        }
        var parameters = new MapSqlParameterSource();
        String filter = scopeFilter(scope, parameters)
                + exact("merchant_id", "merchant", query.getMerchantId(), parameters)
                + exact("status", "status", query.getStatus(), parameters)
                + exact("item_key", "itemKey", query.getItemKey(), parameters);
        String grouped = """
                SELECT merchant_id, application_no, MAX(submitted_at) recent, MAX(id) newest
                FROM merchant_payment_application_item WHERE 1 = 1
                """ + filter + " GROUP BY merchant_id, application_no";
        return page(grouped, "recent DESC, newest DESC", query, parameters,
                (row, index) -> new Batch(row.getString("merchant_id"), row.getString("application_no")));
    }

    public boolean hasActiveItem(String merchant, String itemKey) {
        return !jdbc.queryForList("""
                SELECT id FROM merchant_payment_application_item
                WHERE merchant_id = :merchant AND item_key = :itemKey AND active_slot = 1
                """, Map.of("merchant", merchant, "itemKey", itemKey), Long.class).isEmpty();
    }

    public long insertItem(String merchant, String itemKey, String application, String request, String reason,
                    String source, String operation, long grantVersion, Actor actor, LocalDateTime now) {
        var parameters = actorParameters(actor)
                .addValue("merchant", merchant).addValue("itemKey", itemKey).addValue("application", application)
                .addValue("request", request).addValue("reason", reason).addValue("source", source)
                .addValue("operation", operation).addValue("grantVersion", grantVersion).addValue("now", now);
        return insert("""
                INSERT INTO merchant_payment_application_item (
                    merchant_id, item_key, application_no, create_request_id, reason, source, operation, status,
                    expected_grant_version, active_slot, round_no, version, submitted_at, gmt_create, gmt_modified,
                    initial_submitter_app_code, initial_submitter_account_id, initial_submitter_user_id,
                    current_submitter_app_code, current_submitter_account_id, current_submitter_user_id)
                VALUES (:merchant, :itemKey, :application, :request, :reason, :source, :operation, 'PENDING_REVIEW',
                    :grantVersion, 1, 1, 1, :now, :now, :now, :app, :account, :user, :app, :account, :user)
                """, parameters);
    }

    public int transitionItem(Item item, String status, LocalDateTime now) {
        boolean open = "RETURNED".equals(status);
        return jdbc.update("""
                UPDATE merchant_payment_application_item
                SET status = :status, version = version + 1, active_slot = :slot, closed_at = :closed, gmt_modified = :now
                WHERE merchant_id = :merchant AND id = :id AND version = :version
                """, new MapSqlParameterSource("status", status)
                .addValue("slot", open ? 1 : null).addValue("closed", open ? null : now).addValue("now", now)
                .addValue("merchant", item.merchant()).addValue("id", item.id()).addValue("version", item.version()));
    }

    public int resubmit(Item item, Actor actor, String reason, LocalDateTime now) {
        return jdbc.update("""
                UPDATE merchant_payment_application_item
                SET status = 'PENDING_REVIEW', version = version + 1, round_no = round_no + 1, reason = :reason,
                    current_submitter_app_code = :app, current_submitter_account_id = :account,
                    current_submitter_user_id = :user, submitted_at = :now, gmt_modified = :now
                WHERE merchant_id = :merchant AND id = :id AND version = :version
                """, actorParameters(actor).addValue("reason", reason).addValue("now", now)
                .addValue("merchant", item.merchant()).addValue("id", item.id()).addValue("version", item.version()));
    }

    public List<Grant> grants(String merchant) {
        return query("SELECT * FROM merchant_payment_authorization WHERE merchant_id = :merchant", merchant, GRANT);
    }

    public Optional<FeeReference> currentFeeReference(String merchant, LocalDateTime now) {
        return jdbc.query("""
                SELECT v.id, v.version_no, v.effective_time
                FROM fee_plan p JOIN fee_plan_version v ON v.id = p.current_version_id AND v.plan_id = p.id
                WHERE p.plan_type = 'MERCHANT' AND p.merchant_id = :merchant AND p.status = 'ENABLED' AND p.deleted = 0
                  AND v.version_status = 'ACTIVE' AND v.deleted = 0 AND v.effective_time <= :now
                """, Map.of("merchant", merchant, "now", now), (row, index) ->
                new FeeReference(row.getString("id"), row.getString("version_no"), time(row, "effective_time")))
                .stream().findFirst();
    }

    public Optional<Grant> lockGrant(String merchant, String itemKey) {
        return jdbc.query("""
                SELECT * FROM merchant_payment_authorization
                WHERE merchant_id = :merchant AND item_key = :itemKey FOR UPDATE
                """, Map.of("merchant", merchant, "itemKey", itemKey), GRANT).stream().findFirst();
    }

    public void approve(Item item, Grant existing, Actor actor, long reviewId, LocalDateTime now) {
        var parameters = actorParameters(actor).addValue("merchant", item.merchant()).addValue("itemKey", item.itemKey())
                .addValue("sourceRef", Long.toString(item.id())).addValue("reviewId", reviewId).addValue("now", now);
        if (existing == null) {
            jdbc.update("""
                    INSERT INTO merchant_payment_authorization (
                        merchant_id, item_key, authorization_status, version, source, source_ref, approved_app_code,
                        approved_account_id, approved_user_id, effective_time, last_review_record_id, gmt_create, gmt_modified)
                    VALUES (:merchant, :itemKey, 'ACTIVE', 1, 'APPLICATION', :sourceRef, :app,
                        :account, :user, :now, :reviewId, :now, :now)
                    """, parameters);
            return;
        }
        parameters.addValue("version", existing.version());
        requireChanged(jdbc.update("""
                UPDATE merchant_payment_authorization SET authorization_status = 'ACTIVE', version = version + 1,
                    source = 'APPLICATION', source_ref = :sourceRef, approved_app_code = :app,
                    approved_account_id = :account, approved_user_id = :user, effective_time = :now,
                    expire_time = NULL, suspended_time = NULL, terminated_time = NULL,
                    last_review_record_id = :reviewId, gmt_modified = :now
                WHERE merchant_id = :merchant AND item_key = :itemKey AND version = :version
                """, parameters));
    }

    public void revise(String merchant, Grant grant, String status, LocalDateTime effective, LocalDateTime expires,
                long reviewId, LocalDateTime now) {
        requireChanged(jdbc.update("""
                UPDATE merchant_payment_authorization SET authorization_status = :status, version = version + 1,
                    effective_time = :effective, expire_time = :expires,
                    suspended_time = CASE WHEN :status <> 'SUSPENDED' THEN NULL ELSE COALESCE(suspended_time, :now) END,
                    terminated_time = CASE WHEN :status = 'TERMINATED' THEN :now ELSE terminated_time END,
                    last_review_record_id = :reviewId, gmt_modified = :now
                WHERE merchant_id = :merchant AND item_key = :itemKey AND version = :version
                """, new MapSqlParameterSource("merchant", merchant).addValue("itemKey", grant.itemKey())
                .addValue("version", grant.version()).addValue("status", status).addValue("effective", effective)
                .addValue("expires", expires).addValue("reviewId", reviewId).addValue("now", now)));
    }

    public Optional<Review> replay(String merchant, String command) {
        return replay("PAYMENT_METHOD", merchant, command);
    }

    public Optional<Review> replay(String scope, String merchant, String command) {
        return jdbc.query("""
                SELECT id, payload_hash, result_json FROM merchant_review_record
                WHERE review_scope = :scope AND merchant_id = :merchant AND command_request_id = :command
                """, Map.of("scope", scope, "merchant", merchant, "command", command), (row, index) ->
                new Review(row.getLong("id"), row.getBytes("payload_hash"), row.getString("result_json")))
                .stream().findFirst();
    }

    public boolean submittedThisRound(Item item, long user) {
        return item.initialUser() == user || item.currentUser() == user || !jdbc.queryForList("""
                SELECT id FROM merchant_review_record
                WHERE review_scope = 'PAYMENT_METHOD' AND merchant_id = :merchant AND application_item_id = :item
                  AND round_no = :round AND operator_user_id = :user AND review_action IN ('SUBMIT', 'RESUBMIT')
                LIMIT 1
                """, Map.of("merchant", item.merchant(), "item", item.id(), "round", item.round(), "user", user),
                Long.class).isEmpty();
    }

    public long review(Audit audit, Actor actor) {
        return review("PAYMENT_METHOD", audit, actor);
    }

    public long review(String scope, Audit audit, Actor actor) {
        return insert("""
                INSERT INTO merchant_review_record (
                    merchant_id, review_scope, request_no, review_action, from_status, to_status, review_comment,
                    operator_id, operator_name, operator_app_code, operator_user_id, gmt_create,
                    target_type, target_key, application_item_id, round_no, command_request_id,
                    payload_hash, result_json, fact_snapshot_json)
                VALUES (:merchant, :scope, :application, :action, :fromStatus, :toStatus, :reason,
                    :account, :operatorName, :app, :user, :now, :targetType, :targetKey, :item, :round,
                    :command, :hash, :result, :snapshot)
                """, actorParameters(actor).addValue("scope", scope).addValue("merchant", audit.merchant())
                .addValue("application", audit.application())
                .addValue("action", audit.action()).addValue("fromStatus", audit.from()).addValue("toStatus", audit.to())
                .addValue("reason", audit.reason()).addValue("now", audit.now()).addValue("targetType", audit.targetType())
                .addValue("targetKey", audit.targetKey()).addValue("item", audit.item()).addValue("round", audit.round())
                .addValue("command", audit.command()).addValue("hash", audit.hash()).addValue("result", audit.result())
                .addValue("snapshot", audit.snapshot()));
    }

    public Optional<LegacyMerchant> legacyMerchant(String merchant) {
        return query("""
                SELECT payment_admission_mode, payment_baseline_batch_ref, gmt_create
                FROM base_merchant_info WHERE merchant_id = :merchant AND deleted = 0
                """, merchant, (row, index) -> new LegacyMerchant(row.getString("payment_admission_mode"),
                row.getString("payment_baseline_batch_ref"), time(row, "gmt_create"))).stream().findFirst();
    }

    public Optional<ReviewedBaseline> pendingBaseline(String merchant, String batch) {
        return jdbc.query("""
                SELECT operator_user_id, fact_snapshot_json
                FROM merchant_review_record WHERE review_scope = 'LEGACY_SCOPE'
                  AND merchant_id = :merchant AND request_no = :batch AND review_action = 'BASELINE_SUBMIT'
                  AND target_type = 'BASELINE_BATCH'
                """, Map.of("merchant", merchant, "batch", batch), (row, index) ->
                new ReviewedBaseline(row.getObject("operator_user_id", Long.class), row.getString("fact_snapshot_json")))
                .stream().findFirst();
    }

    public List<String> candidateEvidence(String merchant, String batch) {
        return jdbc.queryForList("""
                SELECT fact_snapshot_json
                FROM merchant_review_record WHERE review_scope = 'LEGACY_SCOPE'
                  AND merchant_id = :merchant AND request_no = :batch AND review_action = 'BASELINE_CANDIDATE'
                  AND target_type = 'LEGACY_SCOPE' ORDER BY id
                """, Map.of("merchant", merchant, "batch", batch), String.class);
    }

    public List<String> approvedBaselineKeys(String merchant, String batch) {
        return jdbc.queryForList("""
                SELECT target_key FROM merchant_review_record
                WHERE review_scope = 'LEGACY_SCOPE' AND merchant_id = :merchant AND request_no = :batch
                  AND review_action = 'BASELINE_APPROVE' AND target_type = 'LEGACY_SCOPE' AND to_status = 'APPROVED'
                ORDER BY target_key
                """, Map.of("merchant", merchant, "batch", batch), String.class);
    }

    public void bindLegacyBaseline(String merchant, String batch, long revision, LocalDateTime now) {
        requireChanged(jdbc.update("""
                UPDATE base_merchant_info SET payment_admission_mode = 'LEGACY_OBSERVE',
                    payment_baseline_batch_ref = :batch, payment_authorization_revision = payment_authorization_revision + 1,
                    payment_admission_cutover_time = :now
                WHERE merchant_id = :merchant AND payment_authorization_revision = :revision AND deleted = 0
                  AND payment_baseline_batch_ref IS NULL
                  AND (payment_admission_mode IS NULL OR payment_admission_mode = 'LEGACY_OBSERVE')
                """, Map.of("merchant", merchant, "batch", batch, "revision", revision, "now", now)));
    }

    public Set<String> frozenMethods(String merchant) {
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT r.target_key FROM base_merchant_info m
                JOIN merchant_review_record r ON r.merchant_id = m.merchant_id
                  AND r.request_no = m.payment_baseline_batch_ref
                WHERE m.merchant_id = :merchant AND m.deleted = 0 AND m.payment_admission_mode = 'LEGACY_OBSERVE'
                  AND r.review_scope = 'LEGACY_SCOPE' AND r.review_action = 'BASELINE_APPROVE'
                  AND r.target_type = 'LEGACY_SCOPE' AND r.to_status = 'APPROVED'
                """, Map.of("merchant", merchant), String.class));
    }

    public void restrictLegacy(String merchant, String itemKey, String batch, String status, long audit, LocalDateTime now) {
        requireChanged(jdbc.update("""
                INSERT INTO merchant_payment_authorization (
                    merchant_id, item_key, authorization_status, version, source, source_ref, effective_time,
                    suspended_time, terminated_time, last_review_record_id, gmt_create, gmt_modified)
                VALUES (:merchant, :itemKey, :status, 1, 'HISTORICAL_BASELINE', :batch, :now,
                    CASE WHEN :status = 'SUSPENDED' THEN :now ELSE NULL END,
                    CASE WHEN :status = 'TERMINATED' THEN :now ELSE NULL END,
                    :audit, :now, :now)
                """, Map.of("merchant", merchant, "itemKey", itemKey, "batch", batch, "status", status,
                "audit", audit, "now", now)));
    }

    public PageResult<HistoryRow> history(String merchant, String itemKey, Search query) {
        return page("""
                SELECT r.id, r.review_action, r.from_status, r.to_status, r.review_comment, r.round_no, r.gmt_create
                FROM merchant_review_record r
                LEFT JOIN merchant_payment_application_item i ON i.id = r.application_item_id AND i.merchant_id = r.merchant_id
                WHERE r.review_scope = 'PAYMENT_METHOD' AND r.merchant_id = :merchant
                  AND (i.item_key = :itemKey OR (r.target_type = 'AUTHORIZATION' AND r.target_key = :itemKey))
                """, "r.gmt_create DESC, r.id DESC", query,
                new MapSqlParameterSource("merchant", merchant).addValue("itemKey", itemKey),
                MerchantPaymentRepository::historyRow);
    }

    private static HistoryRow historyRow(ResultSet row, int index) throws SQLException {
        return new HistoryRow(row.getString("id"), row.getString("review_action"), row.getString("from_status"),
                row.getString("to_status"), row.getString("review_comment"), row.getObject("round_no", Integer.class),
                time(row, "gmt_create"));
    }

    private <T> PageResult<T> page(String body, String order, Search query, MapSqlParameterSource params, RowMapper<T> mapper) {
        long page = query.safePageNo();
        long size = Math.min(query.safePageSize(), 100);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + body + ") filtered", params, Long.class);
        if (total == null || total == 0) {
            return PageResult.of(0, page, size, List.of());
        }
        params.addValue("limit", size).addValue("offset", (page - 1) * size);
        return PageResult.of(total, page, size,
                jdbc.query(body + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params, mapper));
    }

    private String scopeFilter(MerchantPaymentScope scope, MapSqlParameterSource params) {
        if (scope.allMerchants()) {
            return "";
        }
        params.addValue("scopeMerchants", scope.merchantIds());
        return " AND merchant_id IN (:scopeMerchants)";
    }

    private String exact(String column, String parameter, String value, MapSqlParameterSource params) {
        if (value == null || value.isBlank()) {
            return "";
        }
        params.addValue(parameter, value.trim());
        return " AND " + column + " = :" + parameter;
    }

    private <T> List<T> query(String sql, String merchant, RowMapper<T> mapper) {
        return jdbc.query(sql, Map.of("merchant", merchant), mapper);
    }

    private long insert(String sql, MapSqlParameterSource params) {
        var holder = new GeneratedKeyHolder();
        requireChanged(jdbc.update(sql, params, holder, new String[]{"id"}));
        Number key = holder.getKey();
        if (key == null) {
            throw new IllegalStateException("merchant payment command insert returned no identifier");
        }
        return key.longValue();
    }

    private static MapSqlParameterSource actorParameters(Actor actor) {
        return new MapSqlParameterSource("app", actor.app()).addValue("account", actor.account())
                .addValue("user", actor.user()).addValue("operatorName", actor.name());
    }

    private static LocalDateTime time(ResultSet row, String column) throws SQLException {
        return row.getObject(column, LocalDateTime.class);
    }

    public static void requireChanged(int count) {
        if (count != 1) {
            throw new IllegalStateException("merchant payment row changed under lock");
        }
    }

    public record Actor(String app, long account, long user, String name) {}
    public record DirectoryEntry(String type, String value, String label, String locale, boolean enabled) {}
    public record Batch(String merchant, String application) {}
    public record Item(long id, String application, String merchant, String itemKey, String operation, String status, int round,
                String source, long initialUser, long currentUser, String reason, long expectedGrantVersion, long version,
                LocalDateTime submittedAt) {}
    public record Grant(long id, String itemKey, String status, long version, LocalDateTime effective, LocalDateTime expires,
                 String source) {}
    public record Review(long id, byte[] hash, String result) {}
    public record ItemReview(long itemId, HistoryRow history) {}
    public record LegacyMerchant(String mode, String batch, LocalDateTime createdAt) {}
    public record ReviewedBaseline(Long maker, String fact) {}
    public record Audit(String merchant, String application, String action, String from, String to, String reason,
                 String targetType, String targetKey, Long item, Integer round, String command, byte[] hash,
                 String result, String snapshot, LocalDateTime now) {}
}
