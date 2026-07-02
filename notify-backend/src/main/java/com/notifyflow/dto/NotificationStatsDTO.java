package com.notifyflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated notification statistics")
public class NotificationStatsDTO {

    @Schema(description = "Total notifications sent")
    private long total;

    @Schema(description = "Count per status (DELIVERED, FAILED, PENDING)")
    private Map<String, Long> byStatus;

    @Schema(description = "Count per channel (EMAIL, SMS, PUSH)")
    private Map<String, Long> byChannel;
}
