package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.RiskTimelineEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端交易风控时间轴查询测试。
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
    void shouldMapEveryRiskDetailToTimelineEvent() throws SQLException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet blocked = detailRow(1L, "BLACK_WHITE", "卡号黑名单",
                "HIT", "REJECT", "BLOCK", null,
                "CardNo：411111******1234 触发卡号黑名单，拦截", 40);
        ResultSet passed = detailRow(2L, "AML", "邮箱AML",
                "MISS", "PASS", "ALLOW", null,
                "Email：b***@example.test 不在邮箱AML中，放行", 30);
        ResultSet skipped = detailRow(3L, "BLACK_WHITE", "卡BIN黑名单",
                "SKIPPED", "SKIP", "NONE", "NOT_CONFIGURED",
                "商户未配置卡BIN黑名单，跳过", 20);
        ResultSet unknown = detailRow(4L, "FUTURE_STAGE", "未来扩展规则",
                "HIT", "FUTURE_DECISION", "FUTURE_EFFECT", "FUTURE_REASON",
                "未来版本返回当前系统尚未识别的决策", 50);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<RiskTimelineEventResponse> mapper = invocation.getArgument(2);
                    return List.of(
                            mapper.mapRow(skipped, 0),
                            mapper.mapRow(passed, 1),
                            mapper.mapRow(blocked, 2),
                            mapper.mapRow(unknown, 3));
                });
        JdbcAdminRiskTimelineQueryService service = new JdbcAdminRiskTimelineQueryService(jdbcTemplate);

        List<RiskTimelineEventResponse> events = service.findRiskEvents("202607281055370000179");

        assertThat(events).hasSize(4);
        assertThat(events.get(0).getEventType()).isEqualTo("RISK_RULE_EVALUATED");
        assertThat(events.get(0).getEventStage()).isEqualTo("RISK");
        assertThat(events.get(0).getEventStatus()).isEqualTo("SKIPPED");
        assertThat(events.get(0).getMatchResult()).isEqualTo("SKIPPED");
        assertThat(events.get(0).getDecisionResult()).isEqualTo("SKIP");
        assertThat(events.get(0).getReasonCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(events.get(0).getTimelineSequence()).isEqualTo(220);
        assertThat(events.get(1).getEventStatus()).isEqualTo("SUCCESS");
        assertThat(events.get(1).getMatchResult()).isEqualTo("MISS");
        assertThat(events.get(1).getTimelineSequence()).isEqualTo(230);
        assertThat(events.get(1).getFinalDecision()).isEqualTo("REJECT");
        assertThat(events.get(2).getEventName()).isEqualTo("卡号黑名单");
        assertThat(events.get(2).getEventStatus()).isEqualTo("FAILED");
        assertThat(events.get(2).getTimelineSequence()).isEqualTo(240);
        assertThat(events.get(2).getDecisionEffect()).isEqualTo("BLOCK");
        assertThat(events.get(3).getEventStatus()).isEqualTo("PENDING");
        assertThat(events.get(3).getDecisionEffect()).isEqualTo("FUTURE_EFFECT");
        verify(jdbcTemplate).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    /**
     * 构造一条 JDBC 风控时间轴结果，确保测试覆盖生产 RowMapper 而不是绕过字段读取。
     */
    private ResultSet detailRow(Long id,
                                String stageCode,
                                String functionName,
                                String matchResult,
                                String decisionResult,
                                String decisionEffect,
                                String reasonCode,
                                String decisionReason,
                                int stageOrder) throws SQLException {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("risk_event_id")).thenReturn(id);
        when(row.getLong("rule_id")).thenReturn(1001L);
        when(row.getInt("stage_order")).thenReturn(stageOrder);
        when(row.wasNull()).thenReturn(false);
        when(row.getString("risk_record_no")).thenReturn("RK202607280001");
        when(row.getString("payment_order_no")).thenReturn("202607281055370000179");
        when(row.getString("final_decision")).thenReturn("REJECT");
        when(row.getString("final_reason")).thenReturn("Risk blocked");
        when(row.getString("module_type")).thenReturn("BLACK");
        when(row.getString("function_code")).thenReturn("cardNo");
        when(row.getString("function_name")).thenReturn(functionName);
        when(row.getString("hit_element")).thenReturn("cardNo");
        when(row.getString("hit_value_masked")).thenReturn("411111******1234");
        when(row.getString("decision_result")).thenReturn(decisionResult);
        when(row.getString("reason_code")).thenReturn(reasonCode);
        when(row.getString("decision_reason")).thenReturn(decisionReason);
        when(row.getObject("decision_time", LocalDateTime.class))
                .thenReturn(LocalDateTime.of(2026, 7, 28, 21, 0));
        when(row.getString("stage_code")).thenReturn(stageCode);
        when(row.getString("stage_name")).thenReturn(stageCode);
        when(row.getString("match_result")).thenReturn(matchResult);
        when(row.getString("decision_effect")).thenReturn(decisionEffect);
        return row;
    }
}
