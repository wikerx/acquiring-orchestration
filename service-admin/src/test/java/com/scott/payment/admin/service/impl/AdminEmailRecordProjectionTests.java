package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailRecordQuery;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheReader;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailRecordProjectionTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证管理端邮件列表只读取展示字段，不读取投递密文。
 * @status : create
 */
class AdminEmailRecordProjectionTests {

    @Test
    void shouldPageRecordsWithoutSelectingDeliveryOnlyColumns() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:admin_email_projection_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createListProjectionTable(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO msg_email_send_record (
                    email_no, app_code, merchant_id, template_code, to_emails, subject,
                    content_snapshot, variables_snapshot, biz_type, biz_no, send_status,
                    retry_count, max_retry_count, create_time, update_time, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, "EMAIL-PROJECTION-1", "ADMIN", "M1001", "LOGIN_NOTICE", "m***@example.com",
                "subject", "masked content", "{}", "LOGIN", "BIZ-1", 2, 0, 3);

        try (SqlSession session = sqlSessionFactory(dataSource).openSession(true)) {
            AdminEmailServiceImpl service = new AdminEmailServiceImpl(
                    mock(EmailAccountMapper.class),
                    mock(EmailTemplateMapper.class),
                    session.getMapper(EmailSendRecordMapper.class),
                    mock(AdminConfigService.class),
                    mock(EmailPayloadCrypto.class),
                    mock(AdminEmailDeliveryService.class),
                    mock(AdminSmtpEmailSender.class),
                    new EmailDeliveryProperties(),
                    mock(EnabledEmailTemplateCacheReader.class),
                    mock(com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator.class));

            EmailRecordQuery query = new EmailRecordQuery();
            query.setEmailNo("PROJECTION");
            PageResult<?> result = service.pageRecords(query);

            assertThat(result.getTotal()).isOne();
            assertThat(result.getRecords()).hasSize(1);
        }
    }

    private SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setPlugins(interceptor);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().setMapUnderscoreToCamelCase(true);
        factory.getConfiguration().addMapper(EmailSendRecordMapper.class);
        return factory;
    }

    /** 列表查询夹具刻意不提供 delivery_content_cipher 和 content_type，锁定最小字段投影。 */
    private void createListProjectionTable(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE msg_email_send_record (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    email_no VARCHAR(64), app_code VARCHAR(32), merchant_id VARCHAR(64),
                    merchant_no VARCHAR(64), merchant_name VARCHAR(128), scene_code VARCHAR(64),
                    template_code VARCHAR(64), template_name VARCHAR(128), locale VARCHAR(32),
                    account_id BIGINT, account_code VARCHAR(64), provider_type VARCHAR(32),
                    from_name VARCHAR(128), from_email VARCHAR(256), reply_to_email VARCHAR(256),
                    to_emails VARCHAR(1000), cc_emails VARCHAR(1000), bcc_emails VARCHAR(1000),
                    subject VARCHAR(500), content_snapshot CLOB, variables_snapshot CLOB,
                    biz_type VARCHAR(64), biz_no VARCHAR(128), send_status INT,
                    retry_count INT, max_retry_count INT, next_retry_time TIMESTAMP(3),
                    send_start_time TIMESTAMP(3), send_end_time TIMESTAMP(3),
                    send_success_time TIMESTAMP(3), cost_ms BIGINT, error_code VARCHAR(64),
                    error_message VARCHAR(2000), operator_id BIGINT, operator_name VARCHAR(128),
                    create_by VARCHAR(128), create_time TIMESTAMP(3), update_by VARCHAR(128),
                    update_time TIMESTAMP(3), deleted BIGINT NOT NULL
                )
                """);
    }
}
