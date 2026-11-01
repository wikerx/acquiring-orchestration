package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端在线用户分页响应。
 */
@Data
public class OnlineUserPageResponse {

    /**
     * 当前页在线用户会话。
     */
    private List<OnlineUserResponse> records = new ArrayList<>();

    /**
     * 满足查询条件的在线会话总数。
     */
    private Long total;
}
