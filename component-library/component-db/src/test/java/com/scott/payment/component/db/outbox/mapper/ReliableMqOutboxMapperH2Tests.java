package com.scott.payment.component.db.outbox.mapper;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxMapperH2Tests
 * @date : 2026-08-02 23:30
 * @email : scott_x@163.com
 * @description : 使用真实 MyBatis 注解 SQL 验证可靠 MQ Outbox 的抢占、终态和超时恢复 CAS 约束
 * @status : create
 */
class ReliableMqOutboxMapperH2Tests {

    /** 测试用 MyBatis 会话工厂。 */
    private SqlSessionFactory sqlSessionFactory;

    /** 为每个测试建立独立 H2 表结构。 */
    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:outbox_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE sys_mq_outbox (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL UNIQUE,
                    topic VARCHAR(128) NOT NULL,
                    tag VARCHAR(128),
                    producer_service VARCHAR(128) NOT NULL,
                    trace_id VARCHAR(64),
                    payload_json CLOB NOT NULL,
                    event_status VARCHAR(32) NOT NULL,
                    retry_count INT NOT NULL,
                    max_retry_count INT NOT NULL,
                    next_retry_time TIMESTAMP(3),
                    processing_started_time TIMESTAMP(3),
                    sent_time TIMESTAMP(3),
                    failure_reason VARCHAR(512),
                    version INT NOT NULL,
                    create_time TIMESTAMP(3) NOT NULL,
                    update_time TIMESTAMP(3) NOT NULL
                )
                """);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        sqlSessionFactory.getConfiguration().addMapper(ReliableMqOutboxMapper.class);
    }

    /** 未到 next_retry_time 的记录不得被重复触发提前抢占。 */
    @Test
    void shouldRejectClaimBeforeRetryTimeAndAllowClaimWhenDue() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ReliableMqOutboxMapper mapper = session.getMapper(ReliableMqOutboxMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 8, 2, 23, 30);
            mapper.insert(event("event-retry", "RETRY_WAIT", 1, 3, now.plusMinutes(1), now));
            ReliableMqOutboxDO stored = mapper.selectByEventId("event-retry");

            assertThat(mapper.claim(stored.getId(), stored.getVersion(), now)).isZero();
            assertThat(mapper.claim(stored.getId(), stored.getVersion(), now.plusMinutes(1))).isOne();
            assertThat(mapper.claim(stored.getId(), stored.getVersion(), now.plusMinutes(1))).isZero();
        }
    }

    /** SENT 终态不能被失败或超时恢复覆盖。 */
    @Test
    void shouldKeepSentTerminalStateImmutable() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ReliableMqOutboxMapper mapper = session.getMapper(ReliableMqOutboxMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 8, 2, 23, 30);
            mapper.insert(event("event-sent", "INIT", 0, 3, null, now));
            ReliableMqOutboxDO stored = mapper.selectByEventId("event-sent");

            assertThat(mapper.claim(stored.getId(), stored.getVersion(), now)).isOne();
            assertThat(mapper.markSent(stored.getId(), stored.getVersion() + 1, now.plusSeconds(1))).isOne();
            assertThat(mapper.markFailed(stored.getId(), stored.getVersion() + 2, "CLOSED", null,
                    "late failure", now.plusSeconds(2))).isZero();
            assertThat(mapper.recoverStale(now.plusMinutes(1), now.plusMinutes(2))).isZero();
            assertThat(mapper.selectByEventId("event-sent").getEventStatus()).isEqualTo("SENT");
        }
    }

    /** 超时 PROCESSING 按失败次数恢复为 RETRY_WAIT 或 CLOSED。 */
    @Test
    void shouldRecoverStaleProcessingWithoutReopeningClosedRecords() {
        try (SqlSession session = sqlSessionFactory.openSession(true)) {
            ReliableMqOutboxMapper mapper = session.getMapper(ReliableMqOutboxMapper.class);
            LocalDateTime now = LocalDateTime.of(2026, 8, 2, 23, 30);
            ReliableMqOutboxDO retry = event("event-stale-retry", "PROCESSING", 0, 2, null, now);
            retry.setProcessingStartedTime(now.minusMinutes(10));
            mapper.insert(retry);
            ReliableMqOutboxDO close = event("event-stale-close", "PROCESSING", 1, 2, null, now);
            close.setProcessingStartedTime(now.minusMinutes(10));
            mapper.insert(close);
            mapper.insert(event("event-closed", "CLOSED", 2, 2, null, now));

            assertThat(mapper.recoverStale(now.minusMinutes(5), now)).isEqualTo(2);
            assertThat(mapper.selectByEventId("event-stale-retry").getEventStatus()).isEqualTo("RETRY_WAIT");
            assertThat(mapper.selectByEventId("event-stale-close").getEventStatus()).isEqualTo("CLOSED");
            assertThat(mapper.selectByEventId("event-closed").getEventStatus()).isEqualTo("CLOSED");
        }
    }

    /** 构造最小 Outbox 测试记录。 */
    private ReliableMqOutboxDO event(String eventId,
                                     String status,
                                     int retryCount,
                                     int maxRetryCount,
                                     LocalDateTime nextRetryTime,
                                     LocalDateTime now) {
        ReliableMqOutboxDO event = new ReliableMqOutboxDO();
        event.setEventId(eventId);
        event.setTopic("test-topic");
        event.setTag("test-tag");
        event.setProducerService("test-service");
        event.setTraceId("trace-test");
        event.setPayloadJson("{}");
        event.setEventStatus(status);
        event.setRetryCount(retryCount);
        event.setMaxRetryCount(maxRetryCount);
        event.setNextRetryTime(nextRetryTime);
        event.setVersion(0);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }
}
