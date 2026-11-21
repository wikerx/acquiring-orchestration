package com.scott.payment.payment.service.link;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.payment.mapper.PaymentLinkMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

/**
 * 使用仓库 DDL 和真实 Mapper 执行查询，验证列名、商户隔离、状态与币种汇总；H2 不替代 MySQL 上线预检。
 */
class PaymentLinkMapperIntegrationTest {
 private HikariDataSource source; private JdbcTemplate db; private SqlSessionFactory factory;
 private final LocalDateTime now=LocalDateTime.of(2026,11,17,12,0);
 @BeforeEach void setup() throws Exception {
  source=new HikariDataSource();source.setJdbcUrl("jdbc:h2:mem:link_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");source.setUsername("sa");source.setPassword("");source.setMaximumPoolSize(4);
  db=new JdbcTemplate(source);
  String canonical=Files.readString(Path.of("../docs/sql/payment_acquiring_表结构.sql"));
  for(String table:List.of("payment_checkout_session","payment_checkout_attempt","payment_checkout_event","transaction_locator","payment_link")) ddl(canonical,table);
  String migration=Files.readString(Path.of("../docs/sql/20261117_01_payment_link_schema.sql"));
  for(String table:List.of("payment_link_access","payment_link_collection","payment_link_checkout_binding","payment_link_event")) ddl(migration,table);
  MybatisConfiguration c=new MybatisConfiguration();c.setMapUnderscoreToCamelCase(true);c.setEnvironment(new Environment("link-test",new JdbcTransactionFactory(),source));
  c.addMapper(com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper.class);
  try(var shared=PaymentLinkMapperIntegrationTest.class.getResourceAsStream("/mapper/PaymentLinkQuerySql.xml")) {new XMLMapperBuilder(shared,c,"mapper/PaymentLinkQuerySql.xml",c.getSqlFragments()).parse();}
  try(var input=Files.newInputStream(Path.of("src/main/resources/mapper/PaymentLinkMapper.xml"))) {new XMLMapperBuilder(input,c,"mapper/PaymentLinkMapper.xml",c.getSqlFragments()).parse();}
  factory=new MybatisSqlSessionFactoryBuilder().build(c);
  insertLink("a","m1","ORDER-1");insertLink("b","m2","ORDER-1");
 }
 private void ddl(String content,String table) {
  Matcher m=Pattern.compile("CREATE TABLE (?:IF NOT EXISTS )?`?"+table+"`? \\(.*?\\) ENGINE[^;]+;",Pattern.DOTALL).matcher(content);
  if(!m.find()) throw new AssertionError("DDL not found: "+table);
  String sql=m.group().replaceAll("\\) ENGINE[^;]+;", ")").replaceAll("(?i)COLLATE [A-Za-z0-9_]+", "");
  sql=sql.replaceAll("(?m)^(\\s*)(UNIQUE )?KEY ([`A-Za-z0-9_]+)","$1$2KEY "+table+"_$3");
  db.execute(sql.replace("`", ""));
 }
 private void insertLink(String id,String merchant,String order) {
  try(SqlSession session=factory.openSession(true)) {PaymentLinkDO row=new PaymentLinkDO();row.setLinkId(id);row.setMerchantId(merchant);row.setMerchantOrderNo(order);row.setCreateRequestId("r-"+id);row.setRequestFingerprint("fp");row.setLinkName("Invoice");row.setCurrency("USD");row.setAmount(new BigDecimal("10.00"));row.setCurrencyExponent(2);row.setLocale("en-US");row.setAllowedBrandsJson("[\"VISA\"]");row.setMerchantDisplayName("Merchant");row.setStatus("ACTIVE");row.setExpireTime(now.plusHours(1));row.setCreatedAppCode("MERCHANT");row.setCreatedBy("1");row.setVersion(0);row.setCreateTime(now);row.setUpdateTime(now);session.getMapper(PaymentLinkMapper.class).insert(row);}
 }
 private Actor actor() {Actor actor=new Actor();actor.setAppCode("MERCHANT");actor.setActorId("1");actor.setMerchantIds(Set.of("m1"));return actor;}
 private void bind(String status) {
  db.update("INSERT INTO payment_checkout_session(checkout_session_id,merchant_id,merchant_order_no,merchant_request_id,request_fingerprint,checkout_status,process_stage,last_status_time,label_currency,label_amount,currency_exponent,allowed_payment_methods_json,checkout_domain,expire_time,attempt_count) VALUES('s1','m1','ORDER-1','r','fp',?,'WAITING_CHANNEL',?,'USD',10.00,2,'[]','https://pay.example.invalid',?,2)",status,now,now.plusHours(1));
  db.update("INSERT INTO payment_link_collection(collection_id,link_id,merchant_id,merchant_order_no,submission_deadline,create_time) VALUES('c1','a','m1','ORDER-1',?,?)",now.plusHours(1),now);
  db.update("INSERT INTO payment_link_checkout_binding(link_id,collection_id,checkout_session_id,create_time) VALUES('a','c1','s1',?)",now);
  db.update("INSERT INTO payment_checkout_attempt(checkout_attempt_id,checkout_session_id,merchant_id,merchant_order_no,attempt_no,attempt_request_id,request_fingerprint,attempt_status,process_stage,payment_method,payment_brand,label_currency,label_amount,channel_code) VALUES('t1','s1','m1','ORDER-1',1,'ar','fp','FAILED','RESULT_RENDERED','BANK_CARD','VISA','USD',10.00,'QA')");
 }
 @AfterEach void close(){source.close();}
 @Test void queriesRespectFrozenMerchantScopeAndEmptyScopeFailsClosed() {try(SqlSession s=factory.openSession()){var m=s.getMapper(PaymentLinkMapper.class);assertThat(m.search(actor(),new Query(),now,0)).extracting(View::getMerchantId).containsExactly("m1");Actor empty=actor();empty.setMerchantIds(Set.of());assertThat(m.count(empty,new Query(),now)).isZero();}}
 @Test void attemptQueryUsesActualCheckoutColumnsAndChannelFilter() {bind("FAILED");try(SqlSession s=factory.openSession()){var m=s.getMapper(PaymentLinkMapper.class);assertThat(m.attempts("a",0,10)).singleElement().satisfies(a->{assertThat(a.getCardBrand()).isEqualTo("VISA");assertThat(a.getStatus()).isEqualTo("FAILED");assertThat(new BigDecimal(a.getAmount())).isEqualByComparingTo("10");});Query q=new Query();q.setChannelCode("OTHER");assertThat(m.count(actor(),q,now)).isZero();q.setChannelCode("QA");assertThat(m.count(actor(),q,now)).isEqualTo(1);}}
 @Test void twoAttemptsExposeOnlyThePersistedSystemOrderAndKeepTheRolledBackFailure() {
  bind("SUCCESS");
  db.update("UPDATE payment_checkout_attempt SET transaction_id='reserved-first',transaction_date_time=? WHERE checkout_attempt_id='t1'",now);
  db.update("INSERT INTO payment_checkout_attempt(checkout_attempt_id,checkout_session_id,merchant_id,merchant_order_no,attempt_no,attempt_request_id,request_fingerprint,attempt_status,process_stage,payment_method,payment_brand,label_currency,label_amount,channel_code,transaction_id,transaction_date_time) VALUES('t2','s1','m1','ORDER-1',2,'ar2','fp2','SUCCEEDED','RESULT_RENDERED','BANK_CARD','MASTERCARD','USD',10.00,'QA','persisted-second',?)",now.plusMinutes(1));
  db.update("INSERT INTO transaction_locator(transaction_id,operation_id,root_transaction_id,merchant_id,merchant_order_no,transaction_type,transaction_date_time,root_transaction_date_time) VALUES('persisted-second','op2','persisted-second','m1','ORDER-1','PAYMENT',?,?)",now.plusMinutes(1),now.plusMinutes(1));
  try(SqlSession session=factory.openSession()) {
   var mapper=session.getMapper(PaymentLinkMapper.class);
   var rows=mapper.attempts("a",0,10);
   assertThat(mapper.countAttempts("a")).isEqualTo(2);
   assertThat(rows).hasSize(2);
   assertThat(rows.get(0).getTransactionId()).isEqualTo("persisted-second");
   assertThat(rows.get(0).getTransactionDateTime()).isEqualTo(now.plusMinutes(1));
   assertThat(rows.get(1).getCheckoutAttemptId()).isEqualTo("t1");
   assertThat(rows.get(1).getStatus()).isEqualTo("FAILED");
   assertThat(rows.get(1).getTransactionId()).isNull();
   assertThat(rows.get(1).getTransactionDateTime()).isNull();
  }
 }
 @Test void reservedSystemOrderIdentifiersDoNotBecomeLinksOrCrossMerchantReferences() {
  bind("FAILED");
  db.update("UPDATE payment_checkout_attempt SET transaction_id='reserved-only',transaction_date_time=? WHERE checkout_attempt_id='t1'",now);
  db.update("UPDATE payment_checkout_session SET latest_transaction_id='reserved-only',transaction_date_time=? WHERE checkout_session_id='s1'",now);
  try(SqlSession session=factory.openSession()) {
   var mapper=session.getMapper(PaymentLinkMapper.class);
   assertThat(mapper.search(actor(),new Query(),now,0).get(0).getTransactionId()).isNull();
   assertThat(mapper.attempts("a",0,10).get(0).getTransactionId()).isNull();
   var checkoutMapper=session.getMapper(com.scott.payment.payment.mapper.PaymentCheckoutSessionMapper.class);
   assertThat(checkoutMapper.selectPersistedTransactionId("m1","ORDER-1","reserved-only")).isNull();
   db.update("INSERT INTO transaction_locator(transaction_id,operation_id,root_transaction_id,merchant_id,merchant_order_no,transaction_type,transaction_date_time,root_transaction_date_time) VALUES('reserved-only','other-op','reserved-only','m2','ORDER-1','PAYMENT',?,?)",now,now);
   session.clearCache();
   assertThat(mapper.search(actor(),new Query(),now,0).get(0).getTransactionId()).isNull();
   assertThat(mapper.attempts("a",0,10).get(0).getTransactionId()).isNull();
   assertThat(checkoutMapper.selectPersistedTransactionId("m1","ORDER-1","reserved-only")).isNull();
   db.update("UPDATE transaction_locator SET merchant_id='m1',merchant_order_no='OTHER-ORDER' WHERE transaction_id='reserved-only'");
   session.clearCache();
   assertThat(mapper.attempts("a",0,10).get(0).getTransactionId()).isNull();
   assertThat(checkoutMapper.selectPersistedTransactionId("m1","ORDER-1","reserved-only")).isNull();
   db.update("UPDATE transaction_locator SET merchant_order_no='ORDER-1' WHERE transaction_id='reserved-only'");
   session.clearCache();
   assertThat(mapper.search(actor(),new Query(),now,0).get(0).getTransactionId()).isEqualTo("reserved-only");
   assertThat(mapper.attempts("a",0,10).get(0).getTransactionId()).isEqualTo("reserved-only");
   assertThat(checkoutMapper.selectPersistedTransactionId("m1","ORDER-1","reserved-only")).isEqualTo("reserved-only");
  }
 }
 @Test void lifecycleAndRealSummaryRemainSeparateFromFailedAttempts() {bind("SUCCESS");try(SqlSession s=factory.openSession()){var m=s.getMapper(PaymentLinkMapper.class);assertThat(m.search(actor(),new Query(),now,0)).singleElement().extracting(View::getStatus).isEqualTo("COMPLETED");var summary=m.summarize(actor(),new Query(),now).get(0);assertThat(summary.getCompleted()).isEqualTo(1);assertThat(summary.getFailedAttempts()).isEqualTo(1);assertThat(summary.getAmount()).isEqualByComparingTo("10");}}
 @Test void merchantOrderUniqueConstraintRejectsAnotherLinkAndIgnoresOtherMerchant() {assertThatThrownBy(()->insertLink("c","m1","ORDER-1")).hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);}
 @Test void disabledAndExpiryFiltersUseDatabaseFacts() {db.update("UPDATE payment_link SET expire_time=? WHERE link_id='a'",now);try(SqlSession s=factory.openSession()){var m=s.getMapper(PaymentLinkMapper.class);Query q=new Query();q.setStatus("EXPIRED");assertThat(m.count(actor(),q,now)).isEqualTo(1);db.update("UPDATE payment_link SET status='DISABLED' WHERE link_id='a'");q.setStatus("DISABLED");assertThat(m.count(actor(),q,now)).isEqualTo(1);}}
 @Test void disableRowLockSerializesTheNextSubmissionRead() throws Exception {
  var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(SqlSession owner=factory.openSession(false)) {
   owner.getMapper(PaymentLinkMapper.class).lock("a");
   owner.getConnection().createStatement().executeUpdate("UPDATE payment_link SET status='DISABLED' WHERE link_id='a'");
   var started=new java.util.concurrent.CountDownLatch(1);
   var next=executor.submit(()->{try(SqlSession reader=factory.openSession(true)){started.countDown();return reader.getMapper(PaymentLinkMapper.class).lock("a").getStatus();}});
   assertThat(started.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   assertThatThrownBy(()->next.get(100,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
   owner.commit(true);assertThat(next.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("DISABLED");
  } finally {executor.shutdownNow();}
 }
 @Test void simultaneousSameOrderCreatesOnlyOneCollectionRight() throws Exception {
  var executor=java.util.concurrent.Executors.newFixedThreadPool(4);
  try {
   var jobs=java.util.stream.IntStream.range(0,12).<java.util.concurrent.Callable<Boolean>>mapToObj(index->()->{try{insertLink("race"+index,"m1","RACE");return true;}catch(org.apache.ibatis.exceptions.PersistenceException duplicate){if(duplicate.getCause() instanceof java.sql.SQLIntegrityConstraintViolationException)return false;throw duplicate;}}).toList();
   long successes=0;for(var result:executor.invokeAll(jobs)){if(result.get())successes++;}
   assertThat(successes).isEqualTo(1);assertThat(db.queryForObject("SELECT COUNT(*) FROM payment_link WHERE merchant_id='m1' AND merchant_order_no='RACE'",Long.class)).isEqualTo(1);
  } finally {executor.shutdownNow();}
 }
 @Test void collectionQueryUsesItsOwnIdentityAndOriginalDeadline() {bind("PROCESSING");try(SqlSession session=factory.openSession()){var mapper=session.getMapper(PaymentLinkMapper.class);assertThat(mapper.collections("a",0,10)).singleElement().satisfies(c->{assertThat(c.getCollectionId()).isEqualTo("c1");assertThat(c.getMerchantOrderNo()).isEqualTo("ORDER-1");assertThat(c.getPaymentStatus()).isEqualTo("PROCESSING");assertThat(c.getSubmissionDeadline()).isEqualTo(now.plusHours(1));});assertThat(mapper.countCollections("b")).isZero();}}
 @Test void projectionsPreserveTheFrozenCurrencyExponentWithoutDisplayRounding() {
  bind("FAILED");
  try(SqlSession session=factory.openSession()) {
   var service=new PaymentLinkQueryService(session.getMapper(PaymentLinkMapper.class), org.mockito.Mockito.mock(PaymentLinkPolicy.class));
   assertThat(service.search(actor(),new Query()).getRecords()).singleElement().satisfies(v->{assertThat(v.getAmount()).isEqualTo("10.00");assertThat(v.getCurrencyExponent()).isEqualTo(2);});
   assertThat(service.attempts(actor(),"a",new Query()).getRecords()).singleElement().satisfies(v->{assertThat(v.getAmount()).isEqualTo("10.00");assertThat(v.getCurrencyExponent()).isEqualTo(2);});
   db.update("UPDATE payment_link SET currency='JPY',currency_exponent=0,amount=100 WHERE link_id='a'");
   session.clearCache();
   assertThat(service.search(actor(),new Query()).getRecords().get(0).getAmount()).isEqualTo("100");
   db.update("UPDATE payment_link SET currency='KWD',currency_exponent=3,amount=1.234 WHERE link_id='a'");
   session.clearCache();
   assertThat(service.search(actor(),new Query()).getRecords().get(0).getAmount()).isEqualTo("1.234");
  }
 }

 @Test void timelineUsesBoundFactsAndExcludesPrivatePayloadAndPollingNoise() {
  bind("FAILED");
  db.update("INSERT INTO payment_link_event(event_id,link_id,event_type,actor_app_code,actor_id,create_time) VALUES('ev1','a','CREATED','ADMIN','42',?)",now.minusMinutes(1));
  db.update("INSERT INTO payment_checkout_event(checkout_event_id,checkout_session_id,merchant_id,event_type,event_result,event_time,event_payload_json) VALUES('ce1','s1','m1','PAYMENT_SUBMITTED','SUCCESS',?, '{\"private\":\"fictional-hidden\"}')",now);
  db.update("INSERT INTO payment_checkout_event(checkout_event_id,checkout_session_id,merchant_id,event_type,event_result,event_time) VALUES('poll1','s1','m1','PAYMENT_STATUS_QUERIED','SUCCESS',?)",now);
  try(SqlSession session=factory.openSession()) {
   var mapper=session.getMapper(PaymentLinkMapper.class);
   assertThat(mapper.timeline("a",now,0,10)).extracting(Event::getEventType).containsExactlyInAnyOrder("CREATED","PAYMENT_SUBMITTED","PAYMENT_FAILED");
   assertThat(mapper.countTimeline("a",now)).isEqualTo(3);
   assertThat(mapper.timeline("b",now,0,10)).isEmpty();
   assertThat(mapper.timeline("a",now.plusHours(2),0,10)).extracting(Event::getEventType).contains("LINK_EXPIRED");
   assertThat(mapper.timeline("a",now,0,1)).hasSize(1);
  }
 }


 @Test void currencyFilterAppliesToRowsCountsAndSummariesIncludingExactOrderCombinations() {
  insertLink("eur", "m1", "ORDER-EUR");
  db.update("UPDATE payment_link SET currency='EUR' WHERE link_id='eur'");
  bind("SUCCESS");
  try (SqlSession session=factory.openSession()) {
   var mapper=session.getMapper(PaymentLinkMapper.class);Query query=new Query();query.setCurrency("EUR");
   assertThat(mapper.search(actor(),query,now,0)).extracting(View::getLinkId).containsExactly("eur");
   assertThat(mapper.count(actor(),query,now)).isEqualTo(1);
   assertThat(mapper.summarize(actor(),query,now)).singleElement().satisfies(row->{assertThat(row.getCurrency()).isEqualTo("EUR");assertThat(row.getAmount()).isEqualByComparingTo("0");});
   query.setMerchantOrderNo("ORDER-1");
   assertThat(mapper.search(actor(),query,now,0)).isEmpty();
   assertThat(mapper.count(actor(),query,now)).isZero();
   assertThat(mapper.summarize(actor(),query,now)).isEmpty();
   query.setCurrency("USD");assertThat(mapper.count(actor(),query,now)).isEqualTo(1);
   assertThat(mapper.summarize(actor(),query,now)).singleElement().satisfies(row->assertThat(row.getAmount()).isEqualByComparingTo("10"));
  }
 }

 @Test void orderFilterIsExactAndNeverIncludesAnotherOrderOrMerchant() {
  insertLink("other", "m1", "ORDER-10");
  try (SqlSession session=factory.openSession()) {
   var mapper=session.getMapper(PaymentLinkMapper.class); Query query=new Query();query.setMerchantOrderNo("ORDER-1");
   assertThat(mapper.search(actor(), query, now, 0)).extracting(View::getLinkId).containsExactly("a");
   assertThat(mapper.count(actor(), query, now)).isEqualTo(1);
   assertThat(mapper.summarize(actor(), query, now)).singleElement().satisfies(row -> assertThat(row.getTotal()).isEqualTo(1));
   query.setMerchantOrderNo("ORDER");assertThat(mapper.count(actor(), query, now)).isZero();
   query.setMerchantOrderNo(null);query.setLinkId("a");assertThat(mapper.count(actor(), query, now)).isEqualTo(1);
   var service=new PaymentLinkQueryService(mapper, org.mockito.Mockito.mock(PaymentLinkPolicy.class));
   query.setLinkId(null);query.setKeyword("Invoice");
   assertThatThrownBy(() -> service.search(actor(), query)).isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class);
   assertThatThrownBy(() -> service.summary(actor(), query)).isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class);
  }
 }
}
