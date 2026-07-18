package com.powerload.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreatePrewarningTicketRequest {
    @NotBlank
    @Size(max = 500)
    private String summary;

    @NotBlank
    private String riskLevel;

    @NotNull
    private LocalDateTime forecastTime;

    @NotNull
    private Float expectedLoad;
}
