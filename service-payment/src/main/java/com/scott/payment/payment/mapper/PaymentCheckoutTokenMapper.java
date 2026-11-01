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

    /**
     * 按不可逆令牌摘要查询有效或历史令牌记录。
     *
     * @param tokenHash 不透明访问令牌摘要
     * @return 未删除的令牌记录；不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM payment_checkout_token
            WHERE token_hash = #{tokenHash}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutTokenDO selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 原子记录一次有效令牌使用。
     *
     * <p>SQL 同时校验 ACTIVE 状态和可选有效期。有效期为空表示令牌在未被撤销前可持续查询订单结果，
     * 不代表允许继续发起支付。</p>
     *
     * @param tokenHash     不透明访问令牌摘要
     * @param clientIpHash  客户端 IP 摘要
     * @param userAgentHash User-Agent 摘要
     * @param now           本次访问时间
     * @return 更新行数，1 表示成功，0 表示令牌无效或已过期
     */
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
              AND (expire_time IS NULL OR expire_time > #{now})
              AND deleted = 0
            """)
    int markUsed(@Param("tokenHash") String tokenHash,
                 @Param("clientIpHash") String clientIpHash,
                 @Param("userAgentHash") String userAgentHash,
                 @Param("now") LocalDateTime now);

    /**
     * 按当前状态和版本号 CAS 更新令牌状态。
     *
     * @param checkoutTokenId 服务端令牌记录号
     * @param currentStatus   期望当前状态
     * @param nextStatus      目标状态
     * @param reasonCode      状态变更原因编码
     * @param version         期望乐观锁版本
     * @param now             状态变更时间
     * @return 更新行数，0 表示状态或版本已变化
     */
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
