package com.powerload.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTicketRequest {
    @NotBlank @Size(max = 500)
    private String summary;

    private Long assigneeUserId;
}
