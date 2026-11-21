package com.scott.payment.merchant.application.merchantpayment;

import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository;
import com.scott.payment.capability.governance.MerchantPaymentRepository.Actor;
import com.scott.payment.capability.governance.MerchantPaymentScope;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MerchantPaymentReplicaRoutingTests {
    @AfterEach
    void clearSession() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void firstReadAfterApprovalUsesPrimaryWhileIndependentReplicaIsBehind() {
        JdbcDataSource master = database("payment_admission_master");
        JdbcDataSource slave = database("payment_admission_slave");
        approve(master);
        var routing = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return DynamicDataSourceContextHolder.peek();
            }
        };
        routing.setTargetDataSources(Map.of("master", master, "slave", slave));
        routing.setDefaultTargetDataSource(master);
        routing.setLenientFallback(false);
        routing.afterPropertiesSet();

        var governance = mock(MerchantPaymentGovernanceService.class);
        when(governance.merchantScope("200046", 7L, 7L)).thenReturn(
                new Scope(MerchantPaymentScope.limited(Set.of("200046")), new Actor("MERCHANT", 7L, 7L, null)));
        var repository = new MerchantPaymentRepository(new NamedParameterJdbcTemplate(routing));
        var factory = new ProxyFactory(new MerchantPaymentReadDataSources(repository, governance));
        factory.addAdvice(new DynamicDataSourceAnnotationInterceptor(true, null));
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(routing),
                new AnnotationTransactionAttributeSource()));
        var reads = new MerchantPaymentReads((MerchantPaymentReadDataSources) factory.getProxy());
        var jdbc = new JdbcTemplate(routing);

        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setMerchantId("200046");
        account.setAccountId(7L);
        account.setUserId(7L);
        InternalAuthContextHolder.set(account);

        assertThat(reads.<String>read("200046", scope -> approvalStatus(jdbc))).isEqualTo("master:APPROVED");
        approve(slave);
        assertThat(reads.<String>read("200046", scope -> approvalStatus(jdbc))).isEqualTo("slave:APPROVED");
        assertThat(DynamicDataSourceContextHolder.peek()).isNull();
    }

    private static String approvalStatus(JdbcTemplate jdbc) {
        return DynamicDataSourceContextHolder.peek() + ":" + jdbc.queryForObject(
                "SELECT status FROM merchant_payment_application_item WHERE merchant_id = '200046'", String.class);
    }

    private static void approve(JdbcDataSource source) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update("UPDATE base_merchant_info SET payment_authorization_revision = 2 WHERE merchant_id = '200046'");
        jdbc.update("INSERT INTO merchant_review_record VALUES (2, '200046', 'PAYMENT_METHOD')");
        jdbc.update("UPDATE merchant_payment_application_item SET status = 'APPROVED' WHERE merchant_id = '200046'");
    }

    private static JdbcDataSource database(String name) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE base_merchant_info (merchant_id VARCHAR(32), deleted INT,
                payment_authorization_revision BIGINT, payment_admission_mode VARCHAR(16),
                payment_baseline_batch_ref VARCHAR(64))
                """);
        jdbc.execute("CREATE TABLE merchant_review_record (id BIGINT, merchant_id VARCHAR(32), review_scope VARCHAR(32))");
        jdbc.execute("CREATE TABLE merchant_payment_application_item (merchant_id VARCHAR(32), status VARCHAR(24))");
        jdbc.update("INSERT INTO base_merchant_info VALUES ('200046', 0, 1, 'ENFORCED', NULL)");
        jdbc.update("INSERT INTO merchant_review_record VALUES (1, '200046', 'PAYMENT_METHOD')");
        jdbc.update("INSERT INTO merchant_payment_application_item VALUES ('200046', 'PENDING_REVIEW')");
        return source;
    }
}
