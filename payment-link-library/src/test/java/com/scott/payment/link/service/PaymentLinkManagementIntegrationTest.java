package com.scott.payment.link.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.link.config.PaymentLinkSettings;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.link.mapper.*;
import com.scott.payment.link.model.PaymentLinkManagementModels.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Validation;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis、唯一键、Spring 事务和行锁；隔离 H2 中不存在 Payment 进程、客户端或渠道。 */
class PaymentLinkManagementIntegrationTest {
    @Configuration @EnableTransactionManagement static class Transactions { }
    HikariDataSource source;
    JdbcTemplate db;
    AnnotationConfigApplicationContext context;
    PaymentLinkManagementService service;
    PaymentLinkManagementPolicy policy;
    PaymentLinkManagementMapper mapper;
    PaymentLinkSettings settings;
    DataSourceTransactionManager transactions;
    final Actor admin = actor("ADMIN", "42", "m1");
    final Actor merchant = actor("MERCHANT", "7", "m1");
    @BeforeEach void setup() throws Exception {
        source = new HikariDataSource(); source.setJdbcUrl("jdbc:h2:mem:link_commands_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        source.setUsername("sa"); source.setPassword(""); source.setMaximumPoolSize(6); db = new JdbcTemplate(source);
        String canonical = Files.readString(Path.of("../docs/sql/payment_acquiring_表结构.sql"));
        for (var table : List.of("payment_checkout_session", "transaction_idempotency", "channel_info", "channel_mid_config", "merchant_channel_mid_binding", "channel_payment_capability", "channel_capability_currency", "channel_capability_card_brand")) ddl(canonical, table);
        for (var table : List.of("payment_link", "payment_link_access", "payment_link_event", "payment_link_collection", "payment_link_checkout_binding")) ddl(canonical, table);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("isolated", new SpringManagedTransactionFactory(), source));
        for (var type : List.of(PaymentLinkManagementMapper.class, PaymentLinkManagementAccessMapper.class, PaymentLinkManagementEventMapper.class, PaymentLinkCapabilityMapper.class)) config.addMapper(type);
        var template = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        mapper = template.getMapper(PaymentLinkManagementMapper.class);
        settings = new PaymentLinkSettings(); settings.setEnabled(true); settings.setStorageReady(true);
        settings.setFrontendBaseUrl("https://pay.example.invalid"); settings.setActiveKeyVersion("v1"); settings.setSigningKeys(Map.of("v1", "isolated-test-link-signing-material-0001")); settings.validate();
        var iso = mock(IsoDictionaryService.class);
        var usd = new IsoCurrencyInfo("USD", "840", "Dollar", "美元", 2, 100, new BigDecimal("0.01"), "$");
        when(iso.getCurrency("USD")).thenReturn(Optional.of(usd)); when(iso.isCurrencyFractionValid(any(), eq("USD"))).thenReturn(true); when(iso.listCurrencies()).thenReturn(List.of(usd));
        var merchants = mock(MerchantRuntimeProfileCacheService.class); var profile = new MerchantRuntimeProfile();
        profile.setMerchantStatus(1); profile.setActivationStatus("ACTIVE"); profile.setMerchantName("Merchant"); profile.setSettlementCurrency("USD"); when(merchants.findRuntimeProfile("m1")).thenReturn(profile);
        var capabilities = new PaymentLinkDatabaseCapabilities(template.getMapper(PaymentLinkCapabilityMapper.class));
        policy = new PaymentLinkManagementPolicy(settings, merchants, iso, capabilities, Validation.buildDefaultValidatorFactory().getValidator(), new MockEnvironment());
        transactions = new DataSourceTransactionManager(source);
        context = new AnnotationConfigApplicationContext(); context.register(Transactions.class);
        context.registerBean("paymentLinkManagementPolicy", PaymentLinkManagementPolicy.class, () -> policy);
        context.registerBean(PaymentLinkSettings.class, () -> settings);
        context.registerBean(PaymentLinkTokenService.class, () -> new PaymentLinkTokenService(settings));
        context.registerBean(PaymentLinkManagementMapper.class, () -> mapper);
        context.registerBean(PaymentLinkManagementAccessMapper.class, () -> template.getMapper(PaymentLinkManagementAccessMapper.class));
        context.registerBean(PaymentLinkManagementEventMapper.class, () -> template.getMapper(PaymentLinkManagementEventMapper.class));
        var ids = new AtomicLong(1000000000000000000L); context.registerBean(GlobalIdGenerator.class, () -> () -> Long.toString(ids.incrementAndGet()));
        context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> transactions);
        context.register(PaymentLinkManagementService.class); context.refresh(); service = context.getBean(PaymentLinkManagementService.class);
        seedCapabilities();
    }
    @AfterEach void close() { if (context != null) context.close(); if (source != null) source.close(); }
    @Test void createOptionsShareAndDisableWorkLocallyAndKeepOnlyLinkFacts() {
        var choices = policy.options(admin, "m1", Options::new);
        assertThat(choices.getCardBrandsByCurrency()).containsEntry("USD", List.of("VISA"));
        assertThat(policy.allowedMethods("m1", "USD", List.of("VISA"))).singleElement()
                .satisfies(method -> assertThat(method.getThreeDsMode()).isEqualTo("AUTO"));
        String link = service.create(admin, "m1", input("create", "ORDER"));
        assertThat(service.create(merchant, "m1", input("create", "ORDER"))).isEqualTo(link);
        String share = service.share(merchant, link); assertThat(share).startsWith("https://pay.example.invalid/l/"+link+".");
        assertThat(db.queryForObject("SELECT token_hash FROM payment_link_access WHERE link_id=?", String.class, link)).isEqualTo(PaymentLinkTokenService.hash(share.substring(share.lastIndexOf('/')+1)));
        assertThat(service.disable(merchant, link, 0, "Requested", "stop")).isEqualTo(link);
        service.disable(merchant, link, 0, "Requested", "stop");
        assertThat(db.queryForObject("SELECT version FROM payment_link WHERE link_id=?", Integer.class, link)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_link_event WHERE event_type='DISABLED'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.share(admin, link)).isInstanceOf(ServiceException.class);
        for (var table : List.of("transaction_idempotency", "payment_checkout_session", "payment_link_collection", "payment_link_checkout_binding")) assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void permissionBrandAndCurrencyScopesAreCheckedWithoutPayment() {
        assertThatThrownBy(() -> service.create(actor("MERCHANT", "other", "m2"), "m1", input("x", "X"))).isInstanceOf(ServiceException.class);
        var input = input("x", "X"); input.setCardBrands(List.of("MASTERCARD"));
        assertThatThrownBy(() -> service.create(admin, "m1", input)).isInstanceOf(ServiceException.class);
        db.update("UPDATE channel_mid_config SET currency_scope='EUR'");
        assertThat(policy.options(admin,"m1",Options::new).getCardBrandsByCurrency()).isEmpty();
        assertThatThrownBy(() -> service.create(admin,"m1",input("y","Y"))).isInstanceOf(ServiceException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_link",Integer.class)).isZero();
    }
    @Test void parallelAdminAndMerchantRetryProduceOneLinkCredentialAndCreatedEvent() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return service.create(admin,"m1",input("same","SAME")); });
            var second = pool.submit(() -> { start.await(); return service.create(merchant,"m1",input("same","SAME")); });
            start.countDown(); assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
            for (var table : List.of("payment_link", "payment_link_access", "payment_link_event")) assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void orderAndChangedRequestCannotCreateSecondEntry() {
        service.create(admin,"m1",input("one","ORDER"));
        assertThatThrownBy(() -> service.create(merchant,"m1",input("two","ORDER"))).isInstanceOf(ServiceException.class);
        var changed = input("one","ORDER"); changed.setExpireMinutes(1440);
        assertThatThrownBy(() -> service.create(admin,"m1",changed)).isInstanceOf(ServiceException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_link",Integer.class)).isEqualTo(1);
    }
    @Test void linkRowLockSerializesDisableAgainstExistingPayerSubmission() throws Exception {
        var link = service.create(admin,"m1",input("one","ORDER"));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1); var started = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var payer = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                // PaymentLinkCheckoutGuard 的同一行锁契约；此测试不执行真实付款。
                db.queryForMap("SELECT * FROM payment_link WHERE link_id=? FOR UPDATE",link);
                locked.countDown(); try { if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("release timed out"); } catch(InterruptedException ex) { throw new RuntimeException(ex); }
                return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var disable = pool.submit(() -> { started.countDown(); return service.disable(admin,link,0,"Stop","stop"); });
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> disable.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); payer.get(5,TimeUnit.SECONDS); assertThat(disable.get(5,TimeUnit.SECONDS)).isEqualTo(link);
            assertThat(db.queryForObject("SELECT status FROM payment_link WHERE link_id=?",String.class,link)).isEqualTo("DISABLED");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void tokenFailureRollsBackLinkAndAuditTogether() {
        settings.setActiveKeyVersion("missing");
        assertThatThrownBy(() -> service.create(admin,"m1",input("bad","BAD"))).isInstanceOf(ServiceException.class);
        for (var table : List.of("payment_link", "payment_link_access", "payment_link_event")) assertThat(db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void successfulExpiredStaleAndCrossMerchantDisableAreRejected() {
        var link = service.create(admin,"m1",input("one","ORDER"));
        assertThatThrownBy(() -> service.disable(actor("MERCHANT","other","m2"),link,0,"No","x")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.disable(admin,link,9,"No","x")).isInstanceOf(ServiceException.class);
        db.update("UPDATE payment_link SET expire_time=? WHERE link_id=?",LocalDateTime.now().minusHours(1),link);
        assertThatThrownBy(() -> service.disable(admin,link,0,"No","x")).isInstanceOf(ServiceException.class);
        assertThat(db.queryForObject("SELECT status FROM payment_link WHERE link_id=?",String.class,link)).isEqualTo("ACTIVE");
    }
    @Test void credentialRetainsOriginalKeyVersionAfterActiveRotation() {
        var link = service.create(admin,"m1",input("one","ORDER")); String original = service.share(admin,link);
        settings.setSigningKeys(Map.of("v1","isolated-test-link-signing-material-0001","v2","isolated-test-link-signing-material-0002")); settings.setActiveKeyVersion("v2");
        assertThat(service.share(merchant,link)).isEqualTo(original);
    }
    @Test void existingCoreOrderAndCheckoutSessionStillPreventAnotherEntry() {
        db.update("INSERT INTO transaction_idempotency(idempotency_scope,idempotency_key,transaction_status,transaction_date_time,transaction_utc_time) VALUES('MERCHANT_ORDER_FLOW','M1:LOWERCASE','SUCCESS',?,?)",LocalDateTime.now(),LocalDateTime.now());
        assertThatThrownBy(() -> service.create(admin,"m1",input("core","lowercase"))).isInstanceOf(ServiceException.class);
        db.update("UPDATE transaction_idempotency SET deleted=1");
        service.create(admin,"m1",input("after-soft-delete","lowercase"));
        db.update("INSERT INTO payment_checkout_session(checkout_session_id,merchant_id,merchant_order_no,merchant_request_id,request_fingerprint,checkout_status,process_stage,last_status_time,label_currency,label_amount,currency_exponent,allowed_payment_methods_json,checkout_domain,expire_time) VALUES('existing','m1','SESSION','existing','fp','SUCCESS','RESULT_RENDERED',?,'USD',10.00,2,'[]','https://example.invalid',?)",LocalDateTime.now(),LocalDateTime.now().plusDays(1));
        assertThatThrownBy(() -> service.create(admin,"m1",input("session","SESSION"))).isInstanceOf(ServiceException.class);
    }
    @Test void successCannotBeDisabledOrSharedAndExpiredStateRemainsDerived() {
        var link=service.create(admin,"m1",input("paid","PAID"));
        db.update("INSERT INTO payment_checkout_session(checkout_session_id,merchant_id,merchant_order_no,merchant_request_id,request_fingerprint,checkout_status,process_stage,last_status_time,label_currency,label_amount,currency_exponent,allowed_payment_methods_json,checkout_domain,expire_time) VALUES('paid-session','m1','PAID','paid','fp','SUCCESS','RESULT_RENDERED',?,'USD',10.00,2,'[]','https://example.invalid',?)",LocalDateTime.now(),LocalDateTime.now().plusDays(1));
        db.update("INSERT INTO payment_link_collection(collection_id,link_id,merchant_id,merchant_order_no,submission_deadline,create_time) VALUES('collection',?,'m1','PAID',?,?)",link,LocalDateTime.now().plusDays(1),LocalDateTime.now());
        db.update("INSERT INTO payment_link_checkout_binding(link_id,collection_id,checkout_session_id,create_time) VALUES(?,'collection','paid-session',?)",link,LocalDateTime.now());
        assertThatThrownBy(() -> service.disable(admin,link,0,"Stop","stop")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.share(merchant,link)).isInstanceOf(ServiceException.class);
        assertThat(db.queryForObject("SELECT status FROM payment_link WHERE link_id=?",String.class,link)).isEqualTo("ACTIVE");
    }

    private static Actor actor(String app,String id,String merchant) { var a=new Actor();a.setAppCode(app);a.setActorId(id);a.setMerchantIds(Set.of(merchant));return a; }
    private Create input(String request,String order) { var c=new Create();c.setRequestId(request);c.setMerchantOrderNo(order);c.setCurrency("USD");c.setAmount("10.00");c.setCardBrands(List.of("VISA"));return c; }
    private void seedCapabilities() {
        db.update("INSERT INTO channel_info(id,channel_code,channel_cn_name,channel_en_name,channel_status,support_acquiring) VALUES(1,'QA','测试','Test',1,1)");
        db.update("INSERT INTO channel_mid_config(id,channel_id,channel_code,channel_mid,mid_name,business_type,payment_method_scope,card_brand_scope,transaction_type_scope,currency_scope,allowed_country_scope,default_transaction_currency,default_settlement_currency,settlement_cycle,settlement_time_zone,mid_status) VALUES(1,1,'QA','QA-MID','Test','ACQUIRING','BANK_CARD','VISA','PAYMENT','USD','ALL','USD','USD','T1','Asia/Shanghai',1)");
        db.update("INSERT INTO merchant_channel_mid_binding(merchant_id,channel_id,channel_code,mid_config_id,channel_mid,binding_status) VALUES('m1',1,'QA',1,'QA-MID',1)");
        db.update("INSERT INTO channel_payment_capability(id,channel_id,channel_code,business_type,payment_method,transaction_type,default_transaction_currency,capability_status,support_3ds) VALUES(1,1,'QA','ACQUIRING','BANK_CARD','PAYMENT','USD',1,1)");
        db.update("INSERT INTO channel_capability_currency(capability_id,channel_id,channel_code,currency_code,currency_status) VALUES(1,1,'QA','USD',1)");
        db.update("INSERT INTO channel_capability_card_brand(capability_id,channel_id,channel_code,card_brand,brand_status) VALUES(1,1,'QA','VISA',1)");
    }
    private void ddl(String content,String table) {
        var matcher=Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?"+table+"`? \\(.*?\\) ENGINE[^;]+;",Pattern.DOTALL).matcher(content);assertThat(matcher.find()).as(table).isTrue();
        String sql=matcher.group().replaceAll("\\) ENGINE[^;]+;",")").replaceAll("(?i)COLLATE [A-Za-z0-9_]+","");
        sql=sql.replaceAll("(?m)^(\\s*)(UNIQUE )?KEY ([`A-Za-z0-9_]+)","$1$2KEY "+table+"_$3");db.execute(sql.replace("`", "").replace("char charset binary", "varchar"));
    }
}
