package com.ailoveworkout.common;

/** 사용자에게 공개 가능한 상태·코드만 보관하고 내부 예외나 입력 원문을 노출하지 않는다. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static ApiException invalid(String message) {
        return new ApiException(400, "VALIDATION_ERROR", message);
    }
}
