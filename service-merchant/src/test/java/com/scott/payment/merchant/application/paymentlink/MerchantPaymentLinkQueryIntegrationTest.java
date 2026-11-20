package com.scott.payment.merchant.application.paymentlink;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.merchant.client.payment.PaymentLinkInternalClient;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.merchant.mapper.PaymentLinkQueryMapper;
import com.scott.payment.merchant.mapper.PaymentLinkOperatorMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 隔离 H2 运行真实查询 SQL；Payment 客户端一旦被调用即失败，不连接现有业务库。 */
class MerchantPaymentLinkQueryIntegrationTest {
    private static final String LINK = "L" + "1".repeat(22);
    private static final String OTHER = "L" + "2".repeat(22);
    private HikariDataSource source;
    private JdbcTemplate db;
    private SqlSession session;
    private PaymentLinkInternalClient payment;
    private MerchantPaymentLinkApplicationService application;
    private MerchantPaymentLinkQueryService queries;
    private final LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);


    @BeforeEach
    void setUp() throws Exception {
        source = new HikariDataSource();
        source.setJdbcUrl("jdbc:h2:mem:local_link_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        source.setUsername("sa");
        source.setPassword("");
        source.setMaximumPoolSize(3);
        db = new JdbcTemplate(source);
        String canonical = Files.readString(Path.of("../docs/sql/payment_acquiring_表结构.sql"));
        for (String table : List.of("payment_checkout_session", "payment_checkout_attempt", "payment_checkout_event", "transaction_locator", "admin_payment_link_delivery_request", "merchant_payment_link_delivery_request", "msg_email_send_record")) {
            ddl(canonical, table);
        }
        for (String table : List.of("payment_link", "payment_link_collection", "payment_link_checkout_binding", "payment_link_event")) {
            ddl(canonical, table);
        }
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(LocalCacheScope.STATEMENT);
        config.setEnvironment(new Environment("local-read-test", new JdbcTransactionFactory(), source));
        try (var shared = MerchantPaymentLinkQueryIntegrationTest.class.getResourceAsStream("/mapper/PaymentLinkQuerySql.xml")) {
            new XMLMapperBuilder(shared, config, "mapper/PaymentLinkQuerySql.xml", config.getSqlFragments()).parse();
        }
        try (var xml = Files.newInputStream(Path.of("src/main/resources/mapper/PaymentLinkQueryMapper.xml"))) {
            new XMLMapperBuilder(xml, config, "mapper/PaymentLinkQueryMapper.xml", config.getSqlFragments()).parse();
        }
        config.addMapper(com.scott.payment.merchant.mapper.PaymentLinkDeliveryMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        IsoDictionaryService currencies = mock(IsoDictionaryService.class);
        when(currencies.listCurrencies()).thenReturn(List.of(currency("USD"), currency("JPY"), currency("USD")));
        queries = new MerchantPaymentLinkQueryService(session.getMapper(PaymentLinkQueryMapper.class), currencies);
        payment = mock(PaymentLinkInternalClient.class, invocation -> {
            throw new AssertionError("Management reads must not call service-payment");
        });
        var accounts = mock(PaymentLinkOperatorMapper.class);
        var operators = new MerchantPaymentLinkOperatorProjection(accounts);
        var account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setAccountId(42L);
        account.setMerchantId("m1");
        InternalAuthContextHolder.set(account);
        var management = mock(com.scott.payment.link.service.PaymentLinkManagementService.class);
        var policy = mock(com.scott.payment.link.service.PaymentLinkManagementPolicy.class);
        application = new MerchantPaymentLinkApplicationService(management, policy, operators, queries);
        insertLink(LINK, "m1", "ORDER-1", "USD", "10.00", 2);
        insertLink(OTHER, "m2", "ORDER-2", "USD", "99.00", 2);
        bind(LINK, "s1", "m1", "ORDER-1", "USD", "10.00", 2);
        insertAttempt("first", "s1", "m1", "ORDER-1", 1, "FAILED", "reserved-only", "USD", "10.00");
        insertAttempt("second", "s1", "m1", "ORDER-1", 2, "SUCCEEDED", "persisted-second", "USD", "10.00");
        db.update("INSERT INTO transaction_locator(transaction_id,operation_id,root_transaction_id,merchant_id,merchant_order_no,transaction_type,transaction_date_time,root_transaction_date_time) VALUES('persisted-second','op2','persisted-second','m1','ORDER-1','PAYMENT',?,?)", now, now);
        db.update("INSERT INTO payment_link_event(event_id,link_id,event_type,actor_app_code,actor_id,create_time) VALUES('created',?,'CREATED','MERCHANT','42',?)", LINK, now);
        db.update("INSERT INTO payment_checkout_event(checkout_event_id,checkout_session_id,merchant_id,event_type,event_result,event_payload_json,event_time) VALUES('submitted','s1','m1','PAYMENT_SUBMITTED','SUCCESS',?,?)", "{\"internalOnly\":\"restricted-fixture\"}", now);
    }

    @AfterEach
    void tearDown() {
        InternalAuthContextHolder.clear();
        if (payment != null) verifyNoInteractions(payment);
        if (session != null) session.close();
        if (source != null) source.close();
    }

    @Test
    void everyManagementReadWorksWithPaymentUnavailableAndKeepsRealFacts() {
        assertThat(application.filterChoices().currencies()).containsExactly("JPY", "USD");
        var page = application.search(new Query());
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getMerchantId()).isEqualTo("m1");
            assertThat(row.getAmount()).isEqualTo("10.00");
            assertThat(row.getStatus()).isEqualTo("COMPLETED");
            assertThat(row.getAttemptCount()).isEqualTo(2);
            assertThat(row.getTransactionId()).isEqualTo("persisted-second");
        });
        var summary = application.summary(new Query());
        assertThat(summary.getTotal()).isEqualTo(1);
        assertThat(summary.getCompleted()).isEqualTo(1);
        assertThat(summary.getFailedAttempts()).isEqualTo(1);
        assertThat(new BigDecimal(summary.getSuccessfulAmounts().get("USD"))).isEqualByComparingTo("10.00");
        var detail = application.detail(LINK);
        assertThat(detail.getCardBrands()).containsExactly("MASTERCARD");
        assertThat(detail.getNotifyUrl()).isEqualTo("https://example.invalid/notify");
        assertThat(application.collections(LINK, new Query()).getRecords()).singleElement()
                .extracting(Collection::getCheckoutSessionId).isEqualTo("s1");
        var attempts = application.attempts(LINK, new Query());
        assertThat(attempts.getTotal()).isEqualTo(2);
        assertThat(attempts.getRecords()).extracting(Attempt::getStatus).containsExactly("SUCCESS", "FAILED");
        assertThat(attempts.getRecords().get(0).getTransactionId()).isEqualTo("persisted-second");
        assertThat(attempts.getRecords().get(1).getTransactionId()).isNull();
        var events = application.events(LINK, new Query());
        assertThat(events.getRecords()).extracting(Event::getEventType)
                .contains("CREATED", "PAYMENT_SUBMITTED", "PAYMENT_SUCCEEDED", "PAYMENT_FAILED");
        assertThat(JsonUtils.toJsonString(events)).doesNotContain("restricted-fixture", "internalOnly");
        assertThat(db.queryForObject("SELECT root_transaction_id FROM payment_checkout_session WHERE checkout_session_id='s1'", String.class)).isNull();
        assertThat(db.queryForObject("SELECT root_transaction_id FROM transaction_locator WHERE transaction_id='persisted-second'", String.class)).isEqualTo("persisted-second");
    }

    @Test
    void crossMerchantFiltersAndAllDetailTabsAreRejected() {
        Query query = new Query(); query.setMerchantId("m2");
        forbidden(() -> application.search(query));
        forbidden(() -> application.summary(query));
        forbidden(() -> application.detail(OTHER));
        forbidden(() -> application.collections(OTHER, new Query()));
        forbidden(() -> application.attempts(OTHER, new Query()));
        forbidden(() -> application.events(OTHER, new Query()));
    }

    @Test
    void lookupNeverExposesAnotherMerchantOrAnotherOrdersReservedIdentifier() {
        db.update("INSERT INTO transaction_locator(transaction_id,operation_id,root_transaction_id,merchant_id,merchant_order_no,transaction_type,transaction_date_time,root_transaction_date_time) VALUES('reserved-only','other-op','reserved-only','m2','ORDER-1','PAYMENT',?,?)", now, now);
        assertThat(application.attempts(LINK, new Query()).getRecords().get(1).getTransactionId()).isNull();
        db.update("UPDATE transaction_locator SET merchant_id='m1',merchant_order_no='OTHER-ORDER' WHERE transaction_id='reserved-only'");
        assertThat(application.attempts(LINK, new Query()).getRecords().get(1).getTransactionId()).isNull();
    }

    @Test
    void queryPaginationAndChannelFilteringUseStoredFacts() {
        Query query = new Query(); query.setChannelCode("OTHER");
        assertThat(application.search(query).getTotal()).isZero();
        query.setChannelCode("QA"); assertThat(application.search(query).getTotal()).isEqualTo(1);
        Query page = new Query(); page.setPageSize(1); page.setPageNo(2);
        var attempts = application.attempts(LINK, page);
        assertThat(attempts.getTotal()).isEqualTo(2);
        assertThat(attempts.getRecords()).singleElement().extracting(Attempt::getCheckoutAttemptId).isEqualTo("first");
        Query invalid = new Query(); invalid.setPageSize(101);
        assertThatThrownBy(() -> application.search(invalid)).isInstanceOf(ServiceException.class);
        Query legacy = new Query(); legacy.setKeyword("unbounded");
        assertThatThrownBy(() -> application.search(legacy)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> application.detail("missing")).isInstanceOfSatisfying(ServiceException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiResultEnum.ORDER_NOT_FOUND.getCode()));
    }

    @Test
    void currencyTotalsAreNotAddedTogetherAndZeroExponentKeepsIntegerDisplay() {
        String yen = "L" + "3".repeat(22);
        insertLink(yen, "m1", "YEN-1", "JPY", "100", 0);
        bind(yen, "s3", "m1", "YEN-1", "JPY", "100", 0);
        var summary = application.summary(new Query());
        assertThat(summary.getTotal()).isEqualTo(2);
        assertThat(summary.getSuccessfulAmounts()).hasSize(2);
        assertThat(new BigDecimal(summary.getSuccessfulAmounts().get("JPY"))).isEqualByComparingTo("100");
        assertThat(application.detail(yen).getAmount()).isEqualTo("100");
    }

    @Test
    void expiryAndDisableAreProjectedWithoutWritingLifecycleState() {
        db.update("UPDATE payment_link SET expire_time=? WHERE link_id=?", now.minusMinutes(1), OTHER);
        // 查询自己的未付款链接，过期仅为读投影，不改写 ACTIVE。
        String expired = "L" + "4".repeat(22);
        insertLink(expired, "m1", "EXPIRED-1", "USD", "2.00", 2);
        db.update("UPDATE payment_link SET expire_time=? WHERE link_id=?", now.minusMinutes(1), expired);
        Query query = new Query(); query.setStatus("EXPIRED");
        assertThat(application.search(query).getRecords()).singleElement().extracting(View::getLinkId).isEqualTo(expired);
        assertThat(db.queryForObject("SELECT status FROM payment_link WHERE link_id=?", String.class, expired)).isEqualTo("ACTIVE");
        db.update("UPDATE payment_link SET status='DISABLED' WHERE link_id=?", expired);
        query.setStatus("DISABLED"); assertThat(application.search(query).getTotal()).isEqualTo(1);
    }

    @Test
    void callerCannotClaimPlatformScopeOrUseAnotherApplication() {
        InternalAuthContextHolder.get().setAppCode("ADMIN");
        assertThatThrownBy(() -> application.search(new Query())).isInstanceOf(ServiceException.class);
    }

    @Test
    void exportSourceUsesTheSameLocalProjectionAndFrozenScope() {
        var request = new com.scott.payment.merchant.dto.paymentlink.PaymentLinkExportDTOs.PageRequest();
        request.setCreatorAccountId(42L);
        request.setMerchantId("m1");
        request.setMerchantIds(Set.of("m1"));
        var exported = new MerchantPaymentLinkExportSourceApplicationService(queries).page(request);
        assertThat(exported.getTotal()).isEqualTo(1);
        assertThat(exported.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getMerchantId()).isEqualTo("m1");
            assertThat(row.getAmount()).isEqualTo("10.00");
        });
        assertThat(JsonUtils.toJsonString(exported)).doesNotContain("notifyUrl", "redirectUrl", "restricted-fixture");
    }

    @Test
    void deliveryHistoryUsesLocalDetailAuthorizationWithoutSendingMail() {
        var mail = new MerchantPaymentLinkMailApplicationService(application,
                session.getMapper(com.scott.payment.merchant.mapper.PaymentLinkDeliveryMapper.class),
                null, null, null, null, null);
        assertThat(mail.page(LINK, new Query()).getTotal()).isZero();
        forbidden(() -> mail.page(OTHER, new Query()));
    }

    private void insertLink(String link, String merchant, String order, String currency, String amount, int exponent) {
        db.update("INSERT INTO payment_link(link_id,merchant_id,merchant_order_no,link_name,currency,amount,currency_exponent,locale,allowed_brands_json,merchant_display_name,status,expire_time,create_request_id,request_fingerprint,created_app_code,created_by,version,create_time,update_time,notify_url) VALUES(?,?,?,'Invoice',?,?,?,'en-US',?,'Merchant','ACTIVE',?,?,'fp','MERCHANT','42',0,?,?,?)",
                link, merchant, order, currency, new BigDecimal(amount), exponent, "[\"MASTERCARD\"]", now.plusDays(1), "request-"+link, now, now, "https://example.invalid/notify");
    }

    private void bind(String link, String sessionId, String merchant, String order, String currency, String amount, int attempts) {
        db.update("INSERT INTO payment_checkout_session(checkout_session_id,merchant_id,merchant_order_no,merchant_request_id,request_fingerprint,checkout_status,process_stage,last_status_time,label_currency,label_amount,currency_exponent,allowed_payment_methods_json,checkout_domain,expire_time,attempt_count,latest_transaction_id) VALUES(?,?,?,?,'fp','SUCCESS','RESULT_RENDERED',?,?,?,?, '[]','https://example.invalid',?,?,?)",
                sessionId, merchant, order, "request-"+sessionId, now, currency, new BigDecimal(amount), "JPY".equals(currency)?0:2, now.plusDays(1), attempts, "s1".equals(sessionId)?"persisted-second":null);
        db.update("INSERT INTO payment_link_collection(collection_id,link_id,merchant_id,merchant_order_no,submission_deadline,create_time) VALUES(?,?,?,?,?,?)", "collection-"+link, link, merchant, order, now.plusDays(1), now);
        db.update("INSERT INTO payment_link_checkout_binding(link_id,collection_id,checkout_session_id,create_time) VALUES(?,?,?,?)", link, "collection-"+link, sessionId, now);
    }

    private void insertAttempt(String attempt, String sessionId, String merchant, String order, int no, String status, String transaction, String currency, String amount) {
        db.update("INSERT INTO payment_checkout_attempt(checkout_attempt_id,checkout_session_id,merchant_id,merchant_order_no,attempt_no,attempt_request_id,request_fingerprint,attempt_status,process_stage,payment_method,payment_brand,label_currency,label_amount,channel_code,transaction_id,transaction_date_time,complete_time,result_snapshot) VALUES(?,?,?,?,?,?,'fp',?,'RESULT_RENDERED','BANK_CARD','MASTERCARD',?,?,'QA',?,?,?,?)",
                attempt, sessionId, merchant, order, no, "request-"+attempt, status, currency, new BigDecimal(amount), transaction, now, now, "{\"internalOnly\":\"restricted-fixture\"}");
    }

    @Test void terminalTimelineTimeDoesNotDriftWhenAttemptMetadataChanges() {
        LocalDateTime completed = now.minusMinutes(20);
        db.update("UPDATE payment_checkout_attempt SET complete_time = ?, update_time = ? WHERE checkout_attempt_id = 'second'", completed, now);
        var events = application.events(LINK, new Query()).getRecords();
        assertThat(events).filteredOn(event -> "PAYMENT_SUCCEEDED".equals(event.getEventType()))
                .singleElement().satisfies(event -> assertThat(event.getCreateTime()).isEqualTo(completed));
    }

    private void ddl(String content, String table) {
        var matcher = Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?" + table + "`? \\(.*?\\) ENGINE[^;]+;", Pattern.DOTALL).matcher(content);
        assertThat(matcher.find()).as("DDL for %s", table).isTrue();
        String sql = matcher.group().replaceAll("\\) ENGINE[^;]+;", ")").replaceAll("(?i)COLLATE [A-Za-z0-9_]+", "");
        sql = sql.replaceAll("(?m)^(\\s*)(UNIQUE )?KEY ([`A-Za-z0-9_]+)", "$1$2KEY " + table + "_$3");
        db.execute(sql.replace("`", ""));
    }

    private IsoCurrencyInfo currency(String code) {
        return new IsoCurrencyInfo(code, "", code, code, 2, 100, new BigDecimal("0.01"), code);
    }

    private void forbidden(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ServiceException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
    }
}
