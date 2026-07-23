package com.powerload.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class TicketFeedbackRequest {
    @NotBlank
    private String alertClassification;

    @NotBlank
    private String rootCauseCode;

    @Size(max = 1000)
    private String rootCauseDetail;

    @NotEmpty
    @Size(max = 20)
    @Valid
    private List<@NotBlank @Size(max = 200) String> actionsTaken;

    @Size(max = 2000)
    private String actionDetail;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal impactLoadMw;

    @NotBlank
    private String effectiveness;
}
