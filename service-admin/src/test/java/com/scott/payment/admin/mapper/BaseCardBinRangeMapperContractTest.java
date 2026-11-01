package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 旧卡 BIN 集合式导入 SQL 契约测试。
 */
class BaseCardBinRangeMapperContractTest {

    /**
     * 验证旧表导入由单条集合式 SQL 完成，并保留幂等查重条件。
     *
     * @throws Exception 反射读取 Mapper 方法失败时抛出
     */
    @Test
    void shouldUseSetBasedIdempotentLegacyImport() throws Exception {
        Method method = BaseCardBinRangeMapper.class.getMethod(
                "insertValidLegacyRows", String.class, String.class);
        Insert insert = method.getAnnotation(Insert.class);

        assertThat(insert).isNotNull();
        String sql = String.join(" ", insert.value()).toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("insert ignore into base_card_bin_range")
                .contains("select")
                .contains("from card_bin_type_info")
                .contains("not exists")
                .contains("legacy_pk_id");
    }
}
