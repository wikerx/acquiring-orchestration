package com.scott.payment.admin.application.paymentlink;

import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.admin.mapper.PaymentLinkOperatorMapper;
import com.scott.payment.admin.mapper.PaymentLinkOperatorMapper.Operator;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 操作人展示名称不取代审计 ID；匿名身份和商户边界保持不变。 */
class AdminPaymentLinkOperatorProjectionTest {
    private final PaymentLinkOperatorMapper accounts = mock(PaymentLinkOperatorMapper.class);
    private final AdminPaymentLinkOperatorProjection projection = new AdminPaymentLinkOperatorProjection(accounts);

    @BeforeEach void context() {
        var account = new InternalAuthAccount(); account.setAppCode("admin"); account.setMerchantId("m1");
        InternalAuthContextHolder.set(account);
    }
    @AfterEach void clear() { InternalAuthContextHolder.clear(); }

    @Test void namesAreBatchedAndDoNotReplaceStoredIdentitiesOrInventPayerNames() {
        var rows = List.of(new Operator("6", "MERCHANT", "admin", "商户操作员"));
        when(accounts.find(anySet())).thenReturn(rows);
        Event human = event("MERCHANT", "6");
        Event repeated = event("MERCHANT", "6");
        Event missing = event("MERCHANT", "999");
        Event mismatch = event("ADMIN", "6");
        Event payer = event("PAYER", "LINK_HOLDER");
        Event system = event("SYSTEM", null);
        var page = PageResult.of(6, 2, 10, List.of(human, repeated, missing, mismatch, payer, system));
        assertThat(projection.events(page)).isSameAs(page);
        assertThat(human.getActorName()).isEqualTo("商户操作员 (admin)");
        assertThat(repeated.getActorName()).isEqualTo(human.getActorName());
        assertThat(human.getActorId()).isEqualTo("6");
        assertThat(missing.getActorName()).isNull();
        assertThat(mismatch.getActorName()).isNull();
        assertThat(payer.getActorName()).isNull();
        assertThat(payer.getActorId()).isEqualTo("LINK_HOLDER");
        assertThat(system.getActorName()).isNull();
        assertThat(page.getPageNo()).isEqualTo(2);
        verify(accounts).find(Set.of(6L, 999L));
    }

    @Test void nonAccountEventsAndEmptyPagesDoNotQueryTheAccountDirectory() {
        projection.events(PageResult.of(2,1,10,List.of(event("PAYER","LINK_HOLDER"),event("PAYMENT",null))));
        projection.events(PageResult.of(0,1,10,List.of()));
        projection.events(PageResult.of(1,1,10,List.of(event("MERCHANT","invalid"))));
        verifyNoInteractions(accounts);
    }

    @Test void detailAndEmailDeliveryUseTheSameAccountProjection() {
        var rows = List.of(new Operator("6", "MERCHANT", "admin", null));
        when(accounts.find(anySet())).thenReturn(rows);
        var view = new View(); view.setCreatedAppCode("MERCHANT"); view.setCreatedBy("6");
        assertThat(projection.view(view).getCreatedByName()).isEqualTo("admin");
        var delivery = new Delivery(); delivery.setCreatedAppCode("MERCHANT"); delivery.setCreatedBy("6");
        projection.deliveries(PageResult.of(1,1,10,List.of(delivery)));
        assertThat(delivery.getCreatedByName()).isEqualTo("admin");
        assertThat(delivery.getCreatedBy()).isEqualTo("6");
    }

    @Test void realMapperOnlyReadsThePermittedApplicationAndMerchantNames() throws Exception {
        var dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:operatoradmin;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE sys_app(id BIGINT, app_code VARCHAR(20), deleted INT)");
            sql.execute("CREATE TABLE sys_account(id BIGINT, app_id BIGINT, user_id BIGINT, merchant_id VARCHAR(30), login_account VARCHAR(40), deleted INT)");
            sql.execute("CREATE TABLE sys_user(id BIGINT, real_name VARCHAR(40), deleted INT)");
            sql.execute("CREATE TABLE sys_merchant_user(account_id BIGINT, merchant_id VARCHAR(30), real_name VARCHAR(40), deleted INT)");
            sql.execute("INSERT INTO sys_app VALUES(1,'ADMIN',0),(2,'MERCHANT',0),(3,'OTHER',0)");
            sql.execute("INSERT INTO sys_account VALUES(6,2,60,'m1','admin',0),(7,2,70,'m2','other-merchant',0),(8,1,80,NULL,'platform',0),(9,2,90,'m1','deleted',9),(10,3,100,'m1','other-app',0),(11,2,110,'m1','fallback',0)");
            sql.execute("INSERT INTO sys_user VALUES(60,'全局姓名',0),(110,'基础姓名',0)");
            sql.execute("INSERT INTO sys_merchant_user VALUES(6,'m1','商户姓名',0),(6,'wrong-merchant','错误姓名',0),(11,'m1',' ',0)");
        }
        var configuration = new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentLinkOperatorMapper.class);
        try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            var mapper = session.getMapper(PaymentLinkOperatorMapper.class);
            var result = mapper.find(Set.of(6L,7L,8L,9L,10L,11L));
            assertThat(result).extracting(Operator::accountId).containsExactlyInAnyOrder("6", "7", "8", "11");
            assertThat(result.stream().filter(r -> r.accountId().equals("6")).findFirst().orElseThrow().realName()).isEqualTo("商户姓名");
            assertThat(result.stream().filter(r -> r.accountId().equals("11")).findFirst().orElseThrow().realName()).isEqualTo("基础姓名");
        }
    }

    private Event event(String app, String id) {
        var event = new Event(); event.setActorAppCode(app); event.setActorId(id); return event;
    }
}
