package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCheckoutTokenDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Hosted Checkout URL token Mapper。
 */
public interface PaymentCheckoutTokenMapper extends BaseMapper<PaymentCheckoutTokenDO> {

    @Select("""
            SELECT *
            FROM payment_checkout_token
            WHERE token_hash = #{tokenHash}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutTokenDO selectByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE payment_checkout_token
            SET first_used_time = COALESCE(first_used_time, #{now}),
                last_used_time = #{now},
                use_count = use_count + 1,
                last_client_ip_hash = #{clientIpHash},
                last_user_agent_hash = #{userAgentHash},
                version = version + 1,
                update_time = #{now}
            WHERE token_hash = #{tokenHash}
              AND token_status = 'ACTIVE'
              AND expire_time > #{now}
              AND deleted = 0
            """)
    int markUsed(@Param("tokenHash") String tokenHash,
                 @Param("clientIpHash") String clientIpHash,
                 @Param("userAgentHash") String userAgentHash,
                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_token
            SET token_status = #{nextStatus},
                revoked_time = #{now},
                revoke_reason_code = #{reasonCode},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_token_id = #{checkoutTokenId}
              AND token_status = #{currentStatus}
              AND version = #{version}
              AND deleted = 0
            """)
    int updateStatusCas(@Param("checkoutTokenId") String checkoutTokenId,
                        @Param("currentStatus") String currentStatus,
                        @Param("nextStatus") String nextStatus,
                        @Param("reasonCode") String reasonCode,
                        @Param("version") Integer version,
                        @Param("now") LocalDateTime now);
}
