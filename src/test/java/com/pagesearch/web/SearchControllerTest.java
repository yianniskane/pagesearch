package com.pagesearch.web;

import com.pagesearch.search.InvalidSearchException;
import com.pagesearch.search.PageSearch;
import com.pagesearch.search.SearchHit;
import com.pagesearch.search.SearchMode;
import com.pagesearch.search.SearchPage;
import com.pagesearch.search.TextSpan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PageSearch pageSearch;

    @Test
    void searchDefaultsToAllWordsOnTheFirstPage() throws Exception {
        when(pageSearch.search("payment overdue", SearchMode.ALL, 1, false, false)).thenReturn(new SearchPage(
                "payment overdue",
                "all",
                1,
                20,
                1,
                null,
                List.of(new SearchHit(
                        "notes/march.txt",
                        "march",
                        List.of(),
                        "C:/page-search/corpus/notes/march.txt",
                        1.5f,
                        List.of("payment", "overdue"),
                        "invoice payment is overdue",
                        List.of(new TextSpan(8, 15), new TextSpan(19, 26)),
                        null
                ))
        ));

        mockMvc.perform(get("/api/search").param("q", "payment overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("all"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.hits[0].id").value("notes/march.txt"))
                .andExpect(jsonPath("$.hits[0].matchedTerms[1]").value("overdue"))
                .andExpect(jsonPath("$.hits[0].snippetHighlights[0].start").value(8))
                .andExpect(jsonPath("$.hits[0].snippetHighlights[0].end").value(15));
    }

    @Test
    void matchCaseAndRegexArePassedThrough() throws Exception {
        when(pageSearch.search("Plato", SearchMode.ALL, 1, true, true)).thenReturn(new SearchPage(
                "Plato", "all", 1, 20, 1, 175L, List.of()
        ));

        mockMvc.perform(get("/api/search")
                        .param("q", "Plato")
                        .param("matchCase", "true")
                        .param("regex", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(175));
    }

    @Test
    void unknownModeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "payment").param("mode", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("mode must be all, any, phrase, or regex"));
    }

    @Test
    void rejectedQueryIsBadRequest() throws Exception {
        when(pageSearch.search("the", SearchMode.ALL, 1, false, false))
                .thenThrow(new InvalidSearchException("Enter a word to search for"));

        mockMvc.perform(get("/api/search").param("q", "the"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Enter a word to search for"));
    }
}
