package com.powerload.common;

import lombok.Data;

/**
 * 统一响应封装 — { code, message, data, timestamp }
 *
 * @param <T> 业务数据类型
 */
@Data
public class R<T> {

    /** 成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 响应时间戳（毫秒） */
    private long timestamp;

    private R() {
        this.timestamp = System.currentTimeMillis();
    }

    // ─── 静态工厂方法 ───

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }
}
