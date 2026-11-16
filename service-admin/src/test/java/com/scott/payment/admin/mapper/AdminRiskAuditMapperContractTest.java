package com.scott.payment.admin.mapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 Admin 风控审计 Mapper 生成的可执行 SQL，避免普通注解查询残留 XML 实体。
 */
class AdminRiskAuditMapperContractTest {

    @Test
    void merchantRiskRankingShouldUseNativeHalfOpenTimePredicates() {
        Configuration configuration = new Configuration();
        configuration.addMapper(AdminRiskAuditMapper.class);

        BoundSql boundSql = configuration
                .getMappedStatement(AdminRiskAuditMapper.class.getName()
                        + ".selectMerchantRiskRanking")
                .getBoundSql(Map.of(
                        "evaluationStartTime", LocalDateTime.of(2026, 11, 1, 0, 0),
                        "evaluationEndTimeExclusive", LocalDateTime.of(2026, 12, 1, 0, 0),
                        "limit", 20));

        assertThat(boundSql.getSql())
                .contains("evaluation_time >= ?", "evaluation_time < ?")
                .doesNotContain("&gt;", "&lt;");
    }
}
