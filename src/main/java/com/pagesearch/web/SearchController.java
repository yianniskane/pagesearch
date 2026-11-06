package com.pagesearch.web;

import com.pagesearch.search.PageSearch;
import com.pagesearch.search.SearchMode;
import com.pagesearch.search.SearchPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final PageSearch pageSearch;

    public SearchController(PageSearch pageSearch) {
        this.pageSearch = pageSearch;
    }

    @GetMapping
    public SearchPage search(
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String mode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "false") boolean matchCase,
            @RequestParam(defaultValue = "false") boolean regex
    ) {
        return pageSearch.search(q, SearchMode.parse(mode), page, matchCase, regex);
    }
}
