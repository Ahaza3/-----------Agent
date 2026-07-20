package com.powerload.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketResolveRequest {
    @NotBlank(message = "处理结果说明不能为空")
    private String resolution;
}
