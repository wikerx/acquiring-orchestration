package com.scott.payment.component.db.sharding;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.data.entity.DataMerchantApiResponseAuditDO;
import com.scott.payment.data.mapper.DataMerchantApiResponseAuditMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 Data Mapper 验证商户 OpenAPI 响应审计的季度路由和首次响应保护。
 */
class DataMerchantApiResponseAuditLogicalMapperPocTest {

    /**
     * 同时承载两个季度物理表的隔离主库连接池。
     */
    private HikariDataSource primary;

    /**
     * 按交易时间路由商户 API 交互日志的 ShardingSphere 数据源。
     */
    private DataSource shardingDataSource;

    /**
     * 绕过逻辑路由核对物理表状态的测试入口。
     */
    private JdbcTemplate direct;

    /**
     * 初始化两个季度的物理表和生产 Mapper 使用的分片规则。
     *
     * @throws Exception ShardingSphere 数据源创建失败时终止用例
     */
    @BeforeEach
    void setUp() throws Exception {
        primary = new HikariDataSource();
        primary.setJdbcUrl("jdbc:h2:mem:data_response_audit_mapper_poc_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        primary.setUsername("sa");
        primary.setPassword("");
        primary.setMaximumPoolSize(2);
        primary.setMinimumIdle(0);
        direct = new JdbcTemplate(primary);
        createPhysicalTables();

        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPrimaryDataSource("master");
        properties.setReplicaDataSources(List.of());
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setLogicTables(List.of("transaction_merchant_api_interaction_log"));
        TransactionShardingDataSourceConfiguration configuration = new TransactionShardingDataSourceConfiguration();
        shardingDataSource = ShardingSphereDataSourceFactory.createDataSource(
                Map.of("master", primary),
                configuration.buildRules(properties, List.of()),
                configuration.buildSystemProperties(properties));
    }

    /**
     * 关闭 ShardingSphere 和底层 H2 数据源。
     *
     * @throws Exception 测试数据源关闭失败时终止清理
     */
    @AfterEach
    void tearDown() throws Exception {
        if (shardingDataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
        if (primary != null && !primary.isClosed()) {
            primary.close();
        }
    }

    /**
     * 验证逻辑 SQL 精确落到目标季度，并且重复或冲突消息不能改写首次响应事实。
     */
    @Test
    void shouldRouteByTransactionTimeAndProtectFirstResponseFact() {
        LocalDateTime q3Time = LocalDateTime.of(2026, 9, 30, 23, 59, 59, 123_000_000);
        LocalDateTime q4Time = LocalDateTime.of(2026, 10, 1, 0, 0, 0, 456_000_000);
        seedInteraction("TX-AUDIT-Q3", "REQ-AUDIT-Q3", q3Time, "202603");
        seedInteraction("TX-AUDIT-Q4", "REQ-AUDIT-Q4", q4Time, "202604");

        DataMerchantApiResponseAuditDO q3Audit = audit(
                "TX-AUDIT-Q3", "REQ-AUDIT-Q3", q3Time, "a".repeat(64));
        try (SqlSession session = sqlSessionFactory().openSession(true)) {
            DataMerchantApiResponseAuditMapper mapper =
                    session.getMapper(DataMerchantApiResponseAuditMapper.class);

            assertThat(mapper.updateFirstResponseAudit(q3Audit)).isEqualTo(1);
            assertThat(mapper.updateFirstResponseAudit(q3Audit)).isZero();
            assertThat(mapper.selectResponseFacts(q3Audit))
                    .singleElement()
                    .satisfies(fact -> {
                        assertThat(fact.getTransactionId()).isEqualTo(q3Audit.getTransactionId());
                        assertThat(fact.getTransactionDateTime()).isEqualTo(q3Time);
                        assertThat(fact.getRequestId()).isEqualTo(q3Audit.getRequestId());
                        assertThat(fact.getResponseCipherDigest()).isEqualTo(q3Audit.getResponseCipherDigest());
                        assertThat(fact.getResponseCipherMasked()).isEqualTo(q3Audit.getResponseCipherMasked());
                    });

            DataMerchantApiResponseAuditDO conflict = audit(
                    "TX-AUDIT-Q3", "REQ-AUDIT-Q3", q3Time, "b".repeat(64));
            assertThat(mapper.updateFirstResponseAudit(conflict)).isZero();
        }

        assertThat(responseDigest("202603", "TX-AUDIT-Q3")).isEqualTo("a".repeat(64));
        assertThat(responseDigest("202604", "TX-AUDIT-Q4")).isNull();
    }

    /**
     * 创建仅加载商户响应审计 Mapper 的 MyBatis 测试会话工厂。
     *
     * @return 绑定分片数据源的会话工厂
     */
    private SqlSessionFactory sqlSessionFactory() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment(
                "data-response-audit-logical-mapper-poc",
                new JdbcTransactionFactory(),
                shardingDataSource));
        configuration.addMapper(DataMerchantApiResponseAuditMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 构造一条满足生产 Mapper 字段约束的响应审计事实。
     *
     * @param transactionId 平台交易号
     * @param requestId 商户请求号
     * @param transactionDateTime 交易分片时间
     * @param digest 响应密文摘要
     * @return 待条件写入的响应审计事实
     */
    private DataMerchantApiResponseAuditDO audit(String transactionId,
                                                 String requestId,
                                                 LocalDateTime transactionDateTime,
                                                 String digest) {
        DataMerchantApiResponseAuditDO result = new DataMerchantApiResponseAuditDO();
        result.setTransactionId(transactionId);
        result.setRequestId(requestId);
        result.setTransactionDateTime(transactionDateTime);
        result.setResponsePlainJsonMasked("{\"transactionId\":\"***\"}");
        result.setResponseCipherDigest(digest);
        result.setResponseCipherMasked("cipher***masked");
        result.setResponseTime(transactionDateTime.plusSeconds(1));
        return result;
    }

    /**
     * 向指定季度物理表写入尚未补齐响应密文摘要的基础交互日志。
     *
     * @param transactionId 平台交易号
     * @param requestId 商户请求号
     * @param transactionDateTime 交易分片时间
     * @param suffix 目标季度物理表后缀
     */
    private void seedInteraction(String transactionId,
                                 String requestId,
                                 LocalDateTime transactionDateTime,
                                 String suffix) {
        direct.update("""
                INSERT INTO transaction_merchant_api_interaction_log_%s
                    (request_id, transaction_id, transaction_date_time)
                VALUES (?, ?, ?)
                """.formatted(suffix), requestId, transactionId, transactionDateTime);
    }

    /**
     * 查询指定季度物理表中的首次响应密文摘要。
     *
     * @param suffix 季度物理表后缀
     * @param transactionId 平台交易号
     * @return 已写入摘要；尚未写入时返回 null
     */
    private String responseDigest(String suffix, String transactionId) {
        return direct.queryForObject("""
                SELECT response_cipher_digest
                FROM transaction_merchant_api_interaction_log_%s
                WHERE transaction_id = ?
                """.formatted(suffix), String.class, transactionId);
    }

    /**
     * 创建两个季度的最小交互日志物理表，字段与生产 Mapper 读写契约一致。
     */
    private void createPhysicalTables() {
        for (String suffix : List.of("202603", "202604")) {
            direct.execute("CREATE TABLE transaction_merchant_api_interaction_log_" + suffix + " ("
                    + "id BIGINT AUTO_INCREMENT NOT NULL, request_id VARCHAR(128), "
                    + "transaction_id VARCHAR(64) NOT NULL, response_plain_json_masked VARCHAR(16000), "
                    + "response_cipher_digest VARCHAR(128), response_cipher_masked VARCHAR(128), "
                    + "response_time TIMESTAMP(3), transaction_date_time TIMESTAMP(3) NOT NULL)");
        }
    }
}
