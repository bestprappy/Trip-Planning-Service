package com.navio.tripplanningservice.service.publication;

import java.time.Month;
import java.time.MonthDay;
import java.util.*;

/** Fixed calendar evidence only; no private year, hemisphere assumptions or inferred movable holidays. */
record PlanSearchTemporalIntent(Set<Integer> months, boolean christmas, boolean newYear, boolean yearRequested) {
    static PlanSearchTemporalIntent parse(String text) {
        var months=new HashSet<Integer>();
        for(Month month:Month.values()) if(PlanSearchQuery.phrase(text,month.name()))months.add(month.getValue());
        return new PlanSearchTemporalIntent(Set.copyOf(months),PlanSearchQuery.phrase(text,"christmas")||PlanSearchQuery.phrase(text,"xmas"),PlanSearchQuery.phrase(text,"new year"),text.matches(".*\\b(?:19|20|21)\\d{2}\\b.*"));
    }
    String remove(String text) {
        for(Month month:Month.values())text=text.replaceAll("\\b"+month.name().toLowerCase(Locale.ROOT)+"\\b"," ");
        return text.replaceAll("\\b(?:christmas|xmas|new year)\\b"," ").replaceAll("\\b(?:19|20|21)\\d{2}\\b"," ");
    }
    record Fit(boolean eligible,boolean direct,int score,String reason) {}
    Fit match(String text,List<MonthDay> dates) {
        if(yearRequested)return new Fit(false,false,0,"Published travel dates do not include a year");
        if(!months.isEmpty()&&!months.stream().allMatch(m->dates.isEmpty()?PlanSearchQuery.phrase(text,Month.of(m).name()):dates.stream().anyMatch(d->d.getMonthValue()==m)))return new Fit(false,false,0,"");
        boolean christmasText=PlanSearchQuery.phrase(text,"christmas")||PlanSearchQuery.phrase(text,"xmas");
        boolean newYearText=PlanSearchQuery.phrase(text,"new year");
        boolean december=dates.stream().anyMatch(d->d.getMonthValue()==12);
        boolean turn=dates.stream().anyMatch(d->d.getMonthValue()==12&&d.getDayOfMonth()>=26||d.getMonthValue()==1&&d.getDayOfMonth()<=7);
        if(christmas&&!christmasText&&!december||newYear&&!newYearText&&!turn)return new Fit(false,false,0,"");
        // Explicit contrary dates do not establish an event, even if a passing word occurs.
        if(christmas&&!dates.isEmpty()&&!december||newYear&&!dates.isEmpty()&&!turn)return new Fit(false,false,0,"");
        boolean direct=(!christmas||christmasText)&&(!newYear||newYearText);
        int proximity=christmas?dates.stream().filter(d->d.getMonthValue()==12).mapToInt(d->Math.max(0,31-Math.abs(25-d.getDayOfMonth()))).max().orElse(0):0;
        String reason=!direct?(christmas?"Public trip dates in December":"Public trip dates around New Year"):"";
        return new Fit(true,direct,proximity,reason);
    }
}
