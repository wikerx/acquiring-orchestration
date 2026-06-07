package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysLoginLogDTO;
import com.scott.payment.admin.dto.SysLoginLogQueryRequest;
import com.scott.payment.admin.service.AdminLoginLogService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import com.scott.payment.component.db.auth.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminLoginLogServiceImpl
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台登录日志服务实现
 * @status : create
 */
@Service
public class AdminLoginLogServiceImpl implements AdminLoginLogService {

    /**
     * 登录日志 Mapper。
     */
    private final SysLoginLogMapper loginLogMapper;

    /**
     * 创建登录日志服务实现。
     *
     * @param loginLogMapper 登录日志 Mapper
     */
    public AdminLoginLogServiceImpl(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 按条件查询登录日志。
     *
     * @param request 查询条件
     * @return 登录日志分页结果
     */
    @Override
    public PageResult<SysLoginLogDTO> pageLoginLogs(SysLoginLogQueryRequest request) {
        SysLoginLogQueryRequest query = request == null ? new SysLoginLogQueryRequest() : request;
        Page<SysLoginLogDO> page = loginLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildLoginLogQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(this::toLoginLogDTO).toList()
        );
    }

    /**
     * 构建登录日志查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysLoginLogDO> buildLoginLogQueryWrapper(SysLoginLogQueryRequest query) {
        return Wrappers.<SysLoginLogDO>lambdaQuery()
                .eq(query.getAppId() != null, SysLoginLogDO::getAppId, query.getAppId())
                .eq(StringUtils.hasText(query.getLoginIp()), SysLoginLogDO::getLoginIp, query.getLoginIp())
                .eq(StringUtils.hasText(query.getMerchantId()), SysLoginLogDO::getMerchantId, query.getMerchantId())
                .eq(query.getLoginStatus() != null, SysLoginLogDO::getLoginStatus, query.getLoginStatus())
                .likeRight(StringUtils.hasText(query.getLoginAccount()), SysLoginLogDO::getLoginAccount, query.getLoginAccount())
                .ge(query.getLoginStartAt() != null, SysLoginLogDO::getLoginAt, query.getLoginStartAt())
                .le(query.getLoginEndAt() != null, SysLoginLogDO::getLoginAt, query.getLoginEndAt())
                .orderByDesc(SysLoginLogDO::getLoginAt);
    }

    /**
     * 转换登录日志 DTO。
     *
     * @param entity 登录日志实体
     * @return 登录日志 DTO
     */
    private SysLoginLogDTO toLoginLogDTO(SysLoginLogDO entity) {
        SysLoginLogDTO dto = new SysLoginLogDTO();
        dto.setId(entity.getId());
        dto.setAppId(entity.getAppId());
        dto.setAccountId(entity.getAccountId());
        dto.setUserId(entity.getUserId());
        dto.setMerchantId(entity.getMerchantId());
        dto.setLoginAccount(entity.getLoginAccount());
        dto.setLoginIp(entity.getLoginIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setLoginStatus(entity.getLoginStatus());
        dto.setFailReason(entity.getFailReason());
        dto.setLoginAt(entity.getLoginAt());
        return dto;
    }
}
