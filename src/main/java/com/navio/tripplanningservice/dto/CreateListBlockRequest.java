package com.navio.tripplanningservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateListBlockRequest {

    @NotBlank(message = "Block name is required")
    private String name;

    @NotNull(message = "Block type is required")
    private String type;

    private Integer displayOrder;

    private String blockColor;
}
