package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelResultTransactionService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 渠道同步结果事务默认实现，位于 service-payment 服务实现层，使用 REQUIRES_NEW 保证渠道结果持久化不依赖调用方事务。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultPaymentChannelResultTransactionService implements PaymentChannelResultTransactionService {

    /**
     * transaction Record Service 依赖，用于 Default Payment Channel Result Transaction Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与渠道同步结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建渠道同步结果事务默认实现。
     *
     * @param transactionRecordService 交易事实记录服务
     */
    public DefaultPaymentChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this(transactionRecordService, null);
    }

    /**
     * 创建带终态 Outbox 能力的渠道同步结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易状态变更 Outbox 服务
     */
    @Autowired
    public DefaultPaymentChannelResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中保存首次交易渠道同步结果。
     *
     * @param commandDTO 创建交易命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的同步结果
     * @param riskDecisionEnum 本地准备阶段风控决策
     * @param currencyExponent 交易币种默认辅币位
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                           PaymentRouteResultDTO routeResultDTO,
                                           PaymentChannelInvokeResultDTO invokeResultDTO,
                                           PaymentCreateResultDTO resultDTO,
                                           PaymentRiskDecisionEnum riskDecisionEnum,
                                           int currencyExponent) {
        boolean statusChanged = transactionRecordService.completeInitialChannelResultAndReport(
                commandDTO,
                routeResultDTO,
                invokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
        if (statusChanged && lifecycleEventService != null
                && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()))) {
            lifecycleEventService.saveStatusChanged(
                    resultDTO.getTransactionId(),
                    resultDTO.getOperationId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    resultDTO.getTransactionType(),
                    resultDTO.getStatus(),
                    commandDTO.getTransactionDateTime());
        }
    }
}
