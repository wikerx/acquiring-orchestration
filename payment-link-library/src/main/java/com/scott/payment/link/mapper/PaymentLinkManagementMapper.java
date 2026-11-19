package com.scott.payment.link.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.link.entity.PaymentLinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 管理写入仅触及链接规则；交易幂等与会话只读，不修改任何资金事实。 */
public interface PaymentLinkManagementMapper extends BaseMapper<PaymentLinkDO> {
    @Select("SELECT * FROM payment_link WHERE link_id = #{linkId} FOR UPDATE")
    PaymentLinkDO lock(@Param("linkId") String linkId);
    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND create_request_id = #{requestId} FOR UPDATE")
    PaymentLinkDO findRequestForUpdate(@Param("merchantId") String merchantId, @Param("requestId") String requestId);
    @Select("SELECT COUNT(*) FROM transaction_idempotency WHERE idempotency_scope = 'MERCHANT_ORDER_FLOW' AND idempotency_key = #{flowKey} AND deleted = 0")
    long countOrderFlow(@Param("flowKey") String flowKey);
    @Select("SELECT COUNT(*) FROM payment_checkout_session WHERE merchant_id = #{merchantId} AND merchant_order_no = #{orderNo} AND deleted = 0")
    long countSession(@Param("merchantId") String merchantId, @Param("orderNo") String orderNo);
    @Select("SELECT s.checkout_status FROM payment_link_checkout_binding b JOIN payment_checkout_session s ON s.checkout_session_id = b.checkout_session_id AND s.deleted = 0 WHERE b.link_id = #{linkId}")
    String paymentStatus(@Param("linkId") String linkId);
}
