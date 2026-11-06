package com.pagesearch.web;

import com.pagesearch.index.IndexReport;
import com.pagesearch.index.IndexStatus;
import com.pagesearch.index.PageIndex;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final PageIndex pageIndex;

    public IndexController(PageIndex pageIndex) {
        this.pageIndex = pageIndex;
    }

    @GetMapping
    public IndexStatus status() {
        return pageIndex.status();
    }

    @PostMapping
    public IndexReport rebuild() {
        return pageIndex.rebuild();
    }
}
