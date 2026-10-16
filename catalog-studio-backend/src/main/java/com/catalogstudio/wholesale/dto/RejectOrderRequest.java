package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.Size;

public record RejectOrderRequest(@Size(max = 1000) String supplierNote) {}