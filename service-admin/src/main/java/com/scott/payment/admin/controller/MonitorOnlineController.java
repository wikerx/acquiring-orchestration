package com.scott.payment.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.CommonResult;
import static com.scott.payment.component.core.model.CommonResult.success;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorOnlineController
 * @date : 2026-06-12 17:31
 * @email : scott_x@163.com
 * @description : 在线用户监控控制器，查询当前活跃登录会话并支持强制下线
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor")
public class MonitorOnlineController {

    /**
     * 登录会话 Mapper。
     */
    @Resource
    private SysLoginSessionMapper sysLoginSessionMapper;

    /**
     * 登录账号 Mapper。
     */
    @Resource
    private SysAccountMapper sysAccountMapper;

    /**
     * 用户主体 Mapper。
     */
    @Resource
    private SysUserMapper sysUserMapper;

    /**
     * 查询在线用户列表。
     *
     * <p>返回当前未退出的登录会话，包含用户名称、登录 IP、登录时间等信息。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param loginIp  登录 IP 过滤条件（可选）
     * @param userName 用户名称过滤条件（可选）
     * @return 分页的在线用户列表
     */
    @GetMapping("/online/list")
    @RequiresPermission("system:online:list")
    public CommonResult<Map<String, Object>> onlineList(
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "loginIp", required = false) String loginIp,
            @RequestParam(value = "userName", required = false) String userName) {

        LambdaQueryWrapper<SysLoginSessionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysLoginSessionDO::getLogout, 0);
        wrapper.orderByDesc(SysLoginSessionDO::getCreatedAt);

        Page<SysLoginSessionDO> page = sysLoginSessionMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);

        // 批量查询账号名称和用户姓名
        List<Long> accountIds = page.getRecords().stream()
                .map(SysLoginSessionDO::getAccountId)
                .distinct().toList();
        List<Long> userIds = page.getRecords().stream()
                .map(SysLoginSessionDO::getUserId)
                .distinct().toList();

        Map<Long, String> accountNameMap = accountIds.isEmpty() ? Map.of()
                : sysAccountMapper.selectBatchIds(accountIds).stream()
                .collect(Collectors.toMap(SysAccountDO::getId, SysAccountDO::getLoginAccount, (a, b) -> a));
        Map<Long, String> userNameMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUserDO::getId, SysUserDO::getRealName, (a, b) -> a));

        // 组装返回数据
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

        return success(result);
    }

    /**
     * 强制下线指定会话。
     *
     * @param sessionId 会话主键 ID
     * @return 空响应
     */
    @DeleteMapping("/online/{sessionId}")
    @RequiresPermission("system:online:forceLogout")
    public CommonResult<Void> forceLogout(@PathVariable("sessionId") Long sessionId) {
        SysLoginSessionDO session = sysLoginSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new com.scott.payment.component.core.exception.ServiceException(
                    com.scott.payment.component.core.enums.ApiResultEnum.NOT_FOUND.getCode(), "会话不存在");
        }
        session.setLogout(1);
        session.setLogoutAt(java.time.LocalDateTime.now());
        session.setUpdatedAt(java.time.LocalDateTime.now());
        sysLoginSessionMapper.updateById(session);
        return success(null);
    }
}
