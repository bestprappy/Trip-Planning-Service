package com.navio.tripplanningservice.controller;
import com.navio.tripplanningservice.service.publication.SharedPlanSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
/** Anonymous read-only search; exact mapping leaves author/pin/publication routes untouched. */
@RestController @RequiredArgsConstructor @RequestMapping("/v1/shared-plans/search")
public class SharedPlanSearchController {
 private final SharedPlanSearchService search;
 @GetMapping public ResponseEntity<?> search(@RequestParam(defaultValue="") String term,@RequestParam(defaultValue="") String country,
 @RequestParam(required=false) Integer days,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size,@RequestParam(defaultValue="relevance") String sort){
  try{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(search.search(term,country,days,page,size,sort));}
  catch(IllegalArgumentException invalid){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message","Invalid search parameters"));}
 }
}
