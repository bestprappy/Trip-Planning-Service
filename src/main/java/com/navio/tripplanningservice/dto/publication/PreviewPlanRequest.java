package com.navio.tripplanningservice.dto.publication;

/**
 * Ask for the exact object a recipient would receive under these options.
 *
 * <p>The preview runs the same {@code PlanPublicationSanitizer} as publication,
 * against the same saved trip. If the preview and the published result could
 * diverge, the preview would be a reassurance rather than a check.
 */
public record PreviewPlanRequest(PublicationOptions options) {
    public PublicationOptions safeOptions() {
        return PublicationOptions.orNone(options);
    }
}
