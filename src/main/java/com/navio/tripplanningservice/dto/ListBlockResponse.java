package com.navio.tripplanningservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListBlockResponse {

    private UUID id;

    private String name;

    private String type;

    private Integer displayOrder;

    private String blockColor;

    private List<BlockItemResponse> items;

    private Instant createdAt;

    private Instant updatedAt;
}
