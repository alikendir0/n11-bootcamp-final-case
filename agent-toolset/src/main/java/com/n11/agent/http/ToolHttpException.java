package com.n11.agent.http;

public class ToolHttpException extends RuntimeException {
    private final String code;

    public ToolHttpException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}
