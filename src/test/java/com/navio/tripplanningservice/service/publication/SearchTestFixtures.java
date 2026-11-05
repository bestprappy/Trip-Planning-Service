package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.repository.*;
import com.navio.tripplanningservice.model.TripPublication;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

/** Shared data builders only; avoids re-running inherited @Test methods in new suites. */
class SearchTestFixtures {
 final ExploreSearchRepository repository=mock(ExploreSearchRepository.class);
 final SharedPlanCopyRepository copies=mock(SharedPlanCopyRepository.class);
 final SharedPlanSearchService service=new SharedPlanSearchService(repository,new ObjectMapper().findAndRegisterModules(),new ExplorePlanSummarizer(),copies);
 TripPublication row(String token,String title,String city,String country,int days){
  return TripPublication.builder().id(UUID.randomUUID()).token(token).status(TripPublication.PublicationStatus.ACTIVE).listedInExplore(true)
   .sanitizerVersion(PlanPublicationSanitizer.SANITIZER_VERSION).listedAt(Instant.EPOCH).updatedAt(Instant.EPOCH)
   .snapshot("{\"sanitizerVersion\":"+PlanPublicationSanitizer.SANITIZER_VERSION+",\"title\":\""+title+"\",\"destinationCity\":\""+city+"\",\"destinationCountry\":\""+country+"\",\"dayCount\":"+days+",\"days\":[]}").build();
 }
 void rows(TripPublication... rows){when(repository.streamListed(anyInt())).thenAnswer(i->Arrays.stream(rows));}
 TripPublication withPlaces(TripPublication value,String type,String name,String address){value.setSnapshot(value.getSnapshot().replace("\"days\":[]","\"days\":[{\"items\":[{\"type\":\"place\",\"placeType\":\""+type+"\",\"name\":\""+name+"\",\"address\":\""+address+"\"}]}]"));return value;}
 TripPublication dated(TripPublication row,int day,boolean shared){row.setSnapshot(row.getSnapshot().replace("\"days\":[]","\"included\":{\"includeDates\":"+shared+"},\"days\":[{\"date\":\"2026-12-"+String.format("%02d",day)+"\",\"items\":[]}]"));return row;}
}
