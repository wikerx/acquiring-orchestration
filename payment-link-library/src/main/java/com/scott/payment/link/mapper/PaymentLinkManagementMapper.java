package com.scott.payment.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.link.entity.PaymentLinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 管理写入仅触及链接规则；交易幂等与会话只读，不修改任何资金事实。 */
public interface PaymentLinkManagementMapper extends BaseMapper<PaymentLinkDO> {
    /** 在调用方事务中锁定链接行，串行执行停用与入口资格判断。 */
    @Select("SELECT * FROM payment_link WHERE link_id = #{linkId} FOR UPDATE")
    PaymentLinkDO lock(@Param("linkId") String linkId);
    /** 锁定同一商户创建请求的既有记录，供唯一键竞争后核对指纹。 */
    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND create_request_id = #{requestId} FOR UPDATE")
    PaymentLinkDO findRequestForUpdate(@Param("merchantId") String merchantId, @Param("requestId") String requestId);
    /** 检查商户订单是否已有核心幂等流程，避免把同一订单重新发布为链接。 */
    @Select("SELECT COUNT(*) FROM transaction_idempotency WHERE idempotency_scope = 'MERCHANT_ORDER_FLOW' AND idempotency_key = #{flowKey} AND deleted = 0")
    long countOrderFlow(@Param("flowKey") String flowKey);
    /** 检查商户订单是否已有收银台会话，防止链接创建占用既有订单。 */
    @Select("SELECT COUNT(*) FROM payment_checkout_session WHERE merchant_id = #{merchantId} AND merchant_order_no = #{orderNo} AND deleted = 0")
    long countSession(@Param("merchantId") String merchantId, @Param("orderNo") String orderNo);
    /** 从链接绑定的收银台会话读取支付状态，不把链接入口状态当作交易结果。 */
    @Select("SELECT s.checkout_status FROM payment_link_checkout_binding b JOIN payment_checkout_session s ON s.checkout_session_id = b.checkout_session_id AND s.deleted = 0 WHERE b.link_id = #{linkId}")
    String paymentStatus(@Param("linkId") String linkId);
}
