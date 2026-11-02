package com.navio.tripplanningservice.controller;
import com.navio.tripplanningservice.service.publication.SharedPlanSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
@WebMvcTest(SharedPlanSearchController.class)
class SharedPlanSearchControllerTests {
 @Autowired MockMvc mvc;
 @MockitoBean SharedPlanSearchService service;
 @Test void anonymousSearchIsReadOnlyAndNeverCached() throws Exception {
  when(service.search("Japan","JP",4,0,12,"relevance")).thenReturn(new SharedPlanSearchService.SearchPage(List.of(),List.of(),List.of(),List.of(),0,true,0,0));
  mvc.perform(get("/v1/shared-plans/search").param("term","Japan").param("country","JP").param("days","4"))
   .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store"))).andExpect(jsonPath("$.matchedCount").value(0));
  verify(service).search("Japan","JP",4,0,12,"relevance");
 }
 @Test void invalidSearchIsBadRequestWithoutEchoingTheInput() throws Exception {
  when(service.search(anyString(),anyString(),nullable(Integer.class),anyInt(),anyInt(),anyString())).thenThrow(new IllegalArgumentException("private details"));
  mvc.perform(get("/v1/shared-plans/search").param("page","99999")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid search parameters"));
 }
 @Test void malformedNumbersAreRejectedBeforeCallingTheService() throws Exception {
  mvc.perform(get("/v1/shared-plans/search").param("days","oops")).andExpect(status().isBadRequest());verifyNoInteractions(service);
 }
 @Test void writesAreNotAllowed() throws Exception {mvc.perform(post("/v1/shared-plans/search")).andExpect(status().isMethodNotAllowed());verifyNoInteractions(service);}
}
