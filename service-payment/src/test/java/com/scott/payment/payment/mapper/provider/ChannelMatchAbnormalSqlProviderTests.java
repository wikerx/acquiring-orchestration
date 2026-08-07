package com.scott.payment.payment.mapper.provider;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalSqlProviderTests
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常查询 SQL 契约测试，锁定分片时间、软删除、分页排序和受控筛选条件。
 * @status : create
 */
class ChannelMatchAbnormalSqlProviderTests {

    /** 所有跨分片查询必须携带交易分片时间半开区间。 */
    @Test
    void shouldRouteAllQueriesByTransactionDateTimeRange() {
        assertRouted(ChannelMatchAbnormalSqlProvider.countSql());
        assertRouted(ChannelMatchAbnormalSqlProvider.pageSql());
        assertRouted(ChannelMatchAbnormalSqlProvider.summarySql());
    }

    /** 分页必须使用稳定的时间和物理主键倒序。 */
    @Test
    void shouldUseStablePaginationOrder() {
        assertThat(ChannelMatchAbnormalSqlProvider.pageSql())
                .contains("ORDER BY first_seen_time DESC, id DESC")
                .contains("LIMIT #{offset}, #{limit}")
                .doesNotContain("${");
    }

    /** 所有 Provider SQL 都必须能被 MyBatis 动态 SQL 语言驱动解析。 */
    @Test
    void shouldGenerateParseableMybatisDynamicSql() {
        assertParseable(ChannelMatchAbnormalSqlProvider.countSql());
        assertParseable(ChannelMatchAbnormalSqlProvider.pageSql());
        assertParseable(ChannelMatchAbnormalSqlProvider.summarySql());
    }

    private void assertRouted(String sql) {
        assertThat(sql)
                .contains("FROM transaction_abnormal_event")
                .contains("transaction_date_time >= #{beginTime}")
                .contains("transaction_date_time &lt; #{endTimeExclusive}")
                .contains("deleted = 0")
                .doesNotContain("target_status");
    }

    private void assertParseable(String sql) {
        assertThatCode(() -> new XMLLanguageDriver()
                .createSqlSource(new Configuration(), sql, Object.class))
                .doesNotThrowAnyException();
    }
}
