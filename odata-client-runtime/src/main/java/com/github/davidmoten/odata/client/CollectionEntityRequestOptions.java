package com.github.davidmoten.odata.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class CollectionEntityRequestOptions implements RequestOptions {

    private final Map<String, String> requestHeaders;
    private final Optional<String> search;
    private final Optional<String> filter;
    private final Optional<String> orderBy;
    private final Optional<Long> skip;
    private final Optional<Long> top;
    private final Optional<String> select;

    public CollectionEntityRequestOptions(Map<String, String> requestHeaders,
            Optional<String> search, Optional<String> filter, Optional<String> orderBy,
            Optional<Long> skip, Optional<Long> top, Optional<String> select) {
        this.requestHeaders = requestHeaders;
        this.search = search;
        this.filter = filter;
        this.orderBy = orderBy;
        this.skip = skip;
        this.top = top;
        this.select = select;
    }

    @Override
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    @Override
    public Map<String, String> getQueries() {
        Map<String, String> map = new HashMap<>();
        search.ifPresent(x -> map.put("$search", x));
        filter.ifPresent(x -> map.put("$filter", x));
        orderBy.ifPresent(x -> map.put("$orderBy", x));
        skip.ifPresent(x -> map.put("$skip", String.valueOf(x)));
        top.ifPresent(x -> map.put("$top", String.valueOf(x)));
        select.ifPresent(x -> map.put("$select", x));
        return map;
    }

    public Optional<String> getSearch() {
        return search;
    }

    public Optional<String> getFilter() {
        return filter;
    }

    public Optional<String> getOrderBy() {
        return orderBy;
    }

    public Optional<Long> getSkip() {
        return skip;
    }

    public Optional<Long> getTop() {
        return top;
    }
}