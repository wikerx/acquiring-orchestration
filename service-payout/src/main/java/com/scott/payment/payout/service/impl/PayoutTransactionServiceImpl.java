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
 * 代付交易服务模拟实现。
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
