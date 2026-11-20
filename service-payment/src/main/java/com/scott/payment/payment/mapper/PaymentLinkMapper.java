package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.link.entity.PaymentLinkDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 链接写入归支付域，列表只读既有会话/尝试事实且必须携带服务端数据范围。 */
public interface PaymentLinkMapper extends BaseMapper<PaymentLinkDO> {
    /** 在调用方事务中锁定链接行，使停用与新付款准备串行校验入口资格。 */
    @Select("SELECT * FROM payment_link WHERE link_id = #{linkId} FOR UPDATE")
    PaymentLinkDO lock(@Param("linkId") String linkId);

    /** 按商户及订单号锁定链接，校验长效收银台会话的冻结事实归属。 */
    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND merchant_order_no = #{orderNo} FOR UPDATE")
    PaymentLinkDO lockMerchantOrder(@Param("merchantId") String merchantId, @Param("orderNo") String orderNo);

    /** 锁定同一商户创建请求的记录，供唯一键竞争后的幂等参数核对。 */
    @Select("SELECT * FROM payment_link WHERE merchant_id = #{merchantId} AND create_request_id = #{requestId} FOR UPDATE")
    PaymentLinkDO findRequestForUpdate(@Param("merchantId") String merchantId, @Param("requestId") String requestId);

    /** 按操作者的数据范围和统一时点计算有效状态，再分页读取链接投影。 */
    List<View> search(@Param("actor") Actor actor, @Param("query") Query query,
                      @Param("now") LocalDateTime now, @Param("offset") long offset);
    /** 使用与列表相同的数据范围、筛选条件和状态时点统计总数。 */
    long count(@Param("actor") Actor actor, @Param("query") Query query, @Param("now") LocalDateTime now);
    /** 按币种分组汇总链接状态与成功金额，禁止跨币种直接相加。 */
    List<Aggregate> summarize(@Param("actor") Actor actor, @Param("query") Query query,
                                        @Param("now") LocalDateTime now);
    /** 按链接读取付款尝试投影，系统订单关联仅来自真实交易事实。 */
    List<Attempt> attempts(@Param("linkId") String linkId, @Param("offset") long offset,
                           @Param("size") int size);
    /** 分页读取链接与收款会话的关联，不触发新的付款动作。 */
    List<Collection> collections(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    /** 统计链接已有收款会话关联，用于会话列表分页。 */
    long countCollections(@Param("linkId") String linkId);
    /** 统计指定链接下的真实付款尝试数，不以成功订单数替代。 */
    long countAttempts(@Param("linkId") String linkId);
    /** 按统一查询时点分页投影链接生命周期及支付事件。 */
    List<Event> timeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now,
                         @Param("offset") long offset, @Param("size") int size);
    /** 按与时间线相同的查询时点统计事件总数。 */
    long countTimeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now);
}
