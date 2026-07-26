package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.VoidChannelResultTransactionService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultVoidChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @description : Void 渠道结果默认事务实现，使用 REQUIRES_NEW 保存同步结果并通过 CAS 推进撤销动作。
 * @status : create
 */
@Service
public class DefaultVoidChannelResultTransactionService implements VoidChannelResultTransactionService {

    /**
     * transaction Record Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 创建 DefaultVoidChannelResultTransactionService 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param transactionRecordService transaction Record Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public DefaultVoidChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
/**
 * 写入或更新 record Void Channel Result 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param preparationResultDTO preparation Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 * @param invokeResultDTO invoke Result DTO 输入值，含义由调用方法名称和所属业务对象限定
 */
    public void recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                        PaymentChannelInvokeResultDTO invokeResultDTO) {
        if (preparationResultDTO == null || preparationResultDTO.getResultDTO() == null) {
            return;
        }
        PaymentCreateResultDTO resultDTO = preparationResultDTO.getResultDTO();
        TransactionOperationDO operationDO = transactionRecordService.findSourceOperationByTransactionId(resultDTO.getTransactionId());
        if (operationDO == null) {
            return;
        }
        transactionRecordService.completeVoidChannelResult(
                operationDO,
                preparationResultDTO.getSourceOrderDO(),
                preparationResultDTO.getCommandDTO(),
                preparationResultDTO.getRouteResultDTO(),
                invokeResultDTO,
                resultDTO,
                preparationResultDTO.getCurrencyExponent());
    }
}
