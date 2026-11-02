package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.repository.ExploreSearchRepository;
import com.navio.tripplanningservice.repository.SharedPlanCopyRepository;
import com.navio.tripplanningservice.model.TripPublication;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedPlanSearchServiceTests {
 final ExploreSearchRepository repository=mock(ExploreSearchRepository.class);
 final SharedPlanCopyRepository copies=mock(SharedPlanCopyRepository.class);
 final SharedPlanSearchService service=new SharedPlanSearchService(repository,new ObjectMapper().findAndRegisterModules(),new ExplorePlanSummarizer(),copies);
 TripPublication row(String token,String title,String city,String country,int days){
  return TripPublication.builder().id(UUID.randomUUID()).token(token).status(TripPublication.PublicationStatus.ACTIVE).listedInExplore(true)
   .sanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION).listedAt(Instant.EPOCH).updatedAt(Instant.EPOCH)
   .snapshot("{\"sanitizerVersion\":"+PlanPublicationSanitizer.SANITIZER_VERSION+",\"title\":\""+title+"\",\"destinationCity\":\""+city+"\",\"destinationCountry\":\""+country+"\",\"dayCount\":"+days+",\"days\":[]}").build();
 }
 void rows(TripPublication... rows){when(repository.streamListed(anyInt())).thenAnswer(i->Arrays.stream(rows));}
 @Test void countryMatchesEvenWhenTitleDoesNotContainItsName(){
  rows(row("a","Quiet weekend","Kyoto","Japan",4));
  var result=service.search("Japan","",null,0,12,"relevance");
  assertThat(result.matchedCount()).isEqualTo(1);assertThat(result.recommendations()).extracting(p->p.token()).containsExactly("a");
 }
 @Test void countryDurationAndTokensAllApply(){
  rows(row("a","Kyoto temples","Kyoto","Japan",4),row("b","Kyoto temples","Kyoto","Japan",5),row("c","Kyoto temples","Kyoto","Thailand",4));
  assertThat(service.search("Kyoto temples","JP",4,0,12,"relevance").matchedCount()).isEqualTo(1);
 }
 @Test void partialTokensAliasesAndAccentsAreSupported(){
  rows(row("a","Ban riverside","Bangkok","Thailand",3));
  assertThat(service.search("ban","",null,0,12,"relevance").matchedCount()).isEqualTo(1);
  rows(row("b","Harbour walk","New York","United States",2));
  assertThat(service.search("USA","US",null,0,12,"relevance").matchedCount()).isEqualTo(1);
  rows(row("c","Café walk","Paris","France",2));
  assertThat(service.search("cafe","",null,0,12,"relevance").matchedCount()).isEqualTo(1);
 }
 @Test void privateRevokedStaleAndUnreadableSnapshotsNeverLeak(){
  var privateRow=row("secret","Secret","Kyoto","Japan",4);privateRow.setListedInExplore(false);
  var stale=row("old","Old","Kyoto","Japan",4);stale.setSanitizerVersion(0);
  var revoked=row("revoked","Revoked","Kyoto","Japan",4);revoked.setStatus(TripPublication.PublicationStatus.REVOKED);
  var broken=row("broken","Broken","Kyoto","Japan",4);broken.setSnapshot("not json");
  rows(privateRow,stale,revoked,broken);
  var result=service.search("Japan","",null,0,12,"relevance");assertThat(result.matchedCount()).isZero();assertThat(result.plans()).isEmpty();
  verifyNoInteractions(copies);
 }
 @Test void scansBeyondFirstPageAndNeverDuplicatesRecommendations(){
  var values=new ArrayList<TripPublication>();for(int i=0;i<35;i++)values.add(row(String.format("%02d",i),"Japan temples "+i,"Kyoto","Japan",4));
  rows(values.toArray(TripPublication[]::new));var first=service.search("Japan","",null,0,12,"relevance");
  var second=service.search("Japan","",null,1,12,"relevance");
  assertThat(first.matchedCount()).isEqualTo(35);assertThat(first.totalElements()).isEqualTo(29);
  assertThat(second.content()).hasSize(12);assertThat(second.recommendations()).isEmpty();
  assertThat(first.content()).doesNotContainAnyElementsOf(first.recommendations());
  assertThat(second.content()).doesNotContainAnyElementsOf(first.content());
 }
 @Test void suggestionsAreRealAndDoNotReadPrivateNotes(){
  rows(row("a","Bangkok Trip","Bangkok","Thailand",3));var result=service.search("ban","",null,0,12,"relevance");
  assertThat(result.destinations()).anyMatch(d->d.label().equals("Bangkok"));assertThat(result.plans()).extracting(p->p.title()).contains("Bangkok Trip");
 }
}
