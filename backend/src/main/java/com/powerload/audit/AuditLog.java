package com.powerload.audit;

import java.lang.annotation.*;

/** 标记需要审计的重要写操作 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    String module();
    String action();
}
