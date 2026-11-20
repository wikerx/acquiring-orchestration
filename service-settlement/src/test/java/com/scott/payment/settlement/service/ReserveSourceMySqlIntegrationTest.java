package com.scott.payment.settlement.service;

import com.scott.payment.settlement.entity.*;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.*;
import com.scott.payment.settlement.service.impl.DefaultSettlementLedgerPostingService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 在独立 MySQL 测试库执行真实 Mapper、行锁和 Spring 事务；禁止连接业务库。
 * 运行前应用现有清分/结算表结构，库名必须以 reserve_source_it_ 开头。
 * 环境变量 SETTLEMENT_RESERVE_TEST_URL/USER/PASSWORD 仅由测试进程读取，不写日志或源码。
 */
@EnabledIfEnvironmentVariable(named="SETTLEMENT_RESERVE_TEST_URL", matches="jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/reserve_source_it_[a-zA-Z0-9_]+.*")
class ReserveSourceMySqlIntegrationTest {
    private static final List<String> TABLES=List.of("settlement_batch","settlement_candidate",
            "settlement_batch_candidate","settlement_batch_rate","settlement_result_item",
            "merchant_fund_account","merchant_fund_ledger","merchant_reserve_item","merchant_reserve_action",
            "clearing_label_principal_repair","clearing_label_principal_repair_reserve_state_audit",
            "clearing_label_principal_repair_dependency_audit","settlement_candidate_dependency",
            "transaction_reserve_clearing_detail","transaction_reserve_clearing_state");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private SettlementLedgerPostingService posting;
    private Faults faults;
    private ReserveHoldFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        dataSource=new DriverManagerDataSource(System.getenv("SETTLEMENT_RESERVE_TEST_URL"),
                System.getenv("SETTLEMENT_RESERVE_TEST_USER"),System.getenv("SETTLEMENT_RESERVE_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(dataSource);
        String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
        assertThat(database).startsWith("reserve_source_it_");
        for(String table:TABLES)jdbc.execute("DELETE FROM "+table);
        faults=new Faults();faults.jdbc=jdbc;faults.dataSource=dataSource;
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        configuration.addInterceptor(faults);
        for(Class<?> type:List.of(SettlementBatchMapper.class,SettlementResultMapper.class,SettlementFundMapper.class,
                SettlementReserveMapper.class,SettlementCandidateMapper.class,SettlementBatchCandidateMapper.class,
                SettlementProjectionMapper.class,SettlementReserveSourceMapper.class))configuration.addMapper(type);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(dataSource);factory.setConfiguration(configuration);
        SqlSessionFactory sessions=factory.getObject();var template=new SqlSessionTemplate(sessions);
        var resolver=new SettlementReserveSourceResolver(template.getMapper(SettlementReserveSourceMapper.class));
        var target=new DefaultSettlementLedgerPostingService(template.getMapper(SettlementBatchMapper.class),
                template.getMapper(SettlementResultMapper.class),template.getMapper(SettlementFundMapper.class),
                template.getMapper(SettlementReserveMapper.class),template.getMapper(SettlementCandidateMapper.class),
                template.getMapper(SettlementBatchCandidateMapper.class),template.getMapper(SettlementProjectionMapper.class),resolver);
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(
                new DataSourceTransactionManager(dataSource),new AnnotationTransactionAttributeSource()));
        posting=(SettlementLedgerPostingService)proxy.getProxy();
        fixture=new ReserveHoldFixture();seed(fixture);
    }

    @Test
    void postsMigratedHoldOnceAndKeepsImmutableReleaseIdentity() {
        assertThat(posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW)).isEqualTo(1);
        assertCommittedOnce();
        assertThatThrownBy(()->posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW))
                .isInstanceOf(SettlementProcessingException.class);
        assertCommittedOnce();
        assertThat(jdbc.queryForObject("SELECT source_reserve_detail_no FROM transaction_reserve_clearing_detail WHERE reserve_clearing_detail_no='RELEASE-DETAIL'",String.class)).isEqualTo("OLD-HOLD");
        assertThat(jdbc.queryForObject("SELECT source_reserve_detail_no FROM merchant_reserve_action WHERE action_type='RELEASE'",String.class)).isEqualTo("RELEASE-DETAIL");
        assertThat(jdbc.queryForObject("SELECT reserve_item_id FROM merchant_reserve_action WHERE action_type='RELEASE'",Long.class)).isEqualTo(40L);
    }

    @Test
    void rollsBackLedgerBalanceReserveAndStatusWhenFailureOccursAfterActionInsert() {
        faults.failAfterAction.set(true);
        assertThatThrownBy(()->posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW))
                .hasMessageContaining("INJECTED_RESERVE_POSTING_FAILURE");
        assertThat(faults.sawUncommittedLedger).isTrue();
        assertThat(count("merchant_fund_ledger")).isZero();
        assertThat(count("merchant_reserve_action")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_result_item WHERE result_role='LEDGER_POSTING'",Integer.class)).isZero();
        assertThat(balance()).isEqualByComparingTo("100.00");
        assertThat(released()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT version FROM merchant_reserve_item WHERE id=40",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT batch_status FROM settlement_batch",String.class)).isEqualTo("CALCULATED");
        assertThat(jdbc.queryForObject("SELECT version FROM settlement_batch",Long.class)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT candidate_status FROM settlement_candidate WHERE id=10",String.class)).isEqualTo("CLAIMED");
        posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW);
        assertCommittedOnce();
    }

    @Test
    void serializesCompetingWorkersWithoutDoubleReleaseOrLedger() throws Exception {
        var start=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> work=()->{start.await(10,TimeUnit.SECONDS);try {
                posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW);return true;
            }catch(SettlementProcessingException expected){return false;}};
            var first=pool.submit(work);var second=pool.submit(work);
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true,false);
            assertCommittedOnce();
        }finally {pool.shutdownNow();}
    }

    @Test
    void rejectsVersionChangedBetweenProofReadAndLiabilityLockAndRollsBackMoney() {
        faults.changeVersionBeforeLock.set(true);
        assertThatThrownBy(()->posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW))
                .isInstanceOfSatisfying(SettlementProcessingException.class,e->assertThat(e.getFailureCode()).isEqualTo("SETTLEMENT_RESERVE_BALANCE_CAS_FAILED"));
        assertThat(count("merchant_fund_ledger")).isZero();assertThat(count("merchant_reserve_action")).isZero();
        assertThat(balance()).isEqualByComparingTo("100.00");assertThat(released()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT version FROM merchant_reserve_item WHERE id=40",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT batch_status FROM settlement_batch",String.class)).isEqualTo("CALCULATED");
    }

    @Test
    void globalReleaseKeyRejectsCrossBatchReplayEvenWithAnIncorrectRestoredLiabilitySnapshot() {
        posting.post(fixture.batch,fixture.facts(),"test-worker",ReserveHoldFixture.NOW);assertCommittedOnce();
        // 仅在独立测试库故意制造错误责任快照，验证最终动作唯一键仍拒绝跨批重复资金效果。
        jdbc.update("UPDATE merchant_reserve_item SET released_amount=0,reserve_status='HELD',version=0 WHERE id=40");
        var second=new ReserveHoldFixture();second.batch.setSettlementBatchNo("SB20261120-00000002");second.batch.setDailySequence(2);
        second.candidate.setSettlementBatchNo(second.batch.getSettlementBatchNo());insert("settlement_batch",second.batch);
        var rate=new LinkedHashMap<String,Object>();rate.put("id",2L);rate.put("settlement_batch_no",second.batch.getSettlementBatchNo());
        rate.put("source_currency","USD");rate.put("target_currency","USD");rate.put("direct_rate",BigDecimal.ONE);rate.put("rate_status","LOCKED");rate.put("rate_source","SYSTEM_IDENTITY");insert("settlement_batch_rate",rate);
        var component=component(second);component.setSettlementResultItemNo("COMPONENT-2");insert("settlement_result_item",component);
        assertThatThrownBy(()->posting.post(second.batch,second.facts(),"test-worker",ReserveHoldFixture.NOW))
                .isInstanceOfSatisfying(SettlementProcessingException.class,e->assertThat(e.getFailureCode()).isEqualTo("SETTLEMENT_RESERVE_ACTION_IDEMPOTENCY_CONFLICT"));
        assertThat(count("merchant_fund_ledger")).isEqualTo(1);assertThat(count("merchant_reserve_action")).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo("105.26");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_result_item WHERE result_role='LEDGER_POSTING'",Integer.class)).isEqualTo(1);
    }

    private void assertCommittedOnce() {
        assertThat(count("merchant_fund_ledger")).isEqualTo(1);assertThat(count("merchant_reserve_action")).isEqualTo(1);
        assertThat(balance()).isEqualByComparingTo("105.26");assertThat(released()).isEqualByComparingTo("5.26");
        assertThat(jdbc.queryForObject("SELECT version FROM merchant_reserve_item WHERE id=40",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT candidate_status FROM settlement_candidate WHERE id=10",String.class)).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_result_item WHERE result_role='LEDGER_POSTING'",Integer.class)).isEqualTo(1);
    }
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
    private BigDecimal balance(){return jdbc.queryForObject("SELECT available_balance FROM merchant_fund_account WHERE id=1",BigDecimal.class);}
    private BigDecimal released(){return jdbc.queryForObject("SELECT released_amount FROM merchant_reserve_item WHERE id=40",BigDecimal.class);}

    private void seed(ReserveHoldFixture f) throws Exception {
        insert("settlement_batch",f.batch);insert("settlement_candidate",f.candidate);
        var newCandidate=new SettlementCandidateDO();org.springframework.beans.BeanUtils.copyProperties(f.candidate,newCandidate);
        newCandidate.setId(12L);newCandidate.setCandidateNo("SC-NEW-HOLD");newCandidate.setSourceType("CLEARING_REVISION");
        newCandidate.setSourceBusinessId("PAYMENT-STATE");newCandidate.setSourceRevision(2);
        newCandidate.setSourceTransactionId("PAYMENT-TX");newCandidate.setSourceTransactionDateTime(ReserveHoldFixture.ORIGINAL_TIME);
        newCandidate.setCandidateStatus("POSTED");newCandidate.setSettlementBatchNo("SB20261119-00000001");insert("settlement_candidate",newCandidate);
        insert("transaction_reserve_clearing_detail",f.oldHold);insert("transaction_reserve_clearing_detail",f.newHold);
        insert("transaction_reserve_clearing_detail",f.release);insert("transaction_reserve_clearing_state",f.state);
        insert("merchant_reserve_item",f.item);
        var account=new MerchantFundAccountDO();account.setId(1L);account.setMerchantId(f.batch.getMerchantId());
        account.setSettlementCurrency("USD");account.setAvailableBalance(new BigDecimal("100.00"));account.setAccountVersion(0L);account.setAccountStatus("NORMAL");insert("merchant_fund_account",account);
        insert("settlement_batch_candidate",Map.of("settlement_batch_no",f.batch.getSettlementBatchNo(),"candidate_id",10L,"source_type","RESERVE_RELEASE","relation_status","CLAIMED","source_revision",1));
        insert("settlement_candidate_dependency",Map.of("id",51L,"candidate_id",10L,"depends_on_candidate_id",12L,"dependency_type","RESERVE_HOLD_POSTED"));
        insert("clearing_label_principal_repair",Map.ofEntries(
            Map.entry("principal_amount_before",BigDecimal.ONE),Map.entry("label_amount",BigDecimal.TEN),
            Map.entry("submit_operator","test-maker"),Map.entry("review_operator","test-checker"),Map.entry("review_comment","fixture-approved"),
            Map.entry("review_time",ReserveHoldFixture.NOW),Map.entry("executed_time",ReserveHoldFixture.NOW),Map.entry("new_candidate_no","SC-NEW-HOLD"),
            Map.entry("repair_no","REPAIR-1"),Map.entry("repair_status","EXECUTED"),Map.entry("merchant_id",f.batch.getMerchantId()),
            Map.entry("transaction_id","PAYMENT-TX"),Map.entry("transaction_date_time",ReserveHoldFixture.ORIGINAL_TIME),Map.entry("finance_state_id","PAYMENT-STATE"),
            Map.entry("expected_clearing_revision",1),Map.entry("new_clearing_revision",2),Map.entry("reserve_fact_count",1),Map.entry("reserve_state_count",1),Map.entry("dependency_count",1),Map.entry("candidate_id",11L),Map.entry("new_candidate_id",12L)));
        insert("clearing_label_principal_repair_reserve_state_audit",Map.of("repair_no","REPAIR-1","reserve_state_id","RESERVE-STATE","old_hold_detail_no","OLD-HOLD","new_hold_detail_no","NEW-HOLD","old_state_version",1L,"new_state_version",2L,"reserve_state_row_id",1L));
        insert("clearing_label_principal_repair_dependency_audit",Map.of("repair_no","REPAIR-1","dependency_id",51L,"dependent_candidate_id",10L,"old_depends_on_candidate_id",11L,"new_depends_on_candidate_id",12L,"dependency_type","RESERVE_HOLD_POSTED"));
        insert("settlement_batch_rate",Map.of("id",1L,"settlement_batch_no",f.batch.getSettlementBatchNo(),"source_currency","USD","target_currency","USD","direct_rate",BigDecimal.ONE,"rate_status","LOCKED","rate_source","SYSTEM_IDENTITY"));
        insert("settlement_result_item",component(f));
    }
    private SettlementResultItemDO component(ReserveHoldFixture f) {
        var row=new SettlementResultItemDO();row.setSettlementResultItemNo("COMPONENT-1");row.setSettlementBatchNo(f.batch.getSettlementBatchNo());
        row.setCandidateId(10L);row.setResultLineNo(1);row.setMerchantId(f.batch.getMerchantId());row.setSettlementAccountId(1L);
        row.setSourceDetailType("RESERVE_CLEARING");row.setSourceDetailNo("RELEASE-DETAIL");row.setResultItemType("RESERVE_RELEASE");
        row.setResultRole("FINANCIAL_COMPONENT");row.setDirection("CREDIT");row.setSourceAmount(new BigDecimal("5.26"));row.setSourceCurrency("USD");
        row.setSourceCurrencyExponent(2);row.setTargetAmount(new BigDecimal("5.26"));row.setUnroundedTargetAmount(new BigDecimal("5.26"));
        row.setTargetCurrency("USD");row.setTargetCurrencyExponent(2);row.setSettlementBatchRateId(1L);row.setRoundingMode("HALF_UP");row.setAppliedLimit("NONE");return row;
    }

    /** 按真实表结构补齐与本场景无关的必填审计列；关键金额、身份及状态均由场景显式指定。 */
    private void insert(String table,Object bean) {
        Map<String,Object> supplied=new LinkedHashMap<>();
        if(bean instanceof Map<?,?> map)map.forEach((k,v)->supplied.put((String)k,v));
        else {var wrapper=new BeanWrapperImpl(bean);for(var p:wrapper.getPropertyDescriptors()) {
            if(!"class".equals(p.getName())) {Object value=wrapper.getPropertyValue(p.getName());
                if(value!=null)supplied.put(p.getName().replaceAll("([a-z0-9])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),value);}
        }}
        Map<String,Object> values=new LinkedHashMap<>();
        try(Connection c=dataSource.getConnection();var columns=c.getMetaData().getColumns(c.getCatalog(),null,table,null)) {
            while(columns.next()) {
                String column=columns.getString("COLUMN_NAME");Object value=supplied.get(column);
                if(value!=null){values.put(column,value);continue;}
                if(columns.getInt("NULLABLE")!=java.sql.DatabaseMetaData.columnNoNulls
                        || columns.getString("COLUMN_DEF")!=null || "YES".equals(columns.getString("IS_AUTOINCREMENT")))continue;
                if(column.endsWith("_hash")){values.put(column,"0".repeat(64));continue;}
                if(column.equals("reserve_basis")){values.put(column,"LABEL_AMOUNT");continue;}
                if(column.equals("original_rounding_mode")){values.put(column,"HALF_UP");continue;}
                int type=columns.getInt("DATA_TYPE");value=switch(type) {
                    case Types.INTEGER,Types.BIGINT,Types.TINYINT,Types.SMALLINT,Types.DECIMAL,Types.NUMERIC -> 0;
                    case Types.DATE -> ReserveHoldFixture.NOW.toLocalDate();
                    case Types.TIMESTAMP,Types.TIMESTAMP_WITH_TIMEZONE -> ReserveHoldFixture.NOW;
                    default -> "fixture-"+UUID.randomUUID().toString().replace("-","");
                };
                if(value instanceof String text && text.length()>columns.getInt("COLUMN_SIZE"))value=text.substring(0,columns.getInt("COLUMN_SIZE"));
                values.put(column,value);
            }
        }catch(Exception e){throw new IllegalStateException(e);}
        String fields=String.join(",",values.keySet());String marks=values.keySet().stream().map(k->"?").collect(Collectors.joining(","));
        jdbc.update("INSERT INTO "+table+" ("+fields+") VALUES ("+marks+")",values.values().toArray());
    }

    @Intercepts({@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}),
            @Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,org.apache.ibatis.session.RowBounds.class,org.apache.ibatis.session.ResultHandler.class})})
    static class Faults implements Interceptor {
        final AtomicBoolean failAfterAction=new AtomicBoolean();final AtomicBoolean changeVersionBeforeLock=new AtomicBoolean();
        boolean sawUncommittedLedger;JdbcTemplate jdbc;DriverManagerDataSource dataSource;
        public Object intercept(Invocation invocation) throws Throwable {
            String id=((MappedStatement)invocation.getArgs()[0]).getId();
            if(id.endsWith("SettlementReserveMapper.selectBySourceForUpdate")&&changeVersionBeforeLock.compareAndSet(true,false)) {
                try(Connection c=dataSource.getConnection();var statement=c.createStatement()) {
                    statement.executeUpdate("UPDATE merchant_reserve_item SET version=version+1 WHERE id=40");
                }
            }
            Object result=invocation.proceed();
            if(id.endsWith("SettlementReserveMapper.insertActionIdempotent")&&failAfterAction.compareAndSet(true,false)) {
                sawUncommittedLedger=jdbc.queryForObject("SELECT COUNT(*) FROM merchant_fund_ledger",Integer.class)==1;
                throw new IllegalStateException("INJECTED_RESERVE_POSTING_FAILURE");
            }
            return result;
        }
    }
}
