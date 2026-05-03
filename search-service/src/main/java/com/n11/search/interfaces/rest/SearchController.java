package com.n11.search.interfaces.rest;

import com.n11.search.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<UUID> search(@RequestParam("q") String q,
                             @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return searchService.search(q, limit);
    }
}
