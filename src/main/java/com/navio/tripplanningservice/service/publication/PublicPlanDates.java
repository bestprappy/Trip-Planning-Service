package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navio.tripplanningservice.dto.publication.PublicPlanSnapshot;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Projects legacy frozen dates into yearless dates plus relative spacing. No database rewrite. */
final class PublicPlanDates {
    private PublicPlanDates() {}
    static PublicPlanSnapshot read(ObjectMapper mapper, String json) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (!(mapper.readTree(json) instanceof ObjectNode root)) throw com.fasterxml.jackson.databind.exc.MismatchedInputException.from((com.fasterxml.jackson.core.JsonParser)null,PublicPlanSnapshot.class,"Expected a public plan object");
        LocalDate start = iso(root.path("startDate"));
        if(start==null) for(JsonNode day:root.path("days")) { start=iso(day.path("date"));if(start!=null)break; }
        boolean hidden=root.path("included").has("includeDates")&&!root.path("included").path("includeDates").asBoolean();
        LocalDate end=iso(root.path("endDate"));
        if(!hidden&&start!=null&&end!=null)root.put("daySpan",ChronoUnit.DAYS.between(start,end));
        for(String collection:new String[]{"days","lists"}) for(JsonNode node:root.path(collection)) {
            if(!(node instanceof ObjectNode day))continue;
            LocalDate date=iso(day.path("date"));
            LocalDate generatedTitle=iso(day.path("title"));
            if(generatedTitle!=null){if(hidden)day.remove("title");else day.put("title",java.time.MonthDay.from(generatedTitle).toString());}
            if(hidden&&day.path("title").asText().matches("(?:--)?\\d{2}-\\d{2}"))day.remove("title");
            if(!hidden&&start!=null&&date!=null)day.put("dayOffset",ChronoUnit.DAYS.between(start,date));
            if(hidden){day.remove("date");day.remove("dayOffset");}
        }
        if(hidden){root.remove("startDate");root.remove("endDate");root.remove("daySpan");}
        return mapper.treeToValue(root,PublicPlanSnapshot.class);
    }
    private static LocalDate iso(JsonNode node) {
        try{return node.isTextual()&&node.asText().matches("\\d{4}-\\d{2}-\\d{2}")?LocalDate.parse(node.asText()):null;}
        catch(RuntimeException invalid){return null;}
    }
}
