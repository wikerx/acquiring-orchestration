package com.scott.payment.risk.mapper;

import com.scott.payment.risk.domain.RiskListMatch;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风控运行时 Mapper 的真实 MySQL 表结构契约测试，默认关闭。
 */
class RiskRuntimeMapperMySqlLiveTests {

    /** 显式启用真实 MySQL 契约测试的系统属性，默认关闭以避免误连共享数据库。 */
    private static final String ENABLED_PROPERTY = "risk.mysql.live.enabled";

    @Test
    @EnabledIfSystemProperty(named = ENABLED_PROPERTY, matches = "true")
    void shouldMatchAnActiveHierarchicalRegionRuleAgainstTheRealSchema() throws Exception {
        try (SqlSession session = sqlSessionFactory().openSession()) {
            RegionFixture fixture = loadActiveRegionFixture(session);
            RiskListMatch match = session.getMapper(RiskRuntimeMapper.class).selectRegionMatch(
                    "BLACK",
                    "region",
                    "高风险区域黑名单",
                    "region",
                    fixture.merchantId(),
                    fixture.countryAlpha3(),
                    fixture.stateProvinceName(),
                    fixture.cityName());

            assertThat(match).isNotNull();
            assertThat(match.getRuleId()).isPositive();
            assertThat(match.getHitValueMasked()).startsWith(fixture.countryAlpha3());
        }
    }

    private SqlSessionFactory sqlSessionFactory() {
        String url = System.getProperty("risk.mysql.live.url",
                "jdbc:mysql://127.0.0.1:3306/payment_acquiring"
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai");
        String username = System.getProperty("risk.mysql.live.username", "root");
        String password = propertyOrEnvironment(
                "risk.mysql.live.password",
                "RISK_MYSQL_LIVE_PASSWORD",
                "scott123456"
        );
        UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        Environment environment = new Environment("risk-mysql-live", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RiskRuntimeMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /**
     * 按系统属性、环境变量、默认值的顺序读取真实 MySQL 测试配置。
     *
     * <p>密码由执行脚本通过环境变量传入，避免进入 Maven 命令行和进程列表；该方法不记录值。</p>
     *
     * @param propertyName JVM 系统属性名
     * @param environmentName 环境变量名
     * @param fallback 本地开发默认值
     * @return 首个非空配置值
     */
    private String propertyOrEnvironment(String propertyName,
                                         String environmentName,
                                         String fallback) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        return fallback;
    }

    private RegionFixture loadActiveRegionFixture(SqlSession session) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT merchant_scope,
                            merchant_id,
                            region_match_level,
                            country_alpha3,
                            state_province_name,
                            city_name
                     FROM risk_black_region
                     WHERE deleted = 0
                       AND status = 1
                       AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
                       AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
                     ORDER BY id
                     LIMIT 1
                     """)) {
            assertThat(resultSet.next()).as("active risk_black_region fixture").isTrue();
            String merchantScope = resultSet.getString("merchant_scope");
            String level = resultSet.getString("region_match_level");
            return new RegionFixture(
                    "MERCHANT".equalsIgnoreCase(merchantScope)
                            ? resultSet.getString("merchant_id")
                            : "200045",
                    resultSet.getString("country_alpha3"),
                    "COUNTRY".equalsIgnoreCase(level) ? null : resultSet.getString("state_province_name"),
                    "CITY".equalsIgnoreCase(level) ? resultSet.getString("city_name") : null);
        }
    }

    private record RegionFixture(String merchantId,
                                 String countryAlpha3,
                                 String stateProvinceName,
                                 String cityName) {
    }
}
