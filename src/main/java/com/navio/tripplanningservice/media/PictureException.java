package com.navio.tripplanningservice.media;

import org.springframework.http.HttpStatus;

/** A rejected or missing trip picture, carrying the status the caller should see. */
public class PictureException extends RuntimeException {

    private final HttpStatus status;

    public PictureException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static PictureException invalid(String message) {
        return new PictureException(HttpStatus.BAD_REQUEST, message);
    }

    public static PictureException tooLarge() {
        return new PictureException(HttpStatus.PAYLOAD_TOO_LARGE, "Picture must be at most 5 MiB");
    }
}
