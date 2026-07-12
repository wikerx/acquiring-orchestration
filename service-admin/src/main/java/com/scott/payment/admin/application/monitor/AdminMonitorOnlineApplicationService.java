package com.scott.payment.admin.application.monitor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorOnlineApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台在线用户监控应用服务
 * @status : create
 *
 * <p>负责管理后台在线用户监控用例编排，统一处理登录会话分页查询、账户与用户信息补充、
 * 以及强制下线等后台监控动作。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorOnlineApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Admin Monitor Online Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMonitorOnlineApplicationService {

    /**
     * 在线会话数据访问组件。
     */
    private final SysLoginSessionMapper sysLoginSessionMapper;

    /**
     * 账号数据访问组件。
     */
    private final SysAccountMapper sysAccountMapper;

    /**
     * 用户数据访问组件。
     */
    private final SysUserMapper sysUserMapper;

    /**
     * 创建在线用户监控应用服务。
     *
     * @param sysLoginSessionMapper 登录会话 Mapper
     * @param sysAccountMapper      账号 Mapper
     * @param sysUserMapper         用户 Mapper
     */
    public AdminMonitorOnlineApplicationService(SysLoginSessionMapper sysLoginSessionMapper,
                                                SysAccountMapper sysAccountMapper,
                                                SysUserMapper sysUserMapper) {
        this.sysLoginSessionMapper = sysLoginSessionMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 分页查询当前在线用户。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 在线用户分页信息
     */
    /**
     * 查询监控治理列表或分页数据，供页面筛选和展示使用。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public Map<String, Object> pageOnlineUsers(int pageNo, int pageSize) {
        LambdaQueryWrapper<SysLoginSessionDO> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysLoginSessionDO::getLogout, 0);
        queryWrapper.orderByDesc(SysLoginSessionDO::getCreatedAt);

        Page<SysLoginSessionDO> page = sysLoginSessionMapper.selectPage(new Page<>(pageNo, pageSize), queryWrapper);
        List<Long> accountIds = page.getRecords().stream().map(SysLoginSessionDO::getAccountId).distinct().toList();
        List<Long> userIds = page.getRecords().stream().map(SysLoginSessionDO::getUserId).distinct().toList();

        Map<Long, String> accountNameMap = accountIds.isEmpty() ? Map.of()
                : sysAccountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(SysAccountDO::getId, SysAccountDO::getLoginAccount, (left, right) -> left));
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUserDO::getId, SysUserDO::getRealName, (left, right) -> left));

        List<Map<String, Object>> records = page.getRecords().stream().map(session -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sessionId", String.valueOf(session.getId()));
            row.put("userName", userNameMap.getOrDefault(session.getUserId(),
                    accountNameMap.getOrDefault(session.getAccountId(), "-")));
            row.put("loginIp", session.getLoginIp());
            row.put("loginTime", session.getCreatedAt());
            row.put("userAgent", session.getUserAgent());
            return row;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        return result;
    }

    /**
     * 强制下线指定登录会话。
     *
     * @param sessionId 会话主键
     */
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @param sessionId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void forceLogout(Long sessionId) {
        SysLoginSessionDO session = sysLoginSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "会话不存在");
        }
        session.setLogout(1);
        session.setLogoutAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sysLoginSessionMapper.updateById(session);
    }
}
