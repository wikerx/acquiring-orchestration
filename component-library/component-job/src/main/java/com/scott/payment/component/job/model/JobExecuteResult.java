package com.scott.payment.component.job.model;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecuteResult
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务执行结果对象
 * @status : create
 */
@Data
public class JobExecuteResult {

    /**
     * 任务是否最终执行成功。
     */
    private boolean success;

    /**
     * 异步任务是否只是被调度系统接受而尚未结束。
     */
    private boolean accepted;

    /**
     * 任务执行结果摘要。
     */
    private String message;

    /**
     * 业务失败码。
     */
    private String errorCode;

    /**
     * 失败详情。
     */
    private String errorMessage;

    /**
     * 附加业务结果。
     */
    private Object data;

    /**
     * 构建成功任务执行结果。
     *
     * @param message 成功说明
     * @return 成功任务执行结果
     */
    public static JobExecuteResult success(String message) {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(true);
        result.setAccepted(false);
        result.setMessage(message);
        return result;
    }

    /**
     * 构建默认成功结果。
     *
     * @return 成功任务执行结果
     */
    public static JobExecuteResult success() {
        return success("success");
    }

    /**
     * 构建携带结果数据的成功响应。
     *
     * @param message 成功说明
     * @param data    附加数据
     * @return 成功任务执行结果
     */
    public static JobExecuteResult success(String message, Object data) {
        JobExecuteResult result = success(message);
        result.setData(data);
        return result;
    }

    /**
     * 构建异步任务已受理结果。
     *
     * @param message 受理说明
     * @return 已受理结果
     */
    public static JobExecuteResult accepted(String message) {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(false);
        result.setAccepted(true);
        result.setMessage(message);
        return result;
    }

    /**
     * 构建失败结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误详情
     * @return 失败结果
     */
    public static JobExecuteResult failed(String errorCode, String errorMessage) {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(false);
        result.setAccepted(false);
        result.setMessage(errorMessage);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
