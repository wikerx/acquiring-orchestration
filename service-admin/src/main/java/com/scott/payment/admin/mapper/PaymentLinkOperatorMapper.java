package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Set;

/** 只查询审计页面需要的账号名称，不读取密码、联系方式等认证字段。 */
public interface PaymentLinkOperatorMapper {
    record Operator(String accountId, String appCode, String loginAccount, String realName) { }

    @Select("""
        <script>
        SELECT a.id AS account_id, app.app_code, a.login_account,
               COALESCE(NULLIF(TRIM(mu.real_name), ''), NULLIF(TRIM(u.real_name), '')) AS real_name
        FROM sys_account a
        JOIN sys_app app ON app.id = a.app_id AND app.deleted = 0
        LEFT JOIN sys_merchant_user mu ON app.app_code = 'MERCHANT' AND mu.account_id = a.id
             AND mu.merchant_id = a.merchant_id AND mu.deleted = 0
        LEFT JOIN sys_user u ON u.id = a.user_id AND u.deleted = 0
        WHERE a.deleted = 0 AND app.app_code IN ('ADMIN', 'MERCHANT')
          AND a.id IN
          <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Operator> find(@Param("ids") Set<Long> ids);
}
