package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.dto.*;
import com.navio.tripplanningservice.integration.mobility.MobilityPlaceClient;
import com.navio.tripplanningservice.model.*;
import com.navio.tripplanningservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import static com.navio.tripplanningservice.support.BlockItemTextLimits.*;

/** Appends only one resolved place; never rewrites a planner snapshot. */
@Service
@RequiredArgsConstructor
public class MyListPlaceService {
    private final TripRepository trips;
    private final ListBlockRepository blocks;
    private final BlockItemRepository items;
    private final MobilityPlaceClient places;

    @Transactional
    public AppendMyListPlaceResponse append(UUID tripId, UUID userId, AppendMyListPlaceRequest request) {
        Trip trip = trips.findOwnedForUpdate(tripId, userId)
                .orElseThrow(() -> new TripService.TripNotFoundException(tripId));
        List<ListBlock> all = blocks.findByTripIdOrderByDisplayOrder(tripId);
        List<ListBlock> lists = all.stream().filter(b -> b.getType() == ListBlock.ListBlockType.LIST).toList();
        ListBlock selected;
        if (request.listBlockId() != null) {
            selected = lists.stream().filter(b -> request.listBlockId().equals(b.getClientId())).findFirst()
                    .orElseThrow(() -> invalid("Choose a My List belonging to this trip"));
        } else if (lists.size() > 1) {
            throw invalid("Choose a My List before adding this place");
        } else {
            selected = lists.isEmpty() ? null : lists.getFirst();
        }
        List<BlockItem> existing = selected == null ? List.of() : items.findByBlockIdOrderByDisplayOrder(selected.getId());
        for (BlockItem item : existing) {
            if (item.getType() == BlockItem.BlockItemType.PLACE && request.providerPlaceId().equals(item.getPlaceId())) {
                return new AppendMyListPlaceResponse(selected.getClientId(), item.getClientId(), trip.getVersion(), true);
            }
        }
        if (!Objects.equals(request.expectedVersion(), trip.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Trip.class, tripId);
        }
        var detail = places.getDetail(request.providerPlaceId());
        if (detail == null || detail.name() == null || detail.name().isBlank() || detail.location() == null
                || !validCoordinate(detail.location().lat(), 90) || !validCoordinate(detail.location().lng(), 180)) {
            throw invalid("The place has no valid name or map location");
        }
        if (selected == null) {
            selected = blocks.save(ListBlock.builder().tripId(tripId).clientId(UUID.randomUUID().toString())
                    .name("My List").type(ListBlock.ListBlockType.LIST).blockDate(trip.getStartDate()).blockColor("teal")
                    .displayOrder(all.stream().map(ListBlock::getDisplayOrder).filter(Objects::nonNull)
                            .max(Integer::compareTo).orElse(-1) + 1).build());
        }
        String clientId = UUID.randomUUID().toString();
        items.save(BlockItem.builder().blockId(selected.getId()).clientId(clientId)
                .type(BlockItem.BlockItemType.PLACE).placeId(request.providerPlaceId())
                .title(clamp(detail.name(), TITLE)).placeName(clamp(detail.name(), NAME)).placeAddress("")
                .placeLat(detail.location().lat()).placeLng(detail.location().lng()).visited(false)
                .displayOrder(existing.stream().map(BlockItem::getDisplayOrder).filter(Objects::nonNull)
                        .max(Integer::compareTo).orElse(-1) + 1).build());
        trip.setUpdatedAt(Instant.now());
        Trip saved = trips.saveAndFlush(trip);
        return new AppendMyListPlaceResponse(selected.getClientId(), clientId, saved.getVersion(), false);
    }

    private static boolean validCoordinate(Double value, double bound) {
        return value != null && Double.isFinite(value) && Math.abs(value) <= bound;
    }

    private static PlannerService.PlannerValidationException invalid(String message) {
        return new PlannerService.PlannerValidationException(message);
    }
}
