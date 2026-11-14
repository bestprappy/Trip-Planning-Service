package com.navio.tripplanningservice.controller;

import com.navio.tripplanningservice.dto.publication.PlanFeedback.*;
import com.navio.tripplanningservice.service.publication.PlanFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/shared-plans/{token}/feedback")
public class PlanFeedbackController {
    private final PlanFeedbackService feedback;
    private <T> ResponseEntity<T> read(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<Summary> summary(@PathVariable String token,@RequestHeader(value="X-User-Id",required=false) UUID user) {
        return read(feedback.summary(token,user));
    }
    @PostMapping("/view") public ResponseEntity<Void> view(@PathVariable String token,@RequestHeader(value="X-User-Id",required=false) UUID user,@Valid @RequestBody ViewRequest request) {
        feedback.recordView(token,user,request.readId());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/rating") public ResponseEntity<Summary> rate(@PathVariable String token,@RequestHeader("X-User-Id") UUID user,@Valid @RequestBody RatingRequest request) {
        return read(feedback.rate(token,user,request.score()));
    }
    @DeleteMapping("/rating") public ResponseEntity<Summary> unrate(@PathVariable String token,@RequestHeader("X-User-Id") UUID user) {
        return read(feedback.rate(token,user,null));
    }
    @GetMapping("/comments") public ResponseEntity<Comments> comments(@PathVariable String token,@RequestHeader(value="X-User-Id",required=false) UUID user,
            @RequestParam(required=false) UUID parentId,@RequestParam(defaultValue="0") int page) {
        return read(feedback.comments(token,user,parentId,page));
    }
    @PostMapping("/comments") public ResponseEntity<Void> add(@PathVariable String token,@RequestHeader("X-User-Id") UUID user,@Valid @RequestBody CommentRequest request) {
        feedback.add(token,user,request);return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).build();
    }
    @PutMapping("/comments/{id}") public ResponseEntity<Void> edit(@PathVariable String token,@RequestHeader("X-User-Id") UUID user,@PathVariable UUID id,@Valid @RequestBody EditRequest request) {
        feedback.edit(token,user,id,request);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/comments/{id}") public ResponseEntity<Void> delete(@PathVariable String token,@RequestHeader("X-User-Id") UUID user,@PathVariable UUID id) {
        feedback.delete(token,user,id);return ResponseEntity.noContent().build();
    }
    @PostMapping("/comments/{id}/reports") public ResponseEntity<Void> report(@PathVariable String token,@RequestHeader("X-User-Id") UUID user,@PathVariable UUID id,@Valid @RequestBody ReportRequest request) {
        feedback.report(token,user,id,request);return ResponseEntity.noContent().build();
    }
    @GetMapping("/reports") public ResponseEntity<List<Report>> reports(@PathVariable String token,@RequestHeader("X-User-Id") UUID user) {
        return read(feedback.reports(token,user));
    }
}
