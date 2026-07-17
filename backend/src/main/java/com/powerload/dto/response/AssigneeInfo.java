package com.powerload.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可指派的运维人员 — 最小必要信息，不返回密码/邮箱等字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssigneeInfo {
    private Long id;
    private String displayName;
    private String username;
    private boolean active;
}
