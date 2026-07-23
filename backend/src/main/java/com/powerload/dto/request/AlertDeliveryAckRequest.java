package com.powerload.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertDeliveryAckRequest {
    private LocalDateTime clientRenderedAt;
    @Size(max = 128)
    private String clientSessionId;
}
