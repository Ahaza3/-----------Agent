package com.powerload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String module;
    private String action;
    private String requestMethod;
    private String requestPath;
    private String result;
    private String ipAddress;
    private Long durationMs;
    private String detail;
    private LocalDateTime createdAt;
}
