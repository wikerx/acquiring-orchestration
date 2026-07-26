package com.scott.payment.payout.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payout.api.internal.dto.PayoutCreateCommandDTO;
import com.scott.payment.payout.api.internal.dto.PayoutCreateResultDTO;
import com.scott.payment.payout.domain.state.PayoutTransactionStatusEnum;
import com.scott.payment.payout.service.PayoutTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutTransactionServiceImpl
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutTransactionServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 代付服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PayoutTransactionServiceImpl implements PayoutTransactionService {

    /**
     * 平台代付单号前缀。
     */
    private static final String PAYOUT_ORDER_PREFIX = "PO";

    /**
     * 创建代付交易；当前骨架实现只生成平台单号并返回 RECEIVED 状态。
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
        resultDTO.setStatus(PayoutTransactionStatusEnum.RECEIVED.getCode());
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
