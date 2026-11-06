package com.navio.tripplanningservice.service.publication;
import java.time.MonthDay;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LunarNewYearSearchTests {
 @Test void lunarSearchDoesNotRequireDecemberOrJanuaryAndKeepsItsWords(){
  var query=PlanSearchTemporalIntent.parse("singapore chinese new year");
  assertFalse(query.newYear());
  assertTrue(query.match("singapore chinese new year",List.of(MonthDay.of(2,6))).eligible());
  assertTrue(query.remove("singapore chinese new year").contains("chinese new year"));
 }
 @Test void gregorianSearchDoesNotTreatLunarTextAsJanuaryEvidence(){
  var query=PlanSearchTemporalIntent.parse("new year");
  assertTrue(query.newYear());
  assertFalse(query.match("chinese new year",List.of()).direct());
 }
}
