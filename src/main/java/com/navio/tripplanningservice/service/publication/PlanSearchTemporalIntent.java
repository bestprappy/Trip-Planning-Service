package com.navio.tripplanningservice.service.publication;

import java.time.Month;
import java.time.MonthDay;
import java.util.*;

/** Fixed calendar evidence only; no private year, hemisphere assumptions or inferred movable holidays. */
record PlanSearchTemporalIntent(Set<Integer> months, boolean christmas, boolean newYear, boolean yearRequested,Set<MonthDay> calendarDays,boolean invalidDate) {
    static PlanSearchTemporalIntent parse(String text) {
        var months=new HashSet<Integer>();var calendar=new HashSet<MonthDay>();boolean invalid=false;
        for(Month month:Month.values())for(String name:names(month)) {
            if(PlanSearchQuery.phrase(text,name))months.add(month.getValue());
            var day=java.util.regex.Pattern.compile("\\b(?:"+name+"\\s+(\\d{1,2})(?!\\d)|(\\d{1,2})\\s+"+name+")\\b").matcher(text);
            while(day.find())try{calendar.add(MonthDay.of(month.getValue(),Integer.parseInt(day.group(1)!=null?day.group(1):day.group(2))));}catch(java.time.DateTimeException malformed){invalid=true;}
        }
        return new PlanSearchTemporalIntent(Set.copyOf(months),PlanSearchQuery.phrase(text,"christmas")||PlanSearchQuery.phrase(text,"xmas"),gregorianNewYear(text),text.matches(".*\\b(?:19|20|21)\\d{2}\\b.*"),Set.copyOf(calendar),invalid);
    }
    private static boolean gregorianNewYear(String text){return PlanSearchQuery.phrase(text,"new year")&&!PlanSearchQuery.phrase(text,"chinese new year")&&!PlanSearchQuery.phrase(text,"lunar new year");}
    String remove(String text) {
        for(Month month:Month.values())for(String name:names(month))text=text.replaceAll("\\b(?:"+name+"\\s+\\d{1,2}(?!\\d)|\\d{1,2}\\s+"+name+")\\b"," ").replaceAll("\\b"+name+"\\b"," ");
        if(newYear)text=text.replaceAll("\\bnew year\\b"," ");
        return text.replaceAll("\\b(?:christmas|xmas)\\b"," ").replaceAll("\\b(?:19|20|21)\\d{2}\\b"," ");
    }
    record Fit(boolean eligible,boolean direct,int score,String reason) {}
    Fit match(String text,List<MonthDay> dates) {
        if(invalidDate)return new Fit(false,false,0,"Invalid calendar date");
        if(yearRequested)return new Fit(false,false,0,"Published travel dates do not include a year");
        if(!calendarDays.isEmpty()&&!dates.containsAll(calendarDays))return new Fit(false,false,0,"");
        if(!months.isEmpty()&&!months.stream().allMatch(m->dates.isEmpty()?PlanSearchQuery.phrase(text,Month.of(m).name()):dates.stream().anyMatch(d->d.getMonthValue()==m)))return new Fit(false,false,0,"");
        boolean christmasText=PlanSearchQuery.phrase(text,"christmas")||PlanSearchQuery.phrase(text,"xmas");
        boolean newYearText=gregorianNewYear(text);
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
    private static Set<String> names(Month month){var names=new HashSet<String>();names.add(month.name().toLowerCase(Locale.ROOT));names.add(month.getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH).toLowerCase(Locale.ROOT));if(month==Month.SEPTEMBER)names.add("sept");return names;}
}
