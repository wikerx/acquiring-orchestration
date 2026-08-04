package com.scott.payment.merchant.mapper;

import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 MyBatis 注解 SQL 验证 Merchant 邮件投递状态机。 */
class MerchantEmailSendRecordMapperH2Tests {

    /** 隔离 H2 数据源对应的 MyBatis 会话工厂。 */
    private SqlSessionFactory sqlSessionFactory;
    /** 初始化状态机测试数据的 JDBC 工具。 */
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:merchant_email_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE msg_email_send_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, email_no VARCHAR(64) NOT NULL,
                    app_code VARCHAR(32) NOT NULL, account_id BIGINT, send_status INT NOT NULL,
                    retry_count INT NOT NULL, max_retry_count INT NOT NULL, next_retry_time TIMESTAMP(3),
                    send_start_time TIMESTAMP(3), send_end_time TIMESTAMP(3), send_success_time TIMESTAMP(3),
                    cost_ms BIGINT, error_code VARCHAR(64), error_message VARCHAR(2000),
                    delivery_content_cipher CLOB, content_type VARCHAR(16), update_time TIMESTAMP(3),
                    deleted BIGINT NOT NULL
                )
                """);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        sqlSessionFactory.getConfiguration().addMapper(MerchantEmailSendRecordMapper.class);
    }

    @Test
    void shouldKeepSuccessTerminalAndClearCipher() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MerchantEmailSendRecordMapper mapper = session.getMapper(MerchantEmailSendRecordMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 8, 2, 22, 50);
            insert("EMAIL-1", 0, 0, 3, null, now);

            assertThat(mapper.claimForDelivery(1L, "EMAIL-1", "MERCHANT", now)).isOne();
            assertThat(mapper.markDeliverySuccess(1L, "EMAIL-1", "MERCHANT", now.plusSeconds(1), 20L)).isOne();
            assertThat(mapper.markDeliveryFailure(1L, "EMAIL-1", "MERCHANT", now.plusSeconds(2),
                    now.plusMinutes(1), 30L, "LATE", "late")).isZero();
            MerchantEmailSendRecordDO stored = mapper.selectByDeliveryKey(1L, "EMAIL-1", "MERCHANT");
            assertThat(stored.getSendStatus()).isEqualTo(2);
            assertThat(stored.getDeliveryContentCipher()).isNull();
        }
    }

    @Test
    void shouldRetryOnlyWhenDueAndRecoverOnlyStaleSending() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            MerchantEmailSendRecordMapper mapper = session.getMapper(MerchantEmailSendRecordMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 8, 2, 22, 50);
            insert("EMAIL-DUE", 4, 1, 3, now.minusSeconds(1), now);
            insert("EMAIL-FUTURE", 4, 1, 3, now.plusMinutes(1), now);
            insert("EMAIL-STALE", 1, 0, 3, null, now.minusMinutes(10));
            insert("EMAIL-SUCCESS", 2, 0, 3, null, now.minusMinutes(10));

            assertThat(mapper.selectDueForRetry("MERCHANT", now, 10))
                    .extracting(MerchantEmailSendRecordDO::getEmailNo).containsExactly("EMAIL-DUE");
            assertThat(mapper.requeueForDelivery(1L, "EMAIL-DUE", "MERCHANT", now)).isOne();
            assertThat(mapper.requeueForDelivery(1L, "EMAIL-DUE", "MERCHANT", now)).isZero();
            assertThat(mapper.recoverStaleDelivery("MERCHANT", now.minusMinutes(5), now.plusSeconds(30), now)).isOne();
            assertThat(mapper.selectByDeliveryKey(3L, "EMAIL-STALE", "MERCHANT").getSendStatus()).isEqualTo(4);
            assertThat(mapper.selectByDeliveryKey(4L, "EMAIL-SUCCESS", "MERCHANT").getSendStatus()).isEqualTo(2);
        }
    }

    private void insert(String emailNo,
                        int status,
                        int retryCount,
                        int maxRetryCount,
                        LocalDateTime nextRetryTime,
                        LocalDateTime sendStartTime) {
        jdbcTemplate.update("""
                        INSERT INTO msg_email_send_record (
                            email_no, app_code, account_id, send_status, retry_count, max_retry_count,
                            next_retry_time, send_start_time, delivery_content_cipher, content_type,
                            update_time, deleted
                        ) VALUES (?, 'MERCHANT', 20, ?, ?, ?, ?, ?, 'cipher-content', 'HTML', ?, 0)
                        """,
                emailNo, status, retryCount, maxRetryCount, nextRetryTime, sendStartTime, sendStartTime);
    }
}
