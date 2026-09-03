package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminRiskTimelineQueryService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理端交易风控时间轴 JDBC 查询实现。
 * @status : create
 */
@Service
public class JdbcAdminRiskTimelineQueryService implements AdminRiskTimelineQueryService {

    /** 在只读数据源查询风控主记录及脱敏命中明细的命名参数模板。 */
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
    public List<Map<String, Object>> findRiskEvents(String paymentOrderNo) {
        if (!StringUtils.hasText(paymentOrderNo)) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
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
                """, new MapSqlParameterSource("paymentOrderNo", paymentOrderNo.trim()));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            events.add(toTimelineEvent(row));
        }
        return events;
    }

    /**
     * 将单条 JDBC 风控明细映射为管理端统一时间轴字段。
     *
     * @param row 同时包含风控主记录和命中明细的查询行
     * @return 包含脱敏命中值、阶段顺序和最终决策的时间轴事件
     */
    private Map<String, Object> toTimelineEvent(Map<String, Object> row) {
        Map<String, Object> event = new LinkedHashMap<>();
        Object stageOrder = value(row, "stage_order");
        event.put("riskEventId", value(row, "risk_event_id"));
        event.put("riskRecordNo", value(row, "risk_record_no"));
        event.put("transactionId", value(row, "payment_order_no"));
        event.put("eventType", "RISK_RULE_EVALUATED");
        event.put("eventStage", "RISK");
        event.put("eventName", firstText(row, "function_name", "stage_name", "function_code"));
        event.put("eventStatus", eventStatus(value(row, "decision_effect"), value(row, "decision_result")));
        event.put("eventContent", firstText(row, "decision_reason", "final_reason"));
        event.put("eventTime", value(row, "decision_time"));
        event.put("timelineSequence", intValue(stageOrder, 9999) + 200);
        event.put("stageCode", value(row, "stage_code"));
        event.put("stageName", value(row, "stage_name"));
        event.put("stageOrder", stageOrder);
        event.put("moduleType", value(row, "module_type"));
        event.put("functionCode", value(row, "function_code"));
        event.put("functionName", value(row, "function_name"));
        event.put("ruleId", value(row, "rule_id"));
        event.put("hitElement", value(row, "hit_element"));
        event.put("hitValueMasked", value(row, "hit_value_masked"));
        event.put("matchResult", value(row, "match_result"));
        event.put("decisionResult", value(row, "decision_result"));
        event.put("decisionEffect", value(row, "decision_effect"));
        event.put("finalDecision", value(row, "final_decision"));
        event.put("finalReason", value(row, "final_reason"));
        return event;
    }

    /**
     * 将风控决策影响映射为时间轴状态：阻断为失败，复核/挑战为处理中，其余为成功。
     *
     * @param effect 规则节点决策影响
     * @param decision 节点决策结果，在影响为空时兜底
     * @return 管理端统一状态 SUCCESS、PENDING 或 FAILED
     */
    private String eventStatus(Object effect, Object decision) {
        String normalized = String.valueOf(effect == null ? decision : effect).trim().toUpperCase(Locale.ROOT);
        if ("BLOCK".equals(normalized) || "REJECT".equals(normalized)) {
            return "FAILED";
        }
        if ("REVIEW".equals(normalized) || "CHALLENGE".equals(normalized)
                || "REQUIRE_3DS".equals(normalized)) {
            return "PENDING";
        }
        return "SUCCESS";
    }

    /**
     * 按字段优先级返回首个非空文本。
     *
     * @param row JDBC 查询行
     * @param keys 候选字段名，按优先级排列
     * @return 首个非空字段文本；均为空时返回 {@code null}
     */
    private String firstText(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object candidate = value(row, key);
            if (candidate != null && StringUtils.hasText(String.valueOf(candidate))) {
                return String.valueOf(candidate);
            }
        }
        return null;
    }

    /**
     * 兼容 JDBC 驱动返回的下划线键与驼峰键。
     *
     * @param row JDBC 查询行
     * @param key 下划线格式字段名
     * @return 对应字段值；不存在时返回 {@code null}
     */
    private Object value(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object value = row.get(key);
        if (value != null) {
            return value;
        }
        return row.get(toCamelCase(key));
    }

    /**
     * 将数据库下划线字段名转换为驼峰形式，用于兼容不同列名映射策略。
     *
     * @param value 下划线格式字段名
     * @return 驼峰格式字段名
     */
    private String toCamelCase(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(current));
                upperNext = false;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    /**
     * 将 JDBC 行中的数值字段转换为 int，空值或非法文本使用调用方提供的默认值。
     *
     * @param value    JDBC 原始字段
     * @param fallback 无法转换时的默认值
     * @return 转换后的整数
     */
    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
