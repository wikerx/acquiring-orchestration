package com.sinopay.payment.component.job.model;

public class JobExecuteResult {

    private boolean success;
    private String message;

    public static JobExecuteResult success() {
        JobExecuteResult result = new JobExecuteResult();
        result.setSuccess(true);
        result.setMessage("success");
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

