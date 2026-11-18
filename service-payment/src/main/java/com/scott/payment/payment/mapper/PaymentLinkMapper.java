package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.entity.PaymentLinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 链接写入归支付域，列表只读既有会话/尝试事实且必须携带服务端数据范围。 */
public interface PaymentLinkMapper extends BaseMapper<PaymentLinkDO> {
    @Select("SELECT * FROM payment_link WHERE link_id = #{linkId} FOR UPDATE")
    PaymentLinkDO lock(@Param("linkId") String linkId);

    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND merchant_order_no = #{orderNo} FOR UPDATE")
    PaymentLinkDO lockMerchantOrder(@Param("merchantId") String merchantId, @Param("orderNo") String orderNo);

    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND create_request_id = #{requestId} FOR UPDATE")
    PaymentLinkDO findRequestForUpdate(@Param("merchantId") String merchantId, @Param("requestId") String requestId);

    List<View> search(@Param("actor") Actor actor, @Param("query") Query query,
                      @Param("now") LocalDateTime now, @Param("offset") long offset);
    long count(@Param("actor") Actor actor, @Param("query") Query query, @Param("now") LocalDateTime now);
    List<Aggregate> summarize(@Param("actor") Actor actor, @Param("query") Query query,
                                        @Param("now") LocalDateTime now);
    List<Attempt> attempts(@Param("linkId") String linkId, @Param("offset") long offset,
                           @Param("size") int size);
    List<Collection> collections(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    long countCollections(@Param("linkId") String linkId);
    long countAttempts(@Param("linkId") String linkId);
    List<Event> timeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now,
                         @Param("offset") long offset, @Param("size") int size);
    long countTimeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now);
}
