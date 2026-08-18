package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentStatusCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutThreeDsReturnCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutCardBinResultDTO;

import java.time.LocalDateTime;

/**
 * Hosted Checkout 内部服务。
 */
public interface PaymentCheckoutService {

    /**
     * 创建或幂等复用收银台会话，并签发新的付款人 URL token。
     *
     * @param commandDTO 已完成 OpenAPI 商户鉴权和请求解密后的创建命令
     * @return 收银台会话号、付款 URL 和幂等命中标识
     */
    PaymentCheckoutSessionCreateResultDTO createSession(PaymentCheckoutSessionCreateCommandDTO commandDTO);

    /**
     * 根据 URL token 摘要查询收银台展示数据；非法、过期或错绑 token 只返回拦截态。
     *
     * @param commandDTO 付款人浏览器查询命令，携带 token 摘要和浏览器安全摘要
     * @return 可渲染的收银台会话视图
     */
    PaymentCheckoutSessionQueryResultDTO querySession(PaymentCheckoutSessionQueryCommandDTO commandDTO);

    /**
     * 提交一次付款尝试，按 attemptRequestId 做会话内幂等，并驱动 3DS 与核心支付状态机。
     *
     * @param commandDTO 付款人卡信息、账单信息和本次尝试幂等号
     * @return 支付结果页状态、3DS 动作或轮询提示
     */
    PaymentCheckoutPaymentResultDTO submitPayment(PaymentCheckoutPaymentSubmitCommandDTO commandDTO);

    /**
     * 查询付款尝试最新展示状态；处理中和 3DS 中状态由前端按该结果继续轮询。
     *
     * @param commandDTO 会话号、可选付款尝试号和 token 摘要
     * @return 当前可展示的付款结果
     */
    PaymentCheckoutPaymentResultDTO queryPaymentStatus(PaymentCheckoutPaymentStatusCommandDTO commandDTO);

    /**
     * 接收收银台 3DS bridge 转发的浏览器回跳，回跳只证明认证页面返回，不代表扣款成功。
     *
     * @param commandDTO 3DS return token 摘要、会话号和付款尝试号
     * @return 回跳后的处理中或拦截结果
     */
    PaymentCheckoutPaymentResultDTO handleThreeDsReturn(PaymentCheckoutThreeDsReturnCommandDTO commandDTO);

    /** 扫描并关闭超过付款期限、尚未进入渠道处理的收银台订单。 */
    int expireDue(LocalDateTime now, int limit);

    /** 按会话 MID 能力解析并校验卡 BIN 品牌。 */
    PaymentCheckoutCardBinResultDTO resolveCardBin(PaymentCheckoutCardBinCommandDTO commandDTO);
}
