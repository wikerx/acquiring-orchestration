package com.scott.payment.merchant.mapper;

import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

/** 仅提供查询契约；不继承可写 Mapper，不修改支付域事实。 */
public interface PaymentLinkQueryMapper {
    List<View> search(@Param("actor") Actor actor, @Param("query") Query query,
                      @Param("now") LocalDateTime now, @Param("offset") long offset);
    long count(@Param("actor") Actor actor, @Param("query") Query query, @Param("now") LocalDateTime now);
    List<CurrencyAggregate> summarize(@Param("actor") Actor actor, @Param("query") Query query,
                                      @Param("now") LocalDateTime now);
    DetailMetadata detailMetadata(@Param("linkId") String linkId);
    List<Attempt> attempts(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    long countAttempts(@Param("linkId") String linkId);
    List<Collection> collections(@Param("linkId") String linkId, @Param("offset") long offset, @Param("size") int size);
    long countCollections(@Param("linkId") String linkId);
    List<Event> timeline(@Param("linkId") String linkId, @Param("now") LocalDateTime now,
                         @Param("offset") long offset, @Param("size") int size);
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
        @lombok.ToString.Exclude
        private String notifyUrl;
        @lombok.ToString.Exclude
        private String redirectUrl;
    }
}
