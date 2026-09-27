package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.ExplorePlanSummary;
import com.navio.tripplanningservice.dto.publication.SharedPlanResponse;
import com.navio.tripplanningservice.dto.TripResponse;
import com.navio.tripplanningservice.service.publication.TripPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public reads and an authenticated copy action for shared plans.
 *
 * <p>Read-only. {@code /{token}} returns that token's frozen sanitised snapshot.
 * The collection route lists only plans whose owners separately opted into
 * Explore — an unlisted link can never be enumerated through it, because the
 * listing query selects on that opt-in. Copying requires a gateway supplied
 * user identity and creates a new private plan.
 *
 * <p>This controller is deliberately separate from {@link TripPublicationController}
 * rather than an extra method on it, so the gateway rule that permits anonymous
 * access can name GET methods exactly; POST is handled by the gateway's
 * authenticated fallback rule.
 */
@RestController
@RequestMapping("/v1/shared-plans")
@RequiredArgsConstructor
public class SharedPlanController {

    /** Caps an anonymous caller's page size so the feed cannot be pulled in one request. */
    static final int MAX_PAGE_SIZE = 48;

    private final TripPublicationService publicationService;

    /**
     * The Explore feed. Short {@code no-store} for the same reason as a single
     * plan: unlisting has to take effect on the next load.
     */
    @GetMapping
    public ResponseEntity<Page<ExplorePlanSummary>> listExplorePlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "recent") String sort) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body("trending".equals(sort)
                        ? publicationService.listExplorePlans(pageable, true)
                        : publicationService.listExplorePlans(pageable));
    }

    /**
     * {@code no-store}, not {@code no-cache}: revoking a link has to take effect
     * on the next request, and a shared plan sitting in a CDN, a corporate proxy
     * or a browser's disk cache outlives the owner's decision to stop sharing.
     */
    @GetMapping("/{token}")
    public ResponseEntity<SharedPlanResponse> getSharedPlan(@PathVariable String token) {
        SharedPlanResponse response = publicationService.readSharedPlan(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .header("X-Robots-Tag", "noindex, nofollow, noarchive")
                .header("Referrer-Policy", "no-referrer")
                .body(response);
    }

    /** Authenticated copy of a listed, still published plan into the caller's account. */
    @PostMapping("/{token}/copies")
    public ResponseEntity<TripResponse> copySharedPlan(
            @RequestHeader(name = "X-User-Id") UUID userId,
            @PathVariable String token) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(publicationService.copySharedPlan(token, userId));
    }
}
