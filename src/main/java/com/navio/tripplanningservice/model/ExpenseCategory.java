package com.navio.tripplanningservice.model;

import java.util.Arrays;

public enum ExpenseCategory {
    FLIGHTS("flights"),
    LODGING("lodging"),
    CAR_RENTAL("car-rental"),
    TRANSIT("transit"),
    FOOD("food"),
    DRINKS("drinks"),
    SIGHTSEEING("sightseeing"),
    ACTIVITIES("activities"),
    SHOPPING("shopping"),
    GAS("gas"),
    GROCERIES("groceries"),
    OTHER("other");

    private final String clientId;

    ExpenseCategory(String clientId) {
        this.clientId = clientId;
    }

    public String clientId() {
        return clientId;
    }

    public static ExpenseCategory fromClientId(String clientId) {
        return Arrays.stream(values())
                .filter(category -> category.clientId.equals(clientId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported expense category: " + clientId));
    }
}
