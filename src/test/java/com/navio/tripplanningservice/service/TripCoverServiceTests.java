package com.navio.tripplanningservice.service;

import com.navio.tripplanningservice.media.PictureException;
import com.navio.tripplanningservice.media.PictureValidator;
import com.navio.tripplanningservice.model.Trip;
import com.navio.tripplanningservice.repository.TripCoverRepository;
import com.navio.tripplanningservice.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripCoverServiceTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TRIP = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Mock TripRepository trips;
    @Mock TripCoverRepository covers;
    @InjectMocks TripCoverService service;

    static byte[] png(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    static MockMultipartFile file(byte[] bytes, String contentType) {
        return new MockMultipartFile("file", "cover", contentType, bytes);
    }

    @Test
    void storesTheOwnersReEncodedCoverUnderAFreshId() throws IOException {
        when(trips.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(new Trip()));

        var response = service.upload(TRIP, OWNER, file(png(30, 10), "image/png"));

        var stored = ArgumentCaptor.forClass(PictureValidator.Picture.class);
        verify(covers).save(eq(TRIP), eq(response.coverMediaId()), stored.capture());
        assertThat(response.tripId()).isEqualTo(TRIP);
        assertThat(stored.getValue().contentType()).isEqualTo("image/png");
        assertThat(stored.getValue().bytes()).isNotEmpty();
    }

    @Test
    void someoneElsesTripLooksMissingAndNothingIsStored() throws IOException {
        when(trips.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(TRIP, STRANGER, file(png(30, 10), "image/png")))
                .isInstanceOf(TripService.TripNotFoundException.class);
        verifyNoInteractions(covers);
    }

    @Test
    void rejectsAFileThatIsNotAPicture() {
        when(trips.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(new Trip()));

        assertThatThrownBy(() -> service.upload(TRIP, OWNER, file("<svg/>".getBytes(), "image/png")))
                .isInstanceOfSatisfying(PictureException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(covers);
    }

    @Test
    void rejectsAnEmptyUpload() {
        when(trips.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(new Trip()));

        assertThatThrownBy(() -> service.upload(TRIP, OWNER, file(new byte[0], "image/png")))
                .isInstanceOf(PictureException.class)
                .hasMessage("Choose a PNG or JPEG picture");
    }

    @Test
    void rejectsAPictureLargerThanTheSideLimit() throws IOException {
        when(trips.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(new Trip()));

        assertThatThrownBy(() -> service.upload(TRIP, OWNER, file(png(4097, 1), "image/png")))
                .isInstanceOf(PictureException.class)
                .hasMessageContaining("4096");
        verifyNoInteractions(covers);
    }

    @Test
    void removingSomeoneElsesCoverIsRejectedWithoutDeleting() {
        when(trips.findByIdAndUserId(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(TRIP, STRANGER))
                .isInstanceOf(TripService.TripNotFoundException.class);
        verifyNoInteractions(covers);
    }

    @Test
    void ownerCanRemoveTheirCover() {
        when(trips.findByIdAndUserId(TRIP, OWNER)).thenReturn(Optional.of(new Trip()));

        service.remove(TRIP, OWNER);

        verify(covers).delete(TRIP);
    }

    @Test
    void readingAMissingOrForeignCoverIsNotFound() {
        when(covers.findOwned(TRIP, STRANGER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(TRIP, STRANGER))
                .isInstanceOfSatisfying(PictureException.class,
                        ex -> assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void readsTheOwnersStoredPicture() {
        var picture = new PictureValidator.Picture(new byte[] {1, 2, 3}, "image/jpeg");
        when(covers.findOwned(any(), any())).thenReturn(Optional.of(picture));

        assertThat(service.read(TRIP, OWNER)).isSameAs(picture);
    }
}
