package com.navio.tripplanningservice.service.publication;

import java.time.MonthDay;
import java.util.*;

record PublicSearchEvidence(String title,String text,String strongText,String eventText,Set<String> countries,Set<String> strong,
 Set<String> weak,Set<String> scheduled,List<MonthDay> dates,int stopCount,int natureStops) {}
