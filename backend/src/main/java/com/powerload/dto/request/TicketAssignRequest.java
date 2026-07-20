package com.powerload.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketAssignRequest {
    @NotNull
    private Long assigneeUserId;
}
