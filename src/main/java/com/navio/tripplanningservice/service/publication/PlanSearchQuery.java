package com.navio.tripplanningservice.service.publication;

import java.util.*;
import java.util.regex.Pattern;

/** Deterministic interpretation of travel queries; no private state or generated facts. */
final class PlanSearchQuery {
    record Location(String name, String country, boolean countryLevel) {}
    record Evidence(String title, String text, Set<String> countries, Set<String> themes) {}
    record Match(boolean strong, int score, String reason) {}
    static final Map<String, List<String>> THEMES = Map.of(
        "food", List.of("food", "dining", "restaurant", "restaurants", "cafe", "cafes", "culinary", "seafood", "sushi", "eat", "eating"),
        "scenic", List.of("scenic", "scenery", "scenary", "view", "views", "viewpoint", "landscape", "coast", "coastal", "beach", "mountain", "garden", "lookout"),
        "culture", List.of("culture", "cultural", "temple", "temples", "museum", "museums", "heritage", "historic", "historical"),
        "ev", List.of("ev", "charger", "chargers", "charging", "electric vehicle"),
        "walk", List.of("walk", "walking", "hike", "hiking"),
        "drive", List.of("road trip", "drive", "driving", "roadtrip")
    );
    private static final Set<String> GENERAL_INTENT = Set.of("food","dining","culinary","eat","eating","scenic","scenery","scenary","view","views","landscape","culture","cultural","ev","electric vehicle","road trip","roadtrip","drive","driving","walk","walking");
    private static final Set<String> FILLER = Set.of("a","an","the","and","or","in","of","for","to","with","on","my","our","your","trip","trips","plan","plans","itinerary","itineraries","travel","focus","focused","tour","tours","route","routes");
    final String normalized;
    final String correction;
    final List<Location> geography;
    final Set<String> themes;
    final List<String> terms;
    final Map<String,Set<String>> alternativeCountries;

    static String normal(String text) { return SharedPlanSearchService.normal(text); }
    static String compact(String text) { return normal(text).replace(" ", ""); }
    static boolean phrase(String text, String phrase) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normal(phrase)) + "(?![\\p{L}\\p{N}])").matcher(normal(text)).find();
    }
    static boolean completesLocation(String text,String name) {
        String[] words=normal(text).split(" ");String location=normal(name);
        for(int start=0;start<words.length;start++) {
            String suffix=String.join(" ",Arrays.copyOfRange(words,start,words.length));
            if(suffix.length()<2||!location.startsWith(suffix))continue;
            if(start>0&&words[start-1].equals(location.split(" ")[0]))continue;
            return true;
        }
        return false;
    }
    private static String locationPattern(String text) {
        return "(?<![\\p{L}\\p{N}])" + String.join("\\s*", Arrays.stream(normal(text).split(" ")).map(Pattern::quote).toList()) + "(?![\\p{L}\\p{N}])";
    }
    private PlanSearchQuery(String text, Collection<Location> locations, String selectedCountry) {
        normalized = normal(text);
        String remaining = normalized;
        String corrected = text.strip().replaceAll("(?i)\\bscenary\\b", "scenic");
        var explicitCountries=new HashSet<String>();
        locations.stream().filter(Location::countryLevel).filter(l->Pattern.compile(locationPattern(l.name())).matcher(normalized).find()).forEach(l->explicitCountries.add(l.country()));
        var resolved=new ArrayList<Location>(locations.stream().filter(Location::countryLevel).toList());
        var groups=new LinkedHashMap<String,List<Location>>();
        locations.stream().filter(l->!l.countryLevel()).forEach(l->groups.computeIfAbsent(compact(l.name()),ignored->new ArrayList<>()).add(l));
        var alternatives=new LinkedHashMap<String,Set<String>>();
        for(var entry:groups.entrySet()) {
            var choices=entry.getValue();
            var preferred=choices.stream().filter(l->!explicitCountries.isEmpty()?explicitCountries.contains(l.country()):!selectedCountry.isEmpty()&&selectedCountry.equals(l.country())).toList();
            if(!preferred.isEmpty())choices=preferred;
            Set<String> options=new HashSet<>();choices.forEach(l->options.add(l.country()));
            Location first=choices.getFirst();
            if(options.size()>1){resolved.add(new Location(first.name(),"",false));alternatives.put(compact(first.name()),Set.copyOf(options));}
            else resolved.add(first);
        }
        var found = new ArrayList<Location>();
        for (Location location : resolved.stream().sorted(Comparator.comparingInt((Location l)->normal(l.name()).length()).reversed()).toList()) {
            String pattern = locationPattern(location.name());
            if (Pattern.compile(pattern).matcher(remaining).find()) {
                found.add(location);
                remaining = remaining.replaceAll(pattern, " ");
                if (!location.countryLevel()) corrected = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(corrected).replaceAll(java.util.regex.Matcher.quoteReplacement(location.name()));
            }
        }
        geography = List.copyOf(found);
        alternatives.keySet().retainAll(found.stream().map(l->compact(l.name())).toList());
        alternativeCountries=Map.copyOf(alternatives);
        var wanted = new HashSet<String>();
        for (var theme : THEMES.entrySet()) for (String alias : theme.getValue()) {
            if (phrase(remaining, alias)) { wanted.add(theme.getKey()); if(GENERAL_INTENT.contains(alias)) remaining = remaining.replaceAll("(?<![\\p{L}\\p{N}])"+Pattern.quote(alias)+"(?![\\p{L}\\p{N}])", " "); }
        }
        themes = Set.copyOf(wanted);
        terms = Arrays.stream(remaining.strip().split("\\s+")).filter(t->!t.isBlank()&&!FILLER.contains(t)).distinct().toList();
        correction = normal(corrected).equals(normal(text)) && corrected.equals(text.strip()) ? null : corrected;
    }
    static PlanSearchQuery parse(String text, Collection<Location> locations, String selectedCountry) { return new PlanSearchQuery(text, locations, selectedCountry); }
    static Set<String> themesIn(String text) {
        var result = new HashSet<String>();
        THEMES.forEach((theme, aliases)->{if(aliases.stream().anyMatch(alias->phrase(text,alias)))result.add(theme);});
        return result;
    }
    private static boolean tokenMatch(String term, String text) {
        for (String word : normal(text).split(" ")) {
            if(word.equals(term) || term.length()>=2 && word.startsWith(term)) return true;
            if(term.length()>=5 && Math.abs(term.length()-word.length())<=1 && oneEdit(term,word))return true;
        }
        return false;
    }
    private static boolean oneEdit(String a,String b) {
        int i=0,j=0,edits=0;
        while(i<a.length()&&j<b.length()) {
            if(a.charAt(i)==b.charAt(j)){i++;j++;continue;}
            if(++edits>1)return false;
            if(a.length()>=b.length())i++;
            if(b.length()>=a.length())j++;
        }
        return edits+(a.length()-i)+(b.length()-j)<=1;
    }
    Match match(Evidence item, Map<String,String> countryNames) {
        Set<String> anchors = new HashSet<>(); geography.stream().filter(g->!g.country().isBlank()).forEach(g->anchors.add(g.country()));
        // Typed geography, like an explicit country filter, must never become global theme matching.
        if(!item.countries().containsAll(anchors)||alternativeCountries.values().stream().anyMatch(options->Collections.disjoint(options,item.countries()))) return null;
        int localHits=0,locals=0;
        for(Location location:geography) if(!location.countryLevel()) {locals++;if(Pattern.compile(locationPattern(location.name())).matcher(normal(item.text())).find())localHits++;}
        int themeHits=(int)themes.stream().filter(item.themes()::contains).count();
        int termHits=(int)terms.stream().filter(t->tokenMatch(t,item.text())).count();
        boolean informative=!geography.isEmpty()||!themes.isEmpty()||!terms.isEmpty();
        boolean strong=localHits==locals&&themeHits==themes.size()&&termHits==terms.size();
        if(!informative) strong=true; // Empty/filter-only queries preserve catalogue browsing.
        boolean related=localHits>0||!anchors.isEmpty()||themeHits>0||termHits>0&&(double)termHits/Math.max(1,terms.size())>=0.34;
        if(!strong&&!related)return null;
        int score=localHits*100+(!anchors.isEmpty()?40:0)+themeHits*25+termHits*10;
        String title=normal(item.title());
        score+=title.equals(normalized)?140:title.startsWith(normalized)&&!normalized.isBlank()?80:title.contains(normalized)&&!normalized.isBlank()?40:0;
        String reason=localHits>0?"Also in "+geography.stream().filter(g->!g.countryLevel()&&Pattern.compile(locationPattern(g.name())).matcher(normal(item.text())).find()).findFirst().orElseThrow().name()
            :!anchors.isEmpty()?"Also in "+countryNames.getOrDefault(anchors.stream().sorted().findFirst().orElseThrow(),anchors.stream().sorted().findFirst().orElseThrow())
            :themeHits>0? switch(themes.stream().filter(item.themes()::contains).sorted().findFirst().orElseThrow()) {case "food"->"Includes food stops";case "scenic"->"Includes scenic places";case "culture"->"Includes cultural stops";case "ev"->"Includes EV travel";case "walk"->"Includes walking";default->"Includes driving";}
            :"Shares part of your search";
        return new Match(strong,score,reason);
    }
}
