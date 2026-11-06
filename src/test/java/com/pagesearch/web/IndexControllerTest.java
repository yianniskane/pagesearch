package com.pagesearch.web;

import com.pagesearch.corpus.CorpusReadException;
import com.pagesearch.index.IndexReport;
import com.pagesearch.index.IndexStatus;
import com.pagesearch.index.PageIndex;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PageIndex pageIndex;

    @Test
    void statusAndRebuild() throws Exception {
        when(pageIndex.status()).thenReturn(new IndexStatus(true, 2));
        when(pageIndex.rebuild()).thenReturn(new IndexReport(2, "C:/page-search/index", 15));

        mockMvc.perform(get("/api/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.built").value(true))
                .andExpect(jsonPath("$.pageCount").value(2));

        mockMvc.perform(post("/api/index"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.indexDir").value("C:/page-search/index"))
                .andExpect(jsonPath("$.elapsedMillis").value(15));
    }

    @Test
    void unreadablePageIsBadRequest() throws Exception {
        when(pageIndex.rebuild()).thenThrow(new CorpusReadException("Page is not UTF-8: bad.txt"));

        mockMvc.perform(post("/api/index"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Page is not UTF-8: bad.txt"));
    }
}
