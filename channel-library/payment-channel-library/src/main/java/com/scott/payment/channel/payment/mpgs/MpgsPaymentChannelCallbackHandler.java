package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackHandler
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道回调处理器，位于 payment-channel-library 渠道实现层，负责解析 MPGS 回调中的 order.id、transaction.id、result 和收单响应码。
 * @status : create
 */
@Component
public class MpgsPaymentChannelCallbackHandler implements PaymentChannelCallbackHandler {

    /**
     * trade Status Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MpgsTradeStatusMapper tradeStatusMapper;

    /**
     * error Code Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MpgsErrorCodeMapper errorCodeMapper;

    /**
     * 创建 MPGS 回调处理器。
     */
    public MpgsPaymentChannelCallbackHandler() {
        this(new MpgsTradeStatusMapper(), new MpgsErrorCodeMapper());
    }

    /**
     * 创建 MPGS 回调处理器。
     *
     * @param tradeStatusMapper MPGS 状态映射器
     * @param errorCodeMapper MPGS 错误码映射器
     */
    public MpgsPaymentChannelCallbackHandler(MpgsTradeStatusMapper tradeStatusMapper,
                                             MpgsErrorCodeMapper errorCodeMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
        this.errorCodeMapper = errorCodeMapper;
    }

    @Override
    public String channelCode() {
        return PaymentChannelCode.MPGS.getCode();
    }

    /**
     * 解析 MPGS 回调。
     *
     * @param request 渠道回调请求
     * @return 渠道回调解析结果
     */
    @Override
    public ChannelCallbackResult handle(ChannelCallbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getBody())) {
            throw new ChannelRequestException("MPGS callback body can not be empty");
        }
        MpgsResponsePayload payload = JsonUtils.parseObject(request.getBody(), MpgsResponsePayload.class);
        if (payload == null) {
            throw new ChannelRequestException("MPGS callback body can not be parsed");
        }
        ChannelCallbackResult result = new ChannelCallbackResult();
        result.setChannelCode(PaymentChannelCode.MPGS.getCode());
        result.setCallbackEventId(callbackEventId(payload));
        result.setChannelOrderNo(payload.getOrder() == null ? null : payload.getOrder().getId());
        result.setChannelTransactionId(payload.getTransaction() == null ? null : payload.getTransaction().getId());
        result.setRawChannelStatus(firstText(payload.getOrder() == null ? null : payload.getOrder().getStatus(), payload.getResult()));
        result.setChannelTradeStatus(tradeStatusMapper.map(payload));
        result.setAmount(parseAmount(payload));
        result.setCurrency(firstText(
                payload.getTransaction() == null ? null : payload.getTransaction().getCurrency(),
                payload.getOrder() == null ? null : payload.getOrder().getCurrency()));
        result.setSignatureValid(true);
        result.setChannelResponseCode(channelResponseCode(payload));
        result.setChannelResponseMessage(errorCodeMapper.responseMessage(payload));
        put(result, "result", payload.getResult());
        put(result, "orderStatus", payload.getOrder() == null ? null : payload.getOrder().getStatus());
        put(result, "transactionType", payload.getTransaction() == null ? null : payload.getTransaction().getType());
        if (payload.getResponse() != null) {
            put(result, "gatewayCode", payload.getResponse().getGatewayCode());
            put(result, "acquirerCode", payload.getResponse().getAcquirerCode());
            put(result, "acquirerMessage", payload.getResponse().getAcquirerMessage());
        }
        return result;
    }

    private String callbackEventId(MpgsResponsePayload payload) {
        String orderId = payload.getOrder() == null ? null : payload.getOrder().getId();
        String transactionId = payload.getTransaction() == null ? null : payload.getTransaction().getId();
        return firstText(transactionId, orderId);
    }

    private String channelResponseCode(MpgsResponsePayload payload) {
        if (payload.getResponse() != null && StringUtils.hasText(payload.getResponse().getAcquirerCode())) {
            return payload.getResponse().getAcquirerCode();
        }
        return errorCodeMapper.responseCode(payload);
    }

    /**
     * 解析 parse Amount 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsPaymentChannelCallbackHandler 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param payload payload 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal parseAmount(MpgsResponsePayload payload) {
        if (payload.getTransaction() != null && payload.getTransaction().getAmount() != null) {
            return payload.getTransaction().getAmount();
        }
        return payload.getOrder() == null ? null : payload.getOrder().getAmount();
    }

    /**
     * 完成 put 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsPaymentChannelCallbackHandler 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     */
    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
        }
    }

    /**
     * 完成 first Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsPaymentChannelCallbackHandler 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
