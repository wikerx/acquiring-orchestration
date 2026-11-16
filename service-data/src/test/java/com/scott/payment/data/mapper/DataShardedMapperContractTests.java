package com.scott.payment.data.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * service-data 交易逻辑表 Mapper 分片契约门禁，禁止缺少交易时间或动态拼接物理表。
 */
class DataShardedMapperContractTests {

    /**
     * 所有 Data 侧逻辑表 SQL 必须携带统一分片字段。
     */
    @Test
    void logicalTableSqlShouldCarryTransactionShardingTime() {
        List<Class<?>> mapperTypes = List.of(
                DataMerchantNotificationMapper.class,
                DataMerchantNotificationLogMapper.class,
                DataMerchantNotificationRetryOutboxMapper.class,
                DataCheckoutCardVaultMapper.class,
                DataMerchantApiResponseAuditMapper.class);

        mapperTypes.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .forEach(this::assertShardingContract);
    }

    /**
     * 商户响应审计只能补齐空摘要，禁止重复消息改写首次响应事实。
     *
     * @throws NoSuchMethodException Mapper 方法签名发生非兼容变化时测试失败
     */
    @Test
    void merchantResponseAuditShouldOnlyWriteFirstResponse() throws NoSuchMethodException {
        Method method = DataMerchantApiResponseAuditMapper.class.getMethod(
                "updateFirstResponseAudit",
                com.scott.payment.data.entity.DataMerchantApiResponseAuditDO.class);
        String sql = annotationValue(method.getAnnotation(Update.class));

        assertThat(sql)
                .contains("response_cipher_digest IS NULL")
                .doesNotContain("OR response_cipher_digest");
    }

    /**
     * 校验单个 Mapper 方法上的静态 SQL。
     */
    private void assertShardingContract(Method method) {
        assertLogicalSql(method, annotationValue(method.getAnnotation(Select.class)), "FROM transaction_");
        assertLogicalSql(method, annotationValue(method.getAnnotation(Insert.class)), "INSERT INTO transaction_");
        assertLogicalSql(method, annotationValue(method.getAnnotation(Update.class)), "UPDATE transaction_");
        assertLogicalSql(method, annotationValue(method.getAnnotation(Delete.class)), "DELETE FROM transaction_");
    }

    /**
     * 逻辑表 SQL 必须显式携带分片列，且禁止通过字符串插值访问物理表。
     */
    private void assertLogicalSql(Method method, String sql, String operation) {
        if (!sql.contains(operation)) {
            return;
        }
        assertThat(sql)
                .as("%s.%s sharding SQL", method.getDeclaringClass().getSimpleName(), method.getName())
                .contains("transaction_date_time")
                .doesNotContain("${");
    }

    /**
     * 提取 MyBatis 注解 SQL；方法没有对应注解时返回空串。
     */
    private String annotationValue(Select annotation) {
        return annotation == null ? "" : String.join("\n", annotation.value());
    }

    /**
     * 提取 MyBatis 注解 SQL；方法没有对应注解时返回空串。
     */
    private String annotationValue(Insert annotation) {
        return annotation == null ? "" : String.join("\n", annotation.value());
    }

    /**
     * 提取 MyBatis 注解 SQL；方法没有对应注解时返回空串。
     */
    private String annotationValue(Update annotation) {
        return annotation == null ? "" : String.join("\n", annotation.value());
    }

    /**
     * 提取 MyBatis 注解 SQL；方法没有对应注解时返回空串。
     */
    private String annotationValue(Delete annotation) {
        return annotation == null ? "" : String.join("\n", annotation.value());
    }
}
