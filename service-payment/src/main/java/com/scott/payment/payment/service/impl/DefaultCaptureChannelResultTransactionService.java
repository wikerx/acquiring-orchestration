package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.CaptureChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultCaptureChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @description : Capture 渠道结果默认事务实现，使用 REQUIRES_NEW 保存同步结果并通过 CAS 推进 Capture 动作。
 * @status : create
 */
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultCaptureChannelResultTransactionService implements CaptureChannelResultTransactionService {

    /**
     * transaction Record Service 依赖，用于 Default Capture Channel Result Transaction Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 整理默认capture渠道结果交易service，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param transactionRecordService transaction Record Service 输入值，参与 交易记录service 的查询、校验、转换、写入或日志摘要
     */
    public DefaultCaptureChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

    /**
     * 在独立事务中持久化请款渠道同步结果。
     *
     * <p>仅对已落库的请款动作执行 CAS 完成，累计已请款金额由数据库动作记录计算。</p>
     *
     * @param preparationResultDTO 本地准备事务结果
     * @param invokeResultDTO      渠道调用结果
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordCaptureChannelResult(CapturePreparationResultDTO preparationResultDTO,
                                           PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionId(resultDTO.getTransactionId());
        if (operationDO == null) {
            return;
        }
        transactionRecordService.completeCaptureChannelResult(
                operationDO,
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getCurrencyExponent());
    }
}
