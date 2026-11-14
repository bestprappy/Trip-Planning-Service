package com.navio.tripplanningservice;

import com.navio.tripplanningservice.dto.publication.PlanFeedback.CommentRequest;
import com.navio.tripplanningservice.dto.publication.PlanFeedback.Reason;
import com.navio.tripplanningservice.dto.publication.PlanFeedback.ReportRequest;
import com.navio.tripplanningservice.repository.PlanCommentReportRepository;
import com.navio.tripplanningservice.repository.PlanFeedbackRepository;
import com.navio.tripplanningservice.service.publication.PlanCommentReportModerationService;
import com.navio.tripplanningservice.service.publication.PlanFeedbackService;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest(properties = {
        "spring.config.name=reader-postgres-test", "spring.cloud.config.enabled=false",
        "spring.datasource.url=${NAVIO_TEST_DB_URL}", "spring.datasource.username=${NAVIO_TEST_DB_USERNAME:navio_verify}",
        "spring.datasource.password=${NAVIO_TEST_DB_PASSWORD:navio_verify}", "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration", "spring.flyway.schemas=trip",
        "spring.flyway.default-schema=trip", "spring.flyway.create-schemas=true", "spring.jpa.hibernate.ddl-auto=validate"
})
@org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase(replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.context.annotation.Import({PlanFeedbackService.class, PlanFeedbackRepository.class,
        PlanCommentReportRepository.class, PlanCommentReportModerationService.class})
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "NAVIO_TEST_DB_URL", matches = ".+")
class PlanCommentReportPostgresTests {

    @Autowired PlanFeedbackService feedback;
    @Autowired PlanCommentReportModerationService moderation;
    @Autowired JdbcClient db;

    UUID owner, author, reporter, otherReporter, moderator, trip, publication;
    String token;

    @BeforeEach
    void seed() {
        owner = UUID.randomUUID(); author = UUID.randomUUID(); reporter = UUID.randomUUID(); otherReporter = UUID.randomUUID();
        moderator = UUID.randomUUID(); trip = UUID.randomUUID(); publication = UUID.randomUUID(); token = UUID.randomUUID().toString();
        db.sql("INSERT INTO trip.trip(id,user_id,display_name,start_date,end_date,destination_id,destination_name) VALUES(:id,:user,'Private name','2027-01-01','2027-01-02','test','Test')")
                .param("id", trip).param("user", owner).update();
        db.sql("INSERT INTO trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at) VALUES(:id,:trip,:token,'{\"title\":\"Kyoto in spring\"}'::jsonb,:version,0,true,now())")
                .param("id", publication).param("trip", trip).param("token", token).param("version", PlanPublicationSanitizer.SANITIZER_VERSION).update();
    }

    @AfterEach
    void cleanup() {
        db.sql("DELETE FROM trip.trip WHERE id=:id").param("id", trip).update();
    }

    private UUID reportedComment() {
        feedback.add(token, author, new CommentRequest("Buy cheap followers", "Author", null));
        UUID comment = feedback.comments(token, author, null, 0).content().getFirst().id();
        feedback.report(token, reporter, comment, new ReportRequest(Reason.SPAM));
        feedback.report(token, otherReporter, comment, new ReportRequest(Reason.HARASSMENT));
        return comment;
    }

    @Test
    void moderatorsSeeAReportedPlanCommentWithItsPublishedTitle() {
        UUID comment = reportedComment();

        var page = moderation.queue("MODERATOR", 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        var row = page.getContent().getFirst();
        assertThat(row.commentId()).isEqualTo(comment);
        assertThat(row.planToken()).isEqualTo(token);
        assertThat(row.planTitle()).isEqualTo("Kyoto in spring");
        assertThat(row.authorAccountId()).isEqualTo(author);
        assertThat(row.reportCount()).isEqualTo(2);
        assertThat(row.reasons()).containsExactly("HARASSMENT", "SPAM");
    }

    @Test
    void aRevokedPlanKeepsItsReportsVisibleWithoutALink() {
        reportedComment();
        db.sql("UPDATE trip.trip_publication SET status='REVOKED', token=NULL, listed_in_explore=false, revoked_at=now() WHERE id=:id").param("id", publication).update();

        var row = moderation.queue("ADMIN", 0, 20).getContent().getFirst();

        assertThat(row.planToken()).isNull();
        assertThat(row.planTitle()).isEqualTo("Kyoto in spring");
    }

    @Test
    void dismissingKeepsTheCommentAndALaterReportBringsItBack() {
        UUID comment = reportedComment();

        moderation.resolve("ADMIN", moderator, comment, "dismiss");

        assertThat(moderation.queue("ADMIN", 0, 20).getTotalElements()).isZero();
        assertThat(feedback.comments(token, author, null, 0).content().getFirst().deleted()).isFalse();
        feedback.report(token, reporter, comment, new ReportRequest(Reason.OTHER));
        assertThat(moderation.queue("ADMIN", 0, 20).getContent()).singleElement()
                .satisfies(row -> assertThat(row.reportCount()).isEqualTo(1));
    }

    @Test
    void removingHidesTheCommentForEveryoneAndUpdatesTheCount() {
        UUID comment = reportedComment();
        assertThat(feedback.summary(token, null).commentCount()).isEqualTo(1);

        moderation.resolve("OWNER", moderator, comment, "remove");

        assertThat(moderation.queue("OWNER", 0, 20).getTotalElements()).isZero();
        assertThat(feedback.comments(token, reporter, null, 0).content().getFirst().deleted()).isTrue();
        assertThat(feedback.summary(token, null).commentCount()).isZero();
        assertThatThrownBy(() -> moderation.resolve("OWNER", moderator, comment, "remove"))
                .isInstanceOfSatisfying(PlanFeedbackService.FeedbackException.class, error -> assertThat(error.status()).isEqualTo(404));
    }
}
