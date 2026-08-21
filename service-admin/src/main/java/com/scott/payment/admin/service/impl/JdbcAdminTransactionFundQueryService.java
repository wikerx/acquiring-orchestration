package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.fund.FundAccountEntities.PendingBalanceAggregate;
import com.scott.payment.admin.service.AdminTransactionFundQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionFundQueryService
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 管理端交易资金 JDBC 查询实现；通过 transaction 逻辑数据源读取副本分片，不持久化派生在途余额。
 * @status : create
 */
@Service
public class JdbcAdminTransactionFundQueryService implements AdminTransactionFundQueryService {

    /** 交易成功终态编码。 */
    private static final String SUCCESS = "SUCCESS";
    /** 尚未完成交易结算的状态编码。 */
    private static final String NOT_SETTLED = "NOT_SETTLED";
    /** 会形成商户待结算资金的正向和逆向交易动作。 */
    private static final List<String> FUND_TRANSACTION_TYPES = List.of(
            "PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION", "REFUND", "CHARGEBACK");
    /** 在途净额中按负方向计算的逆向交易动作。 */
    private static final List<String> DEBIT_TRANSACTION_TYPES = List.of("REFUND", "CHARGEBACK");

    /** 使用统一超时预算的交易查询 JDBC 模板。 */
    private final NamedParameterJdbcTemplate jdbcTemplate;
    /** transaction 逻辑数据源普通读执行器，内部路由到已配置从库。 */
    private final TransactionLogicalReadExecutor transactionLogicalReadExecutor;
    /** 已发布最早交易季度的起始时间，用于约束 ShardingSphere 路由范围。 */
    private final LocalDateTime registeredNodeBegin;
    /** Asia/Shanghai 系统业务时钟，用于排除尚未发生的未来交易。 */
    private final Clock clock;

    /**
     * 创建生产环境管理端交易资金查询服务。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param transactionLogicalReadExecutor 交易逻辑数据源副本读执行器
     * @param shardingProperties 已发布分片与查询预算配置
     * @param queryJdbcTemplateFactory 查询专用 JDBC 模板工厂
     */
    @Autowired
    public JdbcAdminTransactionFundQueryService(
            DataSource dataSource,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            TransactionShardingProperties shardingProperties,
            TransactionQueryJdbcTemplateFactory queryJdbcTemplateFactory) {
        this(queryJdbcTemplateFactory.create(dataSource, shardingProperties),
                transactionLogicalReadExecutor, shardingProperties,
                Clock.system(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID)));
    }

    /**
     * 创建可注入查询模板和时钟的交易资金查询服务。
     *
     * @param jdbcTemplate 命名参数 JDBC 查询模板
     * @param transactionLogicalReadExecutor 交易逻辑数据源副本读执行器
     * @param shardingProperties 已发布分片配置
     * @param clock 系统业务时钟
     */
    public JdbcAdminTransactionFundQueryService(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionLogicalReadExecutor transactionLogicalReadExecutor,
            TransactionShardingProperties shardingProperties,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionLogicalReadExecutor = transactionLogicalReadExecutor;
        this.registeredNodeBegin = resolveRegisteredNodeBegin(shardingProperties.getPhysicalNodes());
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<PendingBalanceAggregate> sumPendingBalances(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        MapSqlParameterSource parameters = fundParameters(normalizedMerchantId);
        return transactionLogicalReadExecutor.read(() -> jdbcTemplate.query("""
                SELECT merchant_id AS merchantId,
                       label_currency AS currency,
                       COALESCE(SUM(CASE
                           WHEN transaction_type IN (:debitTransactionTypes) THEN -label_amount
                           ELSE label_amount
                       END), 0) AS amount
                FROM transaction_operation
                WHERE merchant_id = :merchantId
                  AND transaction_date_time >= :beginTime
                  AND transaction_date_time < :endTime
                  AND transaction_status = :transactionStatus
                  AND settlement_status = :settlementStatus
                  AND transaction_type IN (:fundTransactionTypes)
                  AND deleted = 0
                GROUP BY merchant_id, label_currency
                ORDER BY label_currency
                """, parameters, BeanPropertyRowMapper.newInstance(PendingBalanceAggregate.class)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean hasSuccessfulFundTransaction(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        MapSqlParameterSource parameters = fundParameters(normalizedMerchantId);
        Boolean exists = transactionLogicalReadExecutor.read(() -> jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM transaction_operation
                    WHERE merchant_id = :merchantId
                      AND transaction_date_time >= :beginTime
                      AND transaction_date_time < :endTime
                      AND transaction_status = :transactionStatus
                      AND transaction_type IN (:fundTransactionTypes)
                      AND deleted = 0
                    LIMIT 1
                )
                """, parameters, Boolean.class));
        return Boolean.TRUE.equals(exists);
    }

    /** 组装所有交易分片查询都必须携带的商户、时间和资金动作参数。 */
    private MapSqlParameterSource fundParameters(String merchantId) {
        return new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("beginTime", registeredNodeBegin)
                .addValue("endTime", LocalDateTime.now(clock))
                .addValue("transactionStatus", SUCCESS)
                .addValue("settlementStatus", NOT_SETTLED)
                .addValue("fundTransactionTypes", FUND_TRANSACTION_TYPES)
                .addValue("debitTransactionTypes", DEBIT_TRANSACTION_TYPES);
    }

    /** 拒绝空商户号，避免资金聚合失去租户隔离条件。 */
    private String requireMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户号不能为空");
        }
        return merchantId.trim();
    }

    /** 将最早已发布 yyyy0Q 物理节点转换为闭开查询范围的起点。 */
    private static LocalDateTime resolveRegisteredNodeBegin(List<String> physicalNodes) {
        return physicalNodes == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : physicalNodes.stream()
                .filter(node -> node != null && node.matches("\\d{4}0[1-4]"))
                .min(String::compareTo)
                .map(node -> LocalDateTime.of(
                        Integer.parseInt(node.substring(0, 4)),
                        (Character.digit(node.charAt(5), 10) - 1) * 3 + 1,
                        1, 0, 0))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }
}
