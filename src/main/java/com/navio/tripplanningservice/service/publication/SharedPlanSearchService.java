package com.navio.tripplanningservice.service.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navio.tripplanningservice.dto.publication.*;
import com.navio.tripplanningservice.model.TripPublication;
import com.navio.tripplanningservice.repository.ExploreSearchRepository;
import com.navio.tripplanningservice.repository.SharedPlanCopyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/** Public snapshot search. Two streamed passes discover query geography, then rank bounded pools. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class SharedPlanSearchService {
    private final ExploreSearchRepository repository;
    private final ObjectMapper mapper;
    private final ExplorePlanSummarizer summarizer;
    private final SharedPlanCopyRepository copies;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.navio.tripplanningservice.repository.PublicationActivityRepository activity;
    java.time.Clock discoveryClock=java.time.Clock.systemUTC();
    java.time.LocalDate discoveryToday(){return java.time.LocalDate.now(discoveryClock);}
    Map<UUID,Long> activityScores(java.time.LocalDate today){return activity==null?Map.of():activity.scores(today);}
    public SearchPage search(String term,String country,Integer days,int page,int size,String sort,String collections,String themes){
        var filters=PlanDiscovery.Filters.parse(collections,themes);
        return new SmartPlanSearchEngine(this,repository,summarizer,copies).search(term,country,days,page,size,sort,filters);
    }
    static final Map<String,String> COUNTRIES=new TreeMap<>(),CODES=new HashMap<>();
    @org.springframework.beans.factory.annotation.Value("${navio.search.smart-enabled:true}")
    private boolean smartEnabled=true;
    static {
        for(String code:Locale.getISOCountries()) {String name=Locale.of("",code).getDisplayCountry(Locale.ENGLISH);COUNTRIES.put(code,name);CODES.put(normal(name),code);CODES.put(normal(code),code);}
        for(String alias:List.of("usa","u s","u s a","united states of america"))CODES.put(alias,"US");
        for(String alias:List.of("uk","u k","great britain","britain"))CODES.put(alias,"GB");
        CODES.put("turkiye","TR");CODES.put("south korea","KR");CODES.put("north korea","KP");
    }
    public record QuerySuggestion(String text,String source) {}
    public record Destination(String id,String label,String country,String kind,long count) {}
    /** Legacy fields remain during rolling upgrades; new clients use matching/related pools. */
    public record SearchPage(List<ExplorePlanSummary> content,List<ExplorePlanSummary> recommendations,List<Destination> destinations,
        List<ExplorePlanSummary> plans,int number,boolean last,long totalElements,long matchedCount,
        List<ExplorePlanSummary> matching,List<ExplorePlanSummary> related,long matchingTotal,long relatedTotal,
        boolean matchingLast,boolean relatedLast,List<ExplorePlanSummary> relatedPlans,Map<String,String> reasons,String correction,List<QuerySuggestion> querySuggestions,String notice,SearchAssist searchAssist) {
        public SearchPage(List<ExplorePlanSummary> content,List<ExplorePlanSummary> recommendations,List<Destination> destinations,List<ExplorePlanSummary> plans,int number,boolean last,long totalElements,long matchedCount){
            this(content,recommendations,destinations,plans,number,last,totalElements,matchedCount,Stream.concat(recommendations.stream(),content.stream()).toList(),List.of(),matchedCount,0,last,true,List.of(),Map.of(),null,List.of(),null,null);
        }
    }
    private record Candidate(TripPublication row,PublicPlanSnapshot plan,PlanSearchQuery.Match match) {}
    public static String normal(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim().replaceAll(" +"," ");}
    static String code(String value){return CODES.getOrDefault(normal(value),"");}
    private static <T> List<T> safe(List<T> values){return values==null?List.of():values;}
    PublicPlanSnapshot read(TripPublication row) {
        if(!row.isListed()||row.getStatus()!=TripPublication.PublicationStatus.ACTIVE||row.getToken()==null||row.getSanitizerVersion()==null||row.getSanitizerVersion()<PlanPublicationSanitizer.SANITIZER_VERSION)return null;
        try {var plan=PublicPlanDates.read(mapper,row.getSnapshot());return plan!=null&&plan.sanitizerVersion()>=PlanPublicationSanitizer.SANITIZER_VERSION?plan:null;}catch(Exception unreadable){return null;}
    }
    private static List<PublicItemDto> places(PublicPlanSnapshot plan) {
        return Stream.concat(safe(plan.days()).stream(),safe(plan.lists()).stream()).filter(Objects::nonNull)
            .flatMap(day->safe(day.items()).stream()).filter(Objects::nonNull)
            .filter(item->Set.of("place","charger","section").contains(item.type()==null?"":item.type())).toList();
    }
    private static PlanSearchQuery.Evidence evidence(PublicPlanSnapshot plan) {
        Set<String> countries=new HashSet<>();String primary=code(plan.destinationCountry());if(!primary.isEmpty())countries.add(primary);
        var text=new StringBuilder().append(Objects.toString(plan.title(),"")).append(' ').append(Objects.toString(plan.destinationCity(),"")).append(' ').append(Objects.toString(plan.destinationCountry(),""));
        var themeText=new StringBuilder().append(Objects.toString(plan.title(),""));
        for(var item:places(plan)) {
            text.append(' ').append(Objects.toString(item.name(),"")).append(' ').append(Objects.toString(item.city(),""))
                .append(' ').append(Objects.toString(item.description(),"")).append(' ').append(Objects.toString(item.placeType(),"").replace('_',' '));
            themeText.append(' ').append(Objects.toString(item.name(),"")).append(' ').append(Objects.toString(item.placeType(),"").replace('_',' '));
            if(item.charger()!=null){text.append(" ev charger");themeText.append(" ev charger");}
            if(item.arrivalMode()!=null){text.append(' ').append(item.arrivalMode());themeText.append(' ').append(item.arrivalMode());}
            if(item.address()!=null){text.append(' ').append(item.address());String[] parts=item.address().split(",");String c=parts.length==0?"":code(parts[parts.length-1]);if(!c.isEmpty())countries.add(c);}
        }
        if(countries.contains("US")&&PlanSearchQuery.phrase(text.toString(),"ca"))text.append(" California");
        // Only already-sanitized public owner text participates; private planner state is never read.
        if(plan.startingPart()!=null)appendOwnerText(text,themeText,plan.startingPart().content());
        Stream.concat(safe(plan.days()).stream(),safe(plan.lists()).stream()).filter(Objects::nonNull).forEach(day->{
            text.append(' ').append(Objects.toString(day.title(),""));
            for(var item:safe(day.items()))if(item!=null){appendOwnerText(text,themeText,item.notes());appendOwnerText(text,themeText,item.noteContent());}
        });
        String normalized=normal(text.toString());
        var dates=new ArrayList<java.time.MonthDay>();
        if(plan.included()!=null&&plan.included().includeDates()) {
            if(plan.startDate()!=null)dates.add(plan.startDate());
            safe(plan.days()).stream().filter(Objects::nonNull).map(PublicDayDto::date).filter(Objects::nonNull).forEach(dates::add);
        }
        return new PlanSearchQuery.Evidence(plan.title(),normalized,Set.copyOf(countries),PlanSearchQuery.themesIn(themeText.toString()),List.copyOf(dates));
    }
    private static void appendOwnerText(StringBuilder text,StringBuilder themes,String value) {
        if(value==null)return;
        // Speculative/negative owner sentences are not affirmative activity evidence.
        String positive=Arrays.stream(value.split("(?<=[.!?;])|\\n")).filter(sentence->!sentence.contains("?")&&!normal(sentence).matches(".*\\b(?:no|not|without|avoid|maybe|might|perhaps|optional|consider|could|should)\\b.*")).collect(java.util.stream.Collectors.joining(" "));
        text.append(' ').append(positive);themes.append(' ').append(positive);
    }
    private static void retain(PriorityQueue<Candidate> pool,Candidate value,int capacity,Comparator<Candidate> order){
        if(pool.size()<capacity)pool.add(value);else if(order.compare(value,pool.peek())<0){pool.poll();pool.add(value);}
    }
    private static <T> List<T> window(List<T> values,int from,int size){int start=Math.min(values.size(),from);return List.copyOf(values.subList(start,Math.min(values.size(),start+size)));}
    public SearchPage search(String term,String country,Integer days,int page,int size,String sort){
        if(smartEnabled)return new SmartPlanSearchEngine(this,repository,summarizer,copies).search(term,country,days,page,size,sort);
        return searchLegacy(term,country,days,page,size,sort);
    }
    private SearchPage searchLegacy(String term,String country,Integer days,int page,int size,String sort){
        term=term==null?"":term.strip();country=country==null?"":country.toUpperCase(Locale.ROOT);
        if(term.length()>100||(!country.isEmpty()&&!COUNTRIES.containsKey(country))||days!=null&&(days<1||days>36500)||page<0||page>500||size<1||size>48||!Set.of("relevance","recent","trending").contains(sort))throw new IllegalArgumentException("Invalid search parameters");
        final String text=term,selected=country,normalized=normal(term);
        var locations=new LinkedHashMap<String,PlanSearchQuery.Location>();
        COUNTRIES.forEach((c,name)->locations.put("country:"+c,new PlanSearchQuery.Location(name,c,true)));
        CODES.forEach((alias,c)->{if(alias.length()>2||Set.of("uk","us").contains(alias)||normalized.equals(alias))locations.putIfAbsent("alias:"+alias,new PlanSearchQuery.Location(alias,c,true));});
        locations.put("region:california",new PlanSearchQuery.Location("California","US",false));
        locations.put("alias:hua-hin",new PlanSearchQuery.Location("Hua Hin","TH",false));
        // Only query-related public city names are retained; exact mentions cannot be displaced by prefix candidates.
        var cities=new LinkedHashMap<String,PlanSearchQuery.Location>();
        try(Stream<TripPublication> stream=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)) {
            stream.forEach(row->{var plan=read(row);if(plan==null)return;String c=code(plan.destinationCountry());
                var names=new ArrayList<String>();names.add(plan.destinationCity());places(plan).forEach(item->names.add(item.city()));
                for(String name:names)if(name!=null&&!name.isBlank()&&!c.isEmpty()) {
                    boolean exact=!normalized.isBlank()&&PlanSearchQuery.compact(text).contains(PlanSearchQuery.compact(name));
                    if(exact||cities.size()<128&&PlanSearchQuery.completesLocation(text,name))cities.putIfAbsent(normal(name)+":"+c,new PlanSearchQuery.Location(name,c,false));
                }
            });
        }
        locations.putAll(cities);var query=PlanSearchQuery.parse(text,locations.values(),selected);
        Comparator<Candidate> order=Comparator.comparingInt((Candidate c)->"relevance".equals(sort)?c.match().score():0).reversed();
        if("trending".equals(sort))order=order.thenComparing(Comparator.comparingLong((Candidate c)->Optional.ofNullable(c.row().getViewCount()).orElse(0L)).reversed());
        order=order.thenComparing(c->c.row().getListedAt(),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(c->c.row().getToken());
        int capacity=page*size+size+6;var strong=new PriorityQueue<Candidate>(order.reversed());var related=new PriorityQueue<Candidate>(order.reversed());
        var suggestionLocations=locations.values().stream().filter(location->{
            if(location.name().length()<3||!selected.isEmpty()&&!selected.equals(location.country()))return false;
            String name=normal(location.name());return normalized.length()>=2&&(name.startsWith(normalized)||PlanSearchQuery.completesLocation(text,name)||query.geography.stream().anyMatch(g->normal(g.name()).equals(name)));
        }).toList();
        var phraseCandidates=new TreeSet<String>();
        var destinations=new HashMap<String,Destination>();long matchingTotal=0,relatedTotal=0;
        try(Stream<TripPublication> stream=repository.streamListed(PlanPublicationSanitizer.SANITIZER_VERSION)) {
            var iterator=stream.iterator();while(iterator.hasNext()) {
                var row=iterator.next();var plan=read(row);if(plan==null)continue;var facts=evidence(plan);
                if(!selected.isEmpty()&&!facts.countries().contains(selected)||days!=null&&plan.dayCount()!=days)continue;
                // Destination completions are independent of strong/related result eligibility.
                var seenDestinations=new HashSet<String>();
                for(var location:suggestionLocations) {
                    String name=normal(location.name());
                    boolean occurs=location.countryLevel()?facts.countries().contains(location.country()):PlanSearchQuery.phrase(facts.text(),name);
                    if(!occurs)continue;
                    String id=location.countryLevel()?"country:"+location.country():"city:"+name+":"+location.country();
                    if(!seenDestinations.add(id))continue;
                    Destination old=destinations.get(id);String label=location.countryLevel()?COUNTRIES.get(location.country()):location.name();
                    // Country aliases must not multiply the same plan count.
                    destinations.put(id,new Destination(id,label,location.country(),location.countryLevel()?"country":"city",old==null?1:old.count()+1));
                }
                var match=query.match(facts,COUNTRIES);if(match==null)continue;
                if(phraseCandidates.size()<128 && match.strong() && plan.title()!=null && !normalized.isBlank()) {
                    String geography=query.geography.stream().map(g->g.countryLevel()?COUNTRIES.getOrDefault(g.country(),g.name()):g.name()).distinct().filter(name->!PlanSearchQuery.phrase(plan.title(),name)).collect(java.util.stream.Collectors.joining(" "));
                    String phrase=(geography+" "+plan.title()).strip();
                    var phraseMatch=query.match(new PlanSearchQuery.Evidence(phrase,normal(phrase),facts.countries(),PlanSearchQuery.themesIn(phrase)),COUNTRIES);
                    if(phraseMatch!=null&&phraseMatch.strong()&&!normal(phrase).equals(normalized))phraseCandidates.add(phrase);
                }
                var value=new Candidate(row,plan,match);
                if(match.strong()){matchingTotal++;retain(strong,value,capacity,order);}else{relatedTotal++;retain(related,value,capacity,order);}
            }
        }
        var ranked=strong.stream().sorted(order).toList();var broader=related.stream().sorted(order).toList();
        var retained=Stream.concat(ranked.stream(),broader.stream()).toList();
        var counts=retained.isEmpty()?Map.<UUID,SharedPlanCopyRepository.CopyStats>of():copies.counts(retained.stream().map(c->c.row().getId()).toList());
        Function<Candidate,ExplorePlanSummary> summary=c->summarizer.summarize(c.row().getToken(),c.plan(),c.row().getAuthorDisplayName(),c.row().getListedAt(),c.row().getUpdatedAt(),Optional.ofNullable(c.row().getViewCount()).orElse(0L),counts.getOrDefault(c.row().getId(),SharedPlanCopyRepository.CopyStats.ZERO).copies()).withFeedback(c.row());
        int recommended=!normalized.isBlank()&&"relevance".equals(sort)?Math.min(6,ranked.size()):0;
        var matching=window(ranked,page*size,size).stream().map(summary).toList();var relatedPage=window(broader,page*size,size).stream().map(summary).toList();
        var suggestions=normalized.length()>=2?window(ranked,0,4).stream().map(summary).toList():List.<ExplorePlanSummary>of();
        var relatedSuggestions=normalized.length()>=2?window(broader,0,4).stream().map(summary).toList():List.<ExplorePlanSummary>of();
        var reasons=new LinkedHashMap<String,String>();Stream.concat(window(broader,page*size,size).stream(),window(broader,0,4).stream()).forEach(c->reasons.put(c.row().getToken(),c.match().reason()));
        var destinationList=destinations.values().stream().sorted(Comparator.comparingLong(Destination::count).reversed().thenComparing(Destination::label).thenComparing(Destination::id)).limit(6).toList();
        boolean matchingLast=(long)(page+1)*size>=matchingTotal,relatedLast=(long)(page+1)*size>=relatedTotal;
        return new SearchPage(window(ranked,recommended+page*size,size).stream().map(summary).toList(),page==0?window(ranked,0,recommended).stream().map(summary).toList():List.of(),destinationList,suggestions,page,(long)(page+1)*size>=matchingTotal-recommended,matchingTotal-recommended,matchingTotal,
            matching,relatedPage,matchingTotal,relatedTotal,matchingLast,relatedLast,relatedSuggestions,Map.copyOf(reasons),query.correction,phraseCandidates.stream().sorted(Comparator.comparingInt((String p)->normal(p).startsWith(normalized)?0:1).thenComparing(String::length).thenComparing(Function.identity())).limit(4).map(p->new QuerySuggestion(p,"catalogue")).toList(),query.temporal.yearRequested()?"Published trip dates show month and day only; a travel year cannot be verified.":null,null);
    }
}
