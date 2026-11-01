package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.RiskTimelineEventResponse;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 管理端交易风控时间轴 JDBC 查询实现。
 */
@Service
public class JdbcAdminRiskTimelineQueryService implements AdminRiskTimelineQueryService {

    /**
     * 在只读数据源查询风控主记录及脱敏命中明细的命名参数模板。
     */
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建管理端风控时间轴查询服务。
     *
     * @param jdbcTemplate 支持命名参数的 JDBC 查询模板
     */
    public JdbcAdminRiskTimelineQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按支付单号查询风控阶段明细并转换为管理端时间轴事件。
     *
     * <p>查询只返回 {@code hit_value_masked}，不读取卡号、邮箱等敏感原文；
     * 结果按评估时间、阶段顺序、决策时间和主键稳定排序。</p>
     *
     * @param paymentOrderNo 平台支付单号
     * @return 风控时间轴事件；参数为空或无记录时返回空集合
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<RiskTimelineEventResponse> findRiskEvents(String paymentOrderNo) {
        if (!StringUtils.hasText(paymentOrderNo)) {
            return List.of();
        }
        List<RiskTimelineEventResponse> events = jdbcTemplate.query("""
                SELECT d.id AS risk_event_id,
                       r.risk_record_no,
                       r.payment_order_no,
                       r.decision_result AS final_decision,
                       r.decision_reason AS final_reason,
                       d.module_type,
                       d.function_code,
                       d.function_name,
                       d.rule_id,
                       d.hit_element,
                       d.hit_value_masked,
                       d.decision_result,
                       d.reason_code,
                       d.decision_reason,
                       d.decision_time,
                       d.stage_code,
                       d.stage_name,
                       d.stage_order,
                       d.match_result,
                       d.decision_effect
                FROM risk_evaluation_record r
                INNER JOIN risk_evaluation_hit_detail d
                        ON d.risk_record_no = r.risk_record_no
                WHERE r.payment_order_no = :paymentOrderNo
                ORDER BY r.evaluation_time ASC,
                         COALESCE(d.stage_order, 9999) ASC,
                         d.decision_time ASC,
                         d.id ASC
                """, new MapSqlParameterSource("paymentOrderNo", paymentOrderNo.trim()),
                this::mapTimelineEvent);
        return events == null ? List.of() : events;
    }

    /**
     * 将单条 JDBC 风控明细映射为管理端统一时间轴响应。
     *
     * @param resultSet 同时包含风控主记录和命中明细的查询结果
     * @param rowNumber 当前结果行号
     * @return 包含脱敏命中值、阶段顺序和最终决策的时间轴事件
     * @throws SQLException JDBC 字段读取失败
     */
    private RiskTimelineEventResponse mapTimelineEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        Integer stageOrder = nullableInteger(resultSet, "stage_order");
        String decisionEffect = resultSet.getString("decision_effect");
        String decisionResult = resultSet.getString("decision_result");
        String matchResult = resultSet.getString("match_result");
        String reasonCode = resultSet.getString("reason_code");
        RiskTimelineEventResponse event = new RiskTimelineEventResponse();
        event.setRiskEventId(nullableLong(resultSet, "risk_event_id"));
        event.setRiskRecordNo(resultSet.getString("risk_record_no"));
        event.setTransactionId(resultSet.getString("payment_order_no"));
        event.setEventType("RISK_RULE_EVALUATED");
        event.setEventStage("RISK");
        event.setEventName(firstText(resultSet.getString("function_name"),
                resultSet.getString("stage_name"), resultSet.getString("function_code")));
        event.setEventStatus(eventStatus(decisionEffect, decisionResult, matchResult, reasonCode));
        event.setEventContent(firstText(resultSet.getString("decision_reason"),
                resultSet.getString("final_reason")));
        event.setEventTime(resultSet.getObject("decision_time", LocalDateTime.class));
        event.setTimelineSequence((stageOrder == null ? 9999 : stageOrder) + 200);
        event.setStageCode(resultSet.getString("stage_code"));
        event.setStageName(resultSet.getString("stage_name"));
        event.setStageOrder(stageOrder);
        event.setModuleType(resultSet.getString("module_type"));
        event.setFunctionCode(resultSet.getString("function_code"));
        event.setFunctionName(resultSet.getString("function_name"));
        event.setRuleId(nullableLong(resultSet, "rule_id"));
        event.setHitElement(resultSet.getString("hit_element"));
        event.setHitValueMasked(resultSet.getString("hit_value_masked"));
        event.setMatchResult(matchResult);
        event.setDecisionResult(decisionResult);
        event.setDecisionEffect(decisionEffect);
        event.setReasonCode(reasonCode);
        event.setFinalDecision(resultSet.getString("final_decision"));
        event.setFinalReason(resultSet.getString("final_reason"));
        return event;
    }

    /**
     * 将风控执行结果映射为时间轴状态：未启用或未配置为跳过，阻断为失败，复核/挑战为处理中。
     *
     * @param effect 规则节点决策影响
     * @param decision 节点决策结果，在影响为空时兜底
     * @param matchResult 规则匹配结果
     * @param reasonCode 稳定原因码
     * @return 管理端统一状态 SUCCESS、PENDING、FAILED 或 SKIPPED
     */
    private String eventStatus(String effect, String decision, String matchResult, String reasonCode) {
        String normalizedMatch = normalizeStatus(matchResult);
        String normalizedDecision = normalizeStatus(decision);
        String normalizedReason = normalizeStatus(reasonCode);
        if ("SKIPPED".equals(normalizedMatch)
                || "SKIP".equals(normalizedDecision)
                || "SKIPPED".equals(normalizedDecision)
                || "NOT_ENABLED".equals(normalizedReason)
                || "NOT_CONFIGURED".equals(normalizedReason)) {
            return "SKIPPED";
        }
        String normalized = normalizeStatus(StringUtils.hasText(effect) ? effect : decision);
        if ("BLOCK".equals(normalized) || "REJECT".equals(normalized)) {
            return "FAILED";
        }
        if ("REVIEW".equals(normalized) || "CHALLENGE".equals(normalized)
                || "REQUIRE_3DS".equals(normalized)) {
            return "PENDING";
        }
        if ("ALLOW".equals(normalized) || "PASS".equals(normalized) || "APPROVE".equals(normalized)) {
            return "SUCCESS";
        }
        return "PENDING";
    }

    /**
     * 规范化状态。
     */
    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 按参数优先级返回首个非空文本。
     *
     * @param candidates 候选文本，按优先级排列
     * @return 首个非空文本；均为空时返回 {@code null}
     */
    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 读取允许为空的 BIGINT 字段。
     *
     * @param resultSet JDBC 查询结果
     * @param columnName 字段名
     * @return long 值；数据库字段为空时返回 {@code null}
     * @throws SQLException JDBC 字段读取失败
     */
    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    /**
     * 读取允许为空的 INTEGER 字段。
     *
     * @param resultSet JDBC 查询结果
     * @param columnName 字段名
     * @return int 值；数据库字段为空时返回 {@code null}
     * @throws SQLException JDBC 字段读取失败
     */
    private Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
