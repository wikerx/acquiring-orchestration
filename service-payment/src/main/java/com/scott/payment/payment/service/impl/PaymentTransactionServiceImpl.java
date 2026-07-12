package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionServiceImpl
 * @date : 2026-05-31 21:03
 * @email : scott_x@163.com
 * @description : 收单支付交易服务模拟实现，位于 service-payment 服务实现层，仅承载当前骨架交易受理、金额辅币转换和创建事件发布。
 * @status : create
 */
@Service
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    /**
     * 平台收单订单号前缀。
     */
    private static final String PAYMENT_ORDER_PREFIX = "PA";

    /**
     * 支付创建消息 Tag。
     */
    private static final String PAYMENT_CREATED_TAG = "PAYMENT_CREATED";

    /**
     * MQ 生产者，RocketMQ 未配置时会走 Noop 实现，不影响本地启动。
     */
    private final MqProducer mqProducer;

    /**
     * ISO 币种字典服务，用于按币种默认辅币位转换交易金额。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建收单支付交易服务。
     *
     * @param mqProducer          MQ 生产者
     * @param isoDictionaryService ISO 币种字典服务
     */
    public PaymentTransactionServiceImpl(MqProducer mqProducer, IsoDictionaryService isoDictionaryService) {
        this.mqProducer = mqProducer;
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 创建收单授权交易；当前骨架实现只生成平台单号、返回 RECEIVED 状态并发布创建事件。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    @Override
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        validateCreateCommand(commandDTO);
        String paymentOrderNo = PaymentOrderNoGenerator.nextOrderNo(PAYMENT_ORDER_PREFIX);
        PaymentCreateResultDTO resultDTO = new PaymentCreateResultDTO();
        resultDTO.setPaymentOrderNo(paymentOrderNo);
        resultDTO.setMerchantOrderNo(commandDTO.getMerchantOrderNo());
        resultDTO.setStatus(PaymentTransactionStatusEnum.RECEIVED.getCode());
        resultDTO.setCurrency(commandDTO.getCurrency());
        resultDTO.setAmount(toMinorAmount(commandDTO.getAmount(), commandDTO.getCurrency()));
        publishPaymentCreatedEvent(paymentOrderNo);
        return resultDTO;
    }

    /**
     * 校验创建交易命令。
     *
     * @param commandDTO 创建交易命令
     */
    private void validateCreateCommand(PaymentCreateCommandDTO commandDTO) {
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

    /**
     * 按 ISO 4217 币种默认辅币位把主币种单位金额转换为最小币种单位。
     *
     * @param amount   主币种单位金额
     * @param currency ISO 4217 币种代码或名称
     * @return 最小币种单位金额
     */
    private Long toMinorAmount(BigDecimal amount, String currency) {
        try {
            return isoDictionaryService.toMinorUnit(amount, currency);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 发布支付创建事件。
     *
     * @param paymentOrderNo 平台支付订单号
     */
    private void publishPaymentCreatedEvent(String paymentOrderNo) {
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId(paymentOrderNo);
        message.setCreatedAt(LocalDateTime.now());
        mqProducer.send(MqTopic.PAYMENT_EVENT, PAYMENT_CREATED_TAG, message);
    }
}
