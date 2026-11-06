package com.navio.tripplanningservice.service.publication;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Public calendar evidence, never a forecast or inferred private travel year. */
final class PlanDiscovery {
    static final Set<String> SORTS=Set.of("relevance","recent","trending","popular","views","rating","discussed");
    record Filters(Set<String> collections,Set<String> themes) {
        static final Filters NONE=new Filters(Set.of(),Set.of());
        static Filters parse(String collections,String themes){return new Filters(values(collections,Set.of("seasonal","events","trending")),values(themes,Set.of("nature","food","culture","ev")));}
        private static Set<String> values(String raw,Set<String> allowed){
            if(raw==null||raw.isBlank())return Set.of();
            if(raw.length()>100)throw new IllegalArgumentException("Invalid discovery filters");
            var result=new TreeSet<>(Arrays.asList(raw.split(",",-1)));
            if(!allowed.containsAll(result))throw new IllegalArgumentException("Invalid discovery filters");
            return Collections.unmodifiableSet(result);
        }
        boolean accepts(PublicSearchEvidence e,LocalDate today){
            return (collections.isEmpty()||collections.contains("trending")||collections.contains("seasonal")&&seasonal(e,today)||collections.contains("events")&&event(e,today))
                &&(themes.isEmpty()||themes.stream().anyMatch(t->e.strong().contains(t)||t.equals("nature")&&e.strong().contains("scenic")));
        }
        String signature(){return String.join(",",new TreeSet<>(collections))+"|"+String.join(",",new TreeSet<>(themes));}
    }
    static boolean seasonal(PublicSearchEvidence e,LocalDate today){return e.dates().stream().anyMatch(d->d.getMonthValue()==today.getMonthValue());}
    record Occasion(String name,LocalDate date,int before,int after) {}
    // Official HKO Gregorian/lunar tables; unknown years deliberately have no occurrence.
    static final Map<Integer,LocalDate> LUNAR_NEW_YEAR=Map.of(
        2026,LocalDate.of(2026,2,17),2027,LocalDate.of(2027,2,6),2028,LocalDate.of(2028,1,26),
        2029,LocalDate.of(2029,2,13),2030,LocalDate.of(2030,2,3));
    // Chiang Mai provincial tourism notice6839 / Thailand.go.th; do not extrapolate lunar festivals.
    static final Map<Integer,LocalDate> LOY_KRATHONG=Map.of(2026,LocalDate.of(2026,11,24));
    static List<Occasion> upcoming(LocalDate today){
        var result=new ArrayList<Occasion>();
        for(int year=today.getYear()-1;year<=today.getYear()+1;year++){
            var occurrences=new ArrayList<>(List.of(
            new Occasion("christmas",LocalDate.of(year,12,25),7,2),new Occasion("new year",LocalDate.of(year,1,1),6,6),
            new Occasion("easter",easter(year),7,1),new Occasion("halloween",LocalDate.of(year,10,31),7,2)));
            if(LUNAR_NEW_YEAR.containsKey(year))occurrences.add(new Occasion("chinese new year",LUNAR_NEW_YEAR.get(year),7,14));
            if(LOY_KRATHONG.containsKey(year))occurrences.add(new Occasion("loy krathong",LOY_KRATHONG.get(year),3,2));
            for(var event:occurrences){
                long until=ChronoUnit.DAYS.between(today,event.date());
                if(until>=-event.after()&&until<=45)result.add(event);
            }
        }
        return List.copyOf(result);
    }
    private static boolean mentions(PublicSearchEvidence e,Occasion event){
        String text=e.eventText();
        boolean lunar=TravelConcepts.has(text,"chinese new year")||TravelConcepts.has(text,"lunar new year");
        if(event.name().equals("new year")&&lunar)return false;
        if(event.name().equals("easter")&&TravelConcepts.has(text,"orthodox"))return false;
        return TravelConcepts.has(text,event.name())
            ||event.name().equals("christmas")&&TravelConcepts.has(text,"xmas")
            ||event.name().equals("chinese new year")&&lunar
            ||event.name().equals("loy krathong")&&TravelConcepts.has(text,"loi krathong");
    }
    static boolean event(PublicSearchEvidence e,LocalDate today){
        return upcoming(today).stream().anyMatch(event->{
            if(!mentions(e,event))return false;
            if(e.dates().isEmpty())return true;
            for(int offset=-event.before();offset<=event.after();offset++)if(e.dates().contains(MonthDay.from(event.date().plusDays(offset))))return true;
            return false;
        });
    }
    static long eventProximity(PublicSearchEvidence e,LocalDate today){
        if(e.dates().isEmpty())return 366;
        long nearest=366;
        for(var occasion:upcoming(today)){
            if(!mentions(e,occasion))continue;
            for(var date:e.dates())for(int year=occasion.date().getYear()-1;year<=occasion.date().getYear()+1;year++){
                if(!date.isValidYear(year))continue;
                nearest=Math.min(nearest,Math.abs(ChronoUnit.DAYS.between(date.atYear(year),occasion.date())));
            }
        }
        return nearest;
    }
    /** Gregorian (Western) Easter, computed for discovery's current occurrence only. */
    static LocalDate easter(int year){int a=year%19,b=year/100,c=year%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3,h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451,n=h+l-7*m+114;return LocalDate.of(year,n/31,n%31+1);}
}
