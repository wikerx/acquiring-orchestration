package com.scott.payment.payment.service.impl;

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
public class DefaultCaptureChannelResultTransactionService implements CaptureChannelResultTransactionService {

    /**
     * transaction Record Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 创建 DefaultCaptureChannelResultTransactionService 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：支付核心服务层；输入来源、输出结构和异常语义由 DefaultCaptureChannelResultTransactionService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param transactionRecordService transaction Record Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public DefaultCaptureChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

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
