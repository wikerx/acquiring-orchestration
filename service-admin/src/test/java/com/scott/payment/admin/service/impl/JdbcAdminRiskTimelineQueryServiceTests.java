package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminRiskTimelineQueryServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理端交易风控时间轴查询测试。
 * @status : create
 */
class JdbcAdminRiskTimelineQueryServiceTests {

    /**
     * 风控审计时间线会异步追加记录，查询入口不得继续缓存可能陈旧的列表。
     */
    @Test
    void shouldAlwaysReadRiskTimelineFromDatabase() throws NoSuchMethodException {
        Cacheable cacheable = JdbcAdminRiskTimelineQueryService.class
                .getMethod("findRiskEvents", String.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable).isNull();
    }

    @Test
    void shouldMapEveryRiskDetailToTimelineEvent() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        Map<String, Object> blocked = detailRow(1L, "BLACK_WHITE", "卡号黑名单",
                "HIT", "BLOCK", "CardNo：411111******1234 触发卡号黑名单，拦截", 40);
        Map<String, Object> passed = detailRow(2L, "AML", "邮箱AML",
                "MISS", "ALLOW", "Email：b***@example.test 不在邮箱AML中，放行", 30);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(passed, blocked));
        JdbcAdminRiskTimelineQueryService service = new JdbcAdminRiskTimelineQueryService(jdbcTemplate);

        List<Map<String, Object>> events = service.findRiskEvents("202607281055370000179");

        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .containsEntry("eventType", "RISK_RULE_EVALUATED")
                .containsEntry("eventStage", "RISK")
                .containsEntry("eventStatus", "SUCCESS")
                .containsEntry("matchResult", "MISS")
                .containsEntry("timelineSequence", 230)
                .containsEntry("finalDecision", "REJECT");
        assertThat(events.get(1))
                .containsEntry("eventName", "卡号黑名单")
                .containsEntry("eventStatus", "FAILED")
                .containsEntry("timelineSequence", 240)
                .containsEntry("decisionEffect", "BLOCK");
        verify(jdbcTemplate).queryForList(anyString(), any(MapSqlParameterSource.class));
    }

    private Map<String, Object> detailRow(Long id,
                                          String stageCode,
                                          String functionName,
                                          String matchResult,
                                          String decisionEffect,
                                          String decisionReason,
                                          int stageOrder) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("risk_event_id", id);
        row.put("risk_record_no", "RK202607280001");
        row.put("payment_order_no", "202607281055370000179");
        row.put("final_decision", "REJECT");
        row.put("final_reason", "Risk blocked");
        row.put("module_type", "BLACK");
        row.put("function_code", "cardNo");
        row.put("function_name", functionName);
        row.put("rule_id", 1001L);
        row.put("hit_element", "cardNo");
        row.put("hit_value_masked", "411111******1234");
        row.put("decision_result", "REJECT");
        row.put("decision_reason", decisionReason);
        row.put("decision_time", LocalDateTime.of(2026, 7, 28, 21, 0));
        row.put("stage_code", stageCode);
        row.put("stage_name", stageCode);
        row.put("stage_order", stageOrder);
        row.put("match_result", matchResult);
        row.put("decision_effect", decisionEffect);
        return row;
    }
}
