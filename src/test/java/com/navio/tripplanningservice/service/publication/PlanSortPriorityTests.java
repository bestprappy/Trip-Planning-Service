package com.navio.tripplanningservice.service.publication;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlanSortPriorityTests {
 @Test void secondaryViewsPrecedeImplicitRatingCountAndPrioritiesCanReverse(){
  var f=new SearchTestFixtures();var a=f.row("a","Garden","Bangkok","Thailand",2);var b=f.row("b","Garden","Bangkok","Thailand",2);var c=f.row("c","Garden","Bangkok","Thailand",2);
  a.setRatingSum(50L);a.setRatingCount(10L);a.setViewCount(5L);
  b.setRatingSum(5L);b.setRatingCount(1L);b.setViewCount(20L);
  c.setRatingSum(4L);c.setRatingCount(1L);c.setViewCount(100L);f.rows(a,b,c);
  assertEquals(List.of("b","a","c"),f.service.search("","TH",null,0,6,"rating,views").matching().stream().map(p->p.token()).toList());
  assertEquals(List.of("c","b","a"),f.service.search("","TH",null,0,6,"views,rating").matching().stream().map(p->p.token()).toList());
  assertNotEquals(f.service.search("","TH",null,0,6,"rating,views").searchAssist().fingerprint(),f.service.search("","TH",null,0,6,"views,rating").searchAssist().fingerprint());
 }
 @Test void recentCanBeSecondaryAndPaginationHasStableTies(){
  var f=new SearchTestFixtures();var a=f.row("a","Garden","Bangkok","Thailand",2);var b=f.row("b","Garden","Bangkok","Thailand",2);a.setViewCount(5L);b.setViewCount(5L);b.setListedAt(Instant.EPOCH.plusSeconds(1));f.rows(a,b);
  assertEquals("b",f.service.search("","TH",null,0,1,"views,recent").matching().getFirst().token());
  assertEquals("a",f.service.search("","TH",null,1,1,"views,recent").matching().getFirst().token());
 }
 @Test void malformedOrDuplicateSortPrioritiesAreRejected(){
  var f=new SearchTestFixtures();f.rows();for(String sort:List.of("views,views","views,","relevance,views","views,invalid",",views"))assertThrows(IllegalArgumentException.class,()->f.service.search("","",null,0,6,sort));
 }
}
