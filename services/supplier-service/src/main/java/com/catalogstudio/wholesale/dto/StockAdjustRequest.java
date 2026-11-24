package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** Send {@code onHand} (absolute) or {@code delta} (relative). Optionally update {@code price}. */
public record StockAdjustRequest(Integer onHand, Integer delta, @DecimalMin("0.00") BigDecimal price) {}