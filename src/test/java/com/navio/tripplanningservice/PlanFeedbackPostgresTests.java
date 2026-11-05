package com.navio.tripplanningservice;

import com.navio.tripplanningservice.dto.publication.PlanFeedback.*;
import com.navio.tripplanningservice.service.publication.PlanFeedbackService;
import com.navio.tripplanningservice.service.publication.PlanPublicationSanitizer;
import com.navio.tripplanningservice.service.publication.TripPublicationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named="NAVIO_TEST_DB_URL",matches=".+")
class PlanFeedbackPostgresTests {
    @Autowired PlanFeedbackService service;
    @Autowired JdbcClient db;
    UUID owner,user,other,trip,publication;
    String token;
    @BeforeEach void seed(){
        owner=UUID.randomUUID();user=UUID.randomUUID();other=UUID.randomUUID();trip=UUID.randomUUID();publication=UUID.randomUUID();token=UUID.randomUUID().toString();
        db.sql("INSERT INTO trip.trip(id,user_id,display_name,start_date,end_date,destination_id,destination_name) VALUES(:id,:user,'Feedback test','2027-01-01','2027-01-02','test','Test')")
            .param("id",trip).param("user",owner).update();
        db.sql("INSERT INTO trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at) VALUES(:id,:trip,:token,'{}'::jsonb,:version,0,true,now())")
            .param("id",publication).param("trip",trip).param("token",token).param("version",PlanPublicationSanitizer.SANITIZER_VERSION).update();
    }
    @AfterEach void cleanup(){db.sql("DELETE FROM trip.trip WHERE id=:id").param("id",trip).update();}
    @Test void ratingsPersistUpdateAndRemoveWithoutChangingTrip(){
        assertThat(service.summary(token,null).ratingAverage()).isNull();
        service.rate(token,user,5);service.rate(token,user,3);service.rate(token,other,5);
        var summary=service.summary(token,user);assertThat(summary.ratingCount()).isEqualTo(2);assertThat(summary.ratingAverage()).isEqualTo(4);assertThat(summary.myRating()).isEqualTo(3);
        service.rate(token,user,null);service.rate(token,user,null);
        assertThat(service.summary(token,null).ratingCount()).isEqualTo(1);
        assertThat(service.summary(token,null).ratingAverage()).isEqualTo(5);
        assertThat(db.sql("SELECT version FROM trip.trip WHERE id=:id").param("id",trip).query(Long.class).single()).isZero();
    }
    @Test void concurrentRatingsDoNotLoseOrDoubleCountVotes() throws Exception {
        try(var executor=Executors.newFixedThreadPool(6)){
            List<Callable<Summary>> jobs=new ArrayList<>();for(int i=0;i<24;i++){final int score=1+i%5;jobs.add(()->service.rate(token,user,score));}
            for(var future:executor.invokeAll(jobs))future.get(20,TimeUnit.SECONDS);
        }
        var actual=service.summary(token,user);assertThat(actual.ratingCount()).isEqualTo(1);assertThat(actual.ratingAverage()).isEqualTo(actual.myRating().doubleValue());
    }
    @Test void ownershipAndVisibilityCannotBeBypassed(){
        assertThatThrownBy(()->service.rate(token,owner,5)).isInstanceOf(PlanFeedbackService.FeedbackException.class).hasMessageContaining("own");
        assertThatThrownBy(()->service.rate(token,null,5)).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        assertThatThrownBy(()->service.rate(token,user,6)).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        db.sql("UPDATE trip.trip_publication SET listed_in_explore=false WHERE id=:id").param("id",publication).update();
        assertThatThrownBy(()->service.summary(token,user)).isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);
        assertThatThrownBy(()->service.rate(token,user,5)).isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);
        db.sql("UPDATE trip.trip_publication SET listed_in_explore=true,sanitizer_version=0 WHERE id=:id").param("id",publication).update();
        assertThatThrownBy(()->service.comments(token,null,null,0)).isInstanceOf(TripPublicationService.SharedPlanUnavailableException.class);
    }
    @Test void commentsRepliesEditsAndTombstonesArePersistentAndScoped(){
        service.add(token,user,new CommentRequest("Hello <script>alert(1)</script>","Traveler",null));
        var root=service.comments(token,user,null,0).content().getFirst();assertThat(root.canEdit()).isTrue();
        assertThat(service.comments(token,other,null,0).content().getFirst().canEdit()).isFalse();
        assertThatThrownBy(()->service.edit(token,other,root.id(),new EditRequest("stolen",0))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        assertThatThrownBy(()->service.delete(token,other,root.id())).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        service.edit(token,user,root.id(),new EditRequest("Updated",0));
        assertThatThrownBy(()->service.edit(token,user,root.id(),new EditRequest("stale",0))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        service.add(token,other,new CommentRequest("Reply",null,root.id()));
        var reply=service.comments(token,other,root.id(),0).content().getFirst();
        assertThatThrownBy(()->service.add(token,user,new CommentRequest("deep",null,reply.id()))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        assertThatThrownBy(()->service.add(token,user,new CommentRequest("bad",null,UUID.randomUUID()))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        service.delete(token,owner,root.id());service.delete(token,owner,root.id());
        var tombstone=service.comments(token,null,null,0).content().getFirst();assertThat(tombstone.deleted()).isTrue();assertThat(tombstone.content()).isNull();assertThat(tombstone.authorName()).isNull();assertThat(tombstone.replyCount()).isEqualTo(1);
        assertThat(service.summary(token,null).commentCount()).isEqualTo(1);
        assertThat(service.comments(token,null,root.id(),0).content()).hasSize(1);
        assertThatThrownBy(()->service.add(token,other,new CommentRequest("closed",null,root.id()))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
    }
    @Test void reportsAreIdempotentAndOnlyPublisherCanReadThem(){
        service.add(token,user,new CommentRequest("Question","Traveler",null));var id=service.comments(token,user,null,0).content().getFirst().id();
        assertThatThrownBy(()->service.report(token,user,id,new ReportRequest(Reason.SPAM))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        service.report(token,other,id,new ReportRequest(Reason.SPAM));service.report(token,other,id,new ReportRequest(Reason.OTHER));
        assertThat(service.reports(token,owner).getFirst().reportCount()).isEqualTo(1);
        assertThatThrownBy(()->service.reports(token,other)).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        service.delete(token,owner,id);assertThat(service.reports(token,owner)).isEmpty();
    }
    @Test void publicCommentPagesAreBounded(){
        for(int i=0;i<23;i++)service.add(token,user,new CommentRequest("Comment "+i,"Traveler",null));
        var first=service.comments(token,null,null,0);var second=service.comments(token,null,null,1);
        assertThat(first.content()).hasSize(20);assertThat(first.hasMore()).isTrue();assertThat(second.content()).hasSize(3);assertThat(second.hasMore()).isFalse();
        assertThat(first.content().stream().map(Comment::id).toList()).doesNotContainAnyElementsOf(second.content().stream().map(Comment::id).toList());
    }
    @Test void anotherPublicationsCommentCannotBeReadEditedRepliedToOrReported(){
        service.add(token,user,new CommentRequest("Original",null,null));
        var id=service.comments(token,user,null,0).content().getFirst().id();
        UUID secondTrip=UUID.randomUUID();String secondToken=UUID.randomUUID().toString();
        try {
            db.sql("INSERT INTO trip.trip(id,user_id,display_name,start_date,end_date,destination_id,destination_name) VALUES(:id,:u,'Other plan','2027-01-01','2027-01-01','other','Other')").param("id",secondTrip).param("u",owner).update();
            db.sql("INSERT INTO trip.trip_publication(id,trip_id,token,snapshot,sanitizer_version,source_trip_version,listed_in_explore,listed_at) VALUES(:id,:trip,:token,'{}'::jsonb,:v,0,true,now())")
                .param("id",UUID.randomUUID()).param("trip",secondTrip).param("token",secondToken).param("v",PlanPublicationSanitizer.SANITIZER_VERSION).update();
            assertThatThrownBy(()->service.comments(secondToken,null,id,0)).isInstanceOf(PlanFeedbackService.FeedbackException.class);
            assertThatThrownBy(()->service.edit(secondToken,user,id,new EditRequest("wrong plan",0))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
            assertThatThrownBy(()->service.add(secondToken,user,new CommentRequest("wrong parent",null,id))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
            assertThatThrownBy(()->service.report(secondToken,other,id,new ReportRequest(Reason.SPAM))).isInstanceOf(PlanFeedbackService.FeedbackException.class);
            assertThatThrownBy(()->service.delete(secondToken,owner,id)).isInstanceOf(PlanFeedbackService.FeedbackException.class);
        } finally { db.sql("DELETE FROM trip.trip WHERE id=:id").param("id",secondTrip).update(); }
    }
}
