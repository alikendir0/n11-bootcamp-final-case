package com.n11.ai.interfaces.sse;

public final class SseEvents {
    public static final String DELTA = "delta";
    public static final String TOOL_CALL = "tool_call";
    public static final String TOOL_RESULT = "tool_result";
    public static final String DONE = "done";
    public static final String ERROR = "error";
    private SseEvents() {}
}
