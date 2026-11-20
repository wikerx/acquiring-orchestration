package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.paymentlink.PaymentLinkDeliveryDO;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.Delivery;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 发送占位锁跨独立邮件事务持有；响应丢失后按稳定 biz_no 找回已提交的邮件。 */
public interface PaymentLinkDeliveryMapper extends BaseMapper<PaymentLinkDeliveryDO> {
    /** 以商户和请求号占位；唯一键竞争时保留已有邀请请求供幂等核对。 */
    @Insert("""
        INSERT IGNORE INTO admin_payment_link_delivery_request
        (request_id, merchant_id, link_id, fingerprint, recipient_hash, recipient_masked, locale, biz_no, created_by, create_time)
        VALUES (#{r.requestId}, #{r.merchantId}, #{r.linkId}, #{r.fingerprint}, #{r.recipientHash},
                #{r.recipientMasked}, #{r.locale}, #{r.bizNo}, #{r.createdBy}, #{r.createTime})
        """)
    int claim(@Param("r") PaymentLinkDeliveryDO row);
    /** 锁定邀请请求，防止并发重试重复生成邮件发送记录。 */
    @Select("SELECT * FROM admin_payment_link_delivery_request WHERE merchant_id=#{merchantId} AND request_id=#{requestId} FOR UPDATE")
    PaymentLinkDeliveryDO lock(@Param("merchantId") String merchantId, @Param("requestId") String requestId);
    /** 按本端应用、商户与稳定业务号找回已提交邮件，恢复响应丢失的发送结果。 */
    @Select("SELECT id FROM msg_email_send_record WHERE app_code='ADMIN' AND merchant_id=#{merchantId} AND biz_type='PAYMENT_LINK' AND biz_no=#{bizNo} ORDER BY id LIMIT 1")
    Long findCommittedEmail(@Param("merchantId") String merchantId, @Param("bizNo") String bizNo);
    /** 在指定商户和链接内分页合并双端邀请记录，只投影脱敏收件地址。 */
    @Select("""
        SELECT d.request_id, d.recipient_masked, d.locale, m.send_status, d.email_record_id,
               d.created_app_code, d.created_by, d.create_time
        FROM (
          SELECT request_id, merchant_id, link_id, recipient_masked, locale, email_record_id,
                 'ADMIN' AS created_app_code, created_by, create_time, id
          FROM admin_payment_link_delivery_request
          UNION ALL
          SELECT request_id, merchant_id, link_id, recipient_masked, locale, email_record_id,
                 'MERCHANT' AS created_app_code, created_by, create_time, id
          FROM merchant_payment_link_delivery_request
        ) d LEFT JOIN msg_email_send_record m ON m.id=d.email_record_id
        WHERE d.merchant_id=#{merchantId} AND d.link_id=#{linkId}
        ORDER BY d.create_time DESC, d.id DESC LIMIT #{size} OFFSET #{offset}
        """)
    List<Delivery> page(@Param("merchantId") String merchantId, @Param("linkId") String linkId,
                         @Param("size") int size, @Param("offset") long offset);
    /** 统计该商户链接的双端邀请总数，与合并分页使用相同归属条件。 */
    @Select("""
        SELECT (SELECT COUNT(*) FROM admin_payment_link_delivery_request WHERE merchant_id=#{merchantId} AND link_id=#{linkId})
             + (SELECT COUNT(*) FROM merchant_payment_link_delivery_request WHERE merchant_id=#{merchantId} AND link_id=#{linkId})
        """)
    long count(@Param("merchantId") String merchantId, @Param("linkId") String linkId);
    /** 按已关联的邮件记录读取发送状态；无发送结果时不推断为成功。 */
    @Select("SELECT send_status FROM msg_email_send_record WHERE id=#{id}")
    Integer status(@Param("id") Long id);
}
