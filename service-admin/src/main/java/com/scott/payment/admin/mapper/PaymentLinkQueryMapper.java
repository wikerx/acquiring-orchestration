package com.scott.payment.admin.mapper;

import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** 仅提供查询契约；不继承可写 Mapper，不修改支付域事实。 */
public interface PaymentLinkQueryMapper {
    /** 按操作者的数据范围和统一时点计算有效状态，再分页读取链接投影。 */
    List<View> search(@Param("actor") Actor actor, @Param("query") Query query,
                      @Param("now") LocalDateTime now, @Param("offset") long offset);
    /** 使用与列表相同的数据范围、筛选条件和状态时点统计总数。 */
    long count(@Param("actor") Actor actor, @Param("query") Query query, @Param("now") LocalDateTime now);
    /** 按币种分组汇总链接状态与成功金额，禁止跨币种直接相加。 */
    List<CurrencyAggregate> summarize(@Param("actor") Actor actor, @Param("query") Query query,
                                      @Param("now") LocalDateTime now);
    /** 读取链接归属及冻结规则；调用方必须在返回详情前校验商户范围。 */
    DetailMetadata detailMetadata(@Param("linkId") String linkId);
    /** 按链接读取付款尝试投影，系统订单关联仅来自真实交易事实。 */
    List<Attempt> attempts(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    /** 统计指定链接下的真实付款尝试数，不以成功订单数替代。 */
    long countAttempts(@Param("linkId") String linkId);
    /** 分页读取链接与收款会话的关联，不触发新的付款动作。 */
    List<Collection> collections(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    /** 统计链接已有收款会话关联，用于会话列表分页。 */
    long countCollections(@Param("linkId") String linkId);
    /** 按统一查询时点分页投影链接生命周期及支付事件。 */
    List<Event> timeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now,
                         @Param("offset") long offset, @Param("size") int size);
    /** 按与时间线相同的查询时点统计事件总数。 */
    long countTimeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now);

    @Data
    class CurrencyAggregate {
        private String currency;
        private long total;
        private long active;
        private long completed;
        private long processing;
        private long failedAttempts;
        private java.math.BigDecimal amount;
    }

    @Data
    class DetailMetadata {
        private String merchantId;
        private String allowedBrandsJson;
        private String allowedMethodsJson;
        @lombok.ToString.Exclude
        private String notifyUrl;
        @lombok.ToString.Exclude
        private String redirectUrl;
    }
}
