package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.PlannerItemDto;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Public leg metadata only. Never contains anchors, owner identifiers or saved geometry. */
final class PublishedTransport {
    private static final Set<String> MODES=Set.of("drive","transit","rail","bus","bike","walk");
    private static final Set<String> STATIONS=Set.of("subway_station","train_station","light_rail_station","transit_station");
    private static final Set<String> RAIL_STATIONS=Set.of("subway_station","train_station","light_rail_station");
    private static final Pattern STATION=Pattern.compile("\\b(subway|metro|mrt|bts|train|railway|rail|light rail|transit)\\s+station\\b",Pattern.CASE_INSENSITIVE);
    private static final Pattern RAIL_LABEL=Pattern.compile("\\b(subway|metro|mrt|bts|train|railway|rail|light rail)\\s+station\\b",Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper JSON=new ObjectMapper();
    private PublishedTransport() { }
    static String mode(String value) { return value!=null&&MODES.contains(value)?value:null; }
    static String resolve(PlannerItemDto from,PlannerItemDto to) {
        String explicit=to.arrivalMode()!=null&&to.arrivalMode().isTextual()?mode(to.arrivalMode().asText()):null;
        if(explicit!=null)return explicit;
        if(station(from)&&station(to))return rail(from)&&rail(to)?"rail":"transit";
        return "drive";
    }
    static String type(PlannerItemDto item) { return item.placeType()!=null&&item.placeType().matches("[a-z0-9_]{1,100}")?item.placeType():""; }
    private static boolean station(PlannerItemDto item) {
        return STATIONS.contains(type(item).toLowerCase(Locale.ROOT))||(item.description()!=null&&STATION.matcher(item.description()).find());
    }
    private static boolean rail(PlannerItemDto item) {return RAIL_STATIONS.contains(type(item))||(item.description()!=null&&RAIL_LABEL.matcher(item.description()).find());}
    static boolean transit(String mode) { return "transit".equals(mode)||"rail".equals(mode)||"bus".equals(mode); }
    static String routeKey(String value) {
        if(value==null||value.length()>4096||!value.startsWith("v1:"))return null;
        try {
            JsonNode rides=JSON.readTree(value.substring(3));
            if(rides==null||!rides.isArray()||rides.isEmpty()||rides.size()>16)return null;
            for(JsonNode ride:rides){
                if(!ride.isArray()||ride.size()!=5)return null;
                for(JsonNode field:ride)if(!field.isNull()&&(!field.isTextual()||field.asText().length()>200))return null;
            }
            return value;
        }catch(java.io.IOException exception){return null;}
    }
}
