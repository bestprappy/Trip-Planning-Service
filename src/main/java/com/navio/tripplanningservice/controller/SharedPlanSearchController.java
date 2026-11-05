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
 @RequestParam(required=false) Integer days,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size,@RequestParam(defaultValue="relevance") String sort,@RequestParam(required=false) String catalogueRevision,@RequestParam(defaultValue="") String collections,@RequestParam(defaultValue="") String themes){
  try{
   if(catalogueRevision!=null&&!catalogueRevision.matches("[a-f0-9]{64}"))throw new IllegalArgumentException();
   var result=collections.isBlank()&&themes.isBlank()?search.search(term,country,days,page,size,sort):search.search(term,country,days,page,size,sort,collections,themes);
   if(page>0&&catalogueRevision!=null&&result!=null&&result.searchAssist()!=null&&!catalogueRevision.equals(result.searchAssist().catalogueRevision()))return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(Map.of("message","Published plans changed. Refresh your search results."));
   return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
  }
  catch(IllegalArgumentException invalid){return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message","Invalid search parameters"));}
 }
}
