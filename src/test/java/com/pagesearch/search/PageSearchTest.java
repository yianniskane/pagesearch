package com.pagesearch.search;

import com.pagesearch.config.SearchProperties;
import com.pagesearch.corpus.CorpusReader;
import com.pagesearch.index.PageIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageSearchTest {

    @TempDir
    Path root;

    @Test
    void allWordsMustAppearAndHighlightsMarkThem() throws IOException {
        PageSearch search = index(2,
                "notes/march.txt", "The invoice payment is overdue today.",
                "notes/gap.txt", "payment is overdue",
                "notes/one.txt", "payment only");

        SearchPage page = search.search("Payment Overdue", SearchMode.ALL, 1);

        assertEquals("all", page.mode());
        assertEquals(2, page.totalHits());
        assertEquals(Set.of("notes/gap.txt", "notes/march.txt"), new HashSet<>(ids(page)));
        SearchHit march = hit(page, "notes/march.txt");
        assertEquals(List.of("payment", "overdue"), march.matchedTerms());
        assertMarked(march.snippet(), march.snippetHighlights(), "payment");
        assertMarked(march.snippet(), march.snippetHighlights(), "overdue");
    }

    @Test
    void anyWordKeepsPartialMatchesAndListsWhichWordsWereFound() throws IOException {
        PageSearch search = index(20,
                "notes/alpha.txt", "alpha only",
                "notes/both.txt", "alpha and beta");

        SearchPage page = search.search("alpha beta", SearchMode.ANY, 1);

        assertEquals(Set.of("notes/both.txt", "notes/alpha.txt"), new HashSet<>(ids(page)));
        assertEquals(List.of("alpha", "beta"), hit(page, "notes/both.txt").matchedTerms());
        assertEquals(List.of("alpha"), hit(page, "notes/alpha.txt").matchedTerms());
    }

    @Test
    void phraseRequiresTheWordsTogether() throws IOException {
        PageSearch search = index(20,
                "notes/gap.txt", "payment is overdue",
                "exact.txt", "payment overdue now");

        SearchPage page = search.search("payment overdue", SearchMode.PHRASE, 1);

        assertEquals(List.of("exact.txt"), ids(page));
        assertTrue(page.hits().get(0).snippet().contains("payment overdue"));
        assertMarked(page.hits().get(0).snippet(), page.hits().get(0).snippetHighlights(), "payment");
        assertMarked(page.hits().get(0).snippet(), page.hits().get(0).snippetHighlights(), "overdue");
    }

    @Test
    void titleMatchIsMarkedOnTheTitle() throws IOException {
        PageSearch search = index(20, "payment.txt", "nothing of interest");

        SearchHit hit = search.search("payment", SearchMode.ALL, 1).hits().get(0);

        assertEquals("payment.txt", hit.id());
        assertEquals(List.of("payment"), hit.matchedTerms());
        assertMarked(hit.title(), hit.titleHighlights(), "payment");
    }

    @Test
    void secondPageContinuesTheHits() throws IOException {
        PageSearch search = index(1,
                "a.txt", "alpha one",
                "b.txt", "alpha two");

        SearchPage first = search.search("alpha", SearchMode.ALL, 1);
        SearchPage second = search.search("alpha", SearchMode.ALL, 2);
        SearchPage third = search.search("alpha", SearchMode.ALL, 3);

        assertEquals(2, first.totalHits());
        assertEquals(1, first.hits().size());
        assertEquals(1, second.hits().size());
        assertEquals(
                Set.of("a.txt", "b.txt"),
                Set.of(first.hits().get(0).id(), second.hits().get(0).id())
        );
        assertEquals(List.of(), third.hits());
        assertEquals(2, third.totalHits());
    }

    @Test
    void matchCaseCountsOnlyThatCase() throws IOException {
        PageSearch search = index(20, "case.txt", "Plato met plato and Plato.");

        SearchPage sensitive = search.search("Plato", SearchMode.ALL, 1, true, false);
        SearchPage lower = search.search("plato", SearchMode.ALL, 1, true, false);

        assertEquals(2L, sensitive.totalMatches());
        assertEquals(2, sensitive.hits().get(0).matchCount());
        assertMarked(sensitive.hits().get(0).snippet(), sensitive.hits().get(0).snippetHighlights(), "Plato");
        assertEquals(1L, lower.totalMatches());
    }

    @Test
    void regexHonorsMatchCase() throws IOException {
        PageSearch search = index(20, "case.txt", "Plato met plato and Plato.");

        assertEquals(3L, search.search("Plato", SearchMode.ALL, 1, false, true).totalMatches());
        assertEquals(2L, search.search("Plato", SearchMode.ALL, 1, true, true).totalMatches());
        assertThrows(InvalidSearchException.class, () -> search.search("[", SearchMode.ALL, 1, true, true));
    }

    @Test
    void regexDotDoesNotMatchNewline() throws IOException {
        PageSearch search = index(20, "dot.txt", "aXb\na\nb");

        assertEquals(1L, search.search("a.b", SearchMode.ALL, 1, true, true).totalMatches());
    }

    @Test
    void missingIndexAndEmptyQueryFail() {
        SearchProperties properties = new SearchProperties();
        properties.setCorpusDir(root.resolve("corpus"));
        properties.setIndexDir(root.resolve("index"));
        PageSearch search = new PageSearch(properties);

        InvalidSearchException missing = assertThrows(InvalidSearchException.class,
                () -> search.search("payment", SearchMode.ALL, 1));
        assertTrue(missing.getMessage().contains("No index yet"));

        indexQuietly("a.txt", "payment");
        InvalidSearchException blank = assertThrows(InvalidSearchException.class,
                () -> search.search("  ", SearchMode.ALL, 1));
        assertTrue(blank.getMessage().contains("Enter a word"));
        assertThrows(InvalidSearchException.class, () -> search.search("payment", SearchMode.ALL, 0));
    }

    private PageSearch index(int pageSize, String... idThenBody) throws IOException {
        SearchProperties properties = properties(pageSize);
        Path corpus = properties.getCorpusDir();
        for (int i = 0; i < idThenBody.length; i += 2) {
            Path file = corpus.resolve(idThenBody[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, idThenBody[i + 1]);
        }
        new PageIndex(properties, new CorpusReader(properties)).rebuild();
        return new PageSearch(properties);
    }

    private void indexQuietly(String id, String body) {
        try {
            index(20, id, body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private SearchProperties properties(int pageSize) throws IOException {
        SearchProperties properties = new SearchProperties();
        properties.setCorpusDir(Files.createDirectories(root.resolve("corpus")));
        properties.setIndexDir(root.resolve("index"));
        properties.setResultsPerPage(pageSize);
        return properties;
    }

    private static List<String> ids(SearchPage page) {
        return page.hits().stream().map(SearchHit::id).toList();
    }

    private static SearchHit hit(SearchPage page, String id) {
        return page.hits().stream()
                .filter(hit -> hit.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static void assertMarked(String text, List<TextSpan> spans, String word) {
        int start = text.indexOf(word);
        assertTrue(start >= 0, text);
        int end = start + word.length();
        assertTrue(
                spans.stream().anyMatch(span -> span.start() <= start && span.end() >= end),
                text + " " + spans
        );
    }
}
