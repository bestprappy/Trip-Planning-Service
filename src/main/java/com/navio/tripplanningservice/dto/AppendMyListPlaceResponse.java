package com.navio.tripplanningservice.dto;

public record AppendMyListPlaceResponse(String blockId, String itemId, long version, boolean alreadyAdded) {}
