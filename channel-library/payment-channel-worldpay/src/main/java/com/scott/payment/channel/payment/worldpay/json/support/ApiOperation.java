package com.scott.payment.channel.payment.worldpay.json.support;

/**
 * 定义 Worldpay JSON 请求和日志使用的稳定操作名称。
 */
public final class ApiOperation {

    /**
     * 支付操作，表示持卡人一次性授权并请款的渠道交易。
     */
    public static final String PAYMENT = "PAYMENT";

    /**
     * 授权操作，表示只冻结额度、不立即请款的渠道交易。
     */
    public static final String AUTHORIZE = "AUTHORIZE";

    /**
     * 预授权操作，平台语义与授权一致，但保留 PRE_AUTHORIZE 便于渠道侧和后台排查区分。
     */
    public static final String PRE_AUTHORIZE = "PRE_AUTHORIZE";

    /**
     * 请款操作，表示对既有授权或支付订单发起资金捕获。
     */
    public static final String CAPTURE = "CAPTURE";

    /**
     * 退款操作，表示对既有支付或请款发起退还资金。
     */
    public static final String REFUND = "REFUND";

    /**
     * 撤销操作，表示取消未完成或可撤销的授权、支付动作。
     */
    public static final String VOID = "VOID";

    /**
     * 查询操作，表示按渠道订单号和渠道交易号查询交易状态。
     */
    public static final String QUERY = "QUERY";

    /**
     * 禁止实例化常量类。
     */
    private ApiOperation() {
    }
}
