package com.scott.payment.payout.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payout Transaction Service Impl，位于 service-payout 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class PayoutTransactionServiceImpl implements PayoutTransactionService {

    /**
     * 平台代付单号前缀。
     */
    private static final String PAYOUT_ORDER_PREFIX = "PO";

    /**
     * 代付已接收状态。
     */
    private static final String STATUS_RECEIVED = "RECEIVED";

    /**
     * 创建代付交易。
     *
     * @param commandDTO 创建代付命令
     * @return 代付创建结果
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param commandDTO 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PayoutCreateResultDTO createPayout(PayoutCreateCommandDTO commandDTO) {
        validateCreateCommand(commandDTO);
        PayoutCreateResultDTO resultDTO = new PayoutCreateResultDTO();
        resultDTO.setPayoutOrderNo(PaymentOrderNoGenerator.nextOrderNo(PAYOUT_ORDER_PREFIX));
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setStatus(STATUS_RECEIVED);
        return resultDTO;
    }

    /**
     * 校验代付创建命令。
     *
     * @param commandDTO 创建代付命令
     */
    private void validateCreateCommand(PayoutCreateCommandDTO commandDTO) {
        if (commandDTO == null
                || !StringUtils.hasText(commandDTO.getMerchantId())
                || !StringUtils.hasText(commandDTO.getMerchantOrderNo())
                || !StringUtils.hasText(commandDTO.getCurrency())
                || commandDTO.getAmount() == null
                || commandDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        if (commandDTO.getTransactionDateTime() == null) {
            commandDTO.setTransactionDateTime(LocalDateTime.now());
        }
    }
}
