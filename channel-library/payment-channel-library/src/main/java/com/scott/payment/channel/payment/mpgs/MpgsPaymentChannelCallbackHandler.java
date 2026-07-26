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
     * trade Status Mapper，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 容器构造器注入。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private final MpgsTradeStatusMapper tradeStatusMapper;

    /**
     * error Code Mapper，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 解析parse金额，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 渠道适配库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param payload payload 输入值，参与 payload 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private BigDecimal parseAmount(MpgsResponsePayload payload) {
        if (payload.getTransaction() != null && payload.getTransaction().getAmount() != null) {
            return payload.getTransaction().getAmount();
        }
        return payload.getOrder() == null ? null : payload.getOrder().getAmount();
    }

    /**
     * 整理写入，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void put(ChannelCallbackResult result, String key, String value) {
        if (StringUtils.hasText(value)) {
            result.getExtension().put(key, value);
        }
    }

    /**
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
