package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/** Raw queries, fixed manual labels and controlled publications; not a self-labelled parser test. */
class SmartSearchJudgedTests extends SearchTestFixtures {
 record Fixture(String id,String title,String country,String type,String name,int dayCount,String date,boolean optional,boolean listed) {}
 record Judgement(String id,String query,List<String> matching,List<String> relevant) {}
 final ObjectMapper json=new ObjectMapper();
 <T> List<T> fixtures(String name,Class<T> type){try(var input=getClass().getResourceAsStream("/search/"+name+".json")){return json.readValue(input,json.getTypeFactory().constructCollectionType(List.class,type));}catch(Exception e){throw new IllegalStateException(e);}}
 @TestFactory Stream<DynamicTest> judgedRawQueries(){
  var publications=publications();
  return Stream.of("development","validation","holdout").flatMap(split->fixtures(split,Judgement.class).stream().map(j->DynamicTest.dynamicTest(split+":"+j.id()+" "+j.query(),()->{
   rows(publications.toArray(com.navio.tripplanningservice.model.TripPublication[]::new));
   var result=service.search(j.query(),"",null,0,48,"relevance");
   assertThat(result.matching()).extracting(p->p.token()).containsExactlyInAnyOrderElementsOf(j.matching());
   assertThat(result.related()).extracting(p->p.token()).containsExactlyInAnyOrderElementsOf(j.relevant());
   assertThat(result.matchingTotal()).isEqualTo(j.matching().size());assertThat(result.relatedTotal()).isEqualTo(j.relevant().size());
  })));
 }
 List<com.navio.tripplanningservice.model.TripPublication> publications(){
  return fixtures("publications",Fixture.class).stream().map(f->{
   var r=withPlaces(row(f.id(),f.title(),f.country().equals("Thailand")?"Bangkok":f.country().equals("Japan")?"Tokyo":"Paris",f.country(),f.dayCount()),f.type(),f.name(),f.country());
   if(f.date()!=null)r.setSnapshot(r.getSnapshot().replace("\"days\":[{","\"included\":{\"includeDates\":true},\"days\":[{\"date\":\""+f.date()+"\","));
   if(f.optional())r.setSnapshot(r.getSnapshot().replace("\"days\":","\"lists\":"));r.setListedInExplore(f.listed());return r;
  }).toList();
 }
 @Test void heldOutRetrievalMetricsUseFixedManualLabels(){
  rows(publications().toArray(com.navio.tripplanningservice.model.TripPublication[]::new));
  double matchingPrecision=0,relevantPrecision=0,recall=0,ndcg=0;int matchingQueries=0,relevantQueries=0,positiveQueries=0,rewrites=0,validRewrites=0;
  var holdout=fixtures("holdout",Judgement.class);
  for(var j:holdout){
   var result=service.search(j.query(),"",null,0,48,"relevance");
   for(var suggestion:result.searchAssist().suggestions())if(suggestion.kind().equals("rewrite")){
    rewrites++;var rewritten=service.search(suggestion.action().query(),"",null,0,48,"relevance");
    if(new HashSet<>(rewritten.matching().stream().map(p->p.token()).toList()).equals(new HashSet<>(j.matching()))&&new HashSet<>(rewritten.related().stream().map(p->p.token()).toList()).equals(new HashSet<>(j.relevant())))validRewrites++;
   }
   var matching=result.matching().stream().limit(5).map(p->p.token()).toList();
   var relevant=result.related().stream().limit(5).map(p->p.token()).toList();
   if(!matching.isEmpty()){matchingPrecision+=matching.stream().filter(j.matching()::contains).count()/(double)matching.size();matchingQueries++;}
   if(!relevant.isEmpty()){relevantPrecision+=relevant.stream().filter(j.relevant()::contains).count()/(double)relevant.size();relevantQueries++;}
   var labels=new HashMap<String,Integer>();j.matching().forEach(id->labels.put(id,2));j.relevant().forEach(id->labels.put(id,1));
   if(labels.isEmpty())continue;
   var retrieved=Stream.concat(result.matching().stream(),result.related().stream()).limit(10).map(p->p.token()).toList();
   recall+=retrieved.stream().filter(labels::containsKey).distinct().count()/(double)labels.size();
   var ideal=labels.values().stream().sorted(Comparator.reverseOrder()).limit(10).toList();double actualGain=0,idealGain=0;
   for(int i=0;i<retrieved.size();i++)actualGain+=(Math.pow(2,labels.getOrDefault(retrieved.get(i),0))-1)/(Math.log(i+2)/Math.log(2));
   for(int i=0;i<ideal.size();i++)idealGain+=(Math.pow(2,ideal.get(i))-1)/(Math.log(i+2)/Math.log(2));
   ndcg+=actualGain/idealGain;positiveQueries++;
  }
  assertThat(matchingQueries).isPositive();assertThat(relevantQueries).isPositive();assertThat(positiveQueries).isPositive();
  assertThat(matchingPrecision/matchingQueries).isGreaterThanOrEqualTo(.95);assertThat(relevantPrecision/relevantQueries).isGreaterThanOrEqualTo(.9);
  assertThat(recall/positiveQueries).isGreaterThanOrEqualTo(.9);assertThat(ndcg/positiveQueries).isGreaterThanOrEqualTo(.9);
  assertThat(rewrites).isPositive();assertThat(validRewrites/(double)rewrites).isGreaterThanOrEqualTo(.95);
  System.out.printf(Locale.ROOT,"SMART_SEARCH_HOLDOUT queries=%d positive=%d matchingP5=%.3f relevantP5=%.3f recall10=%.3f nDCG10=%.3f%n",holdout.size(),positiveQueries,matchingPrecision/matchingQueries,relevantPrecision/relevantQueries,recall/positiveQueries,ndcg/positiveQueries);
  System.out.printf(Locale.ROOT,"SMART_SEARCH_REWRITES evaluated=%d retrievalPreserving=%d%n",rewrites,validRewrites);
 }
}
