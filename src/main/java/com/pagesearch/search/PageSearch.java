package com.pagesearch.search;

import com.pagesearch.config.SearchProperties;
import com.pagesearch.index.IndexException;
import com.pagesearch.index.PageFields;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Searches the Lucene index. Words are taken from the text the user typed;
 * Lucene operators in that text are not treated as operators.
 */
@Component
public class PageSearch {

    private static final int MAX_WORDS = 32;
    private static final int PREVIEW_CHARS = 160;

    private final SearchProperties properties;

    public PageSearch(SearchProperties properties) {
        this.properties = properties;
    }

    public SearchPage search(String query, SearchMode mode, int page) {
        return search(query, mode, page, false, false);
    }

    public SearchPage search(String query, SearchMode mode, int page, boolean matchCase, boolean regex) {
        if (page < 1) {
            throw new InvalidSearchException("page must be 1 or greater");
        }
        if (matchCase || regex || mode == SearchMode.REGEX) {
            return scan(query, mode, page, matchCase, regex || mode == SearchMode.REGEX);
        }
        List<QueryToken> tokens = analyze(query);
        if (tokens.isEmpty()) {
            throw new InvalidSearchException("Enter a word to search for");
        }
        if (tokens.size() > MAX_WORDS) {
            throw new InvalidSearchException("Use at most 32 words");
        }
        int pageSize = properties.getResultsPerPage();
        if (pageSize < 1) {
            throw new IndexException("results-per-page must be at least 1");
        }
        int offset = offset(page, pageSize);
        Query luceneQuery = toQuery(mode, tokens);
        List<String> words = distinctTexts(tokens);
        return read(query, mode, page, pageSize, offset, words, luceneQuery);
    }

    private SearchPage read(
            String query,
            SearchMode mode,
            int page,
            int pageSize,
            int offset,
            List<String> words,
            Query luceneQuery
    ) {
        Path live = properties.getIndexDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(live)) {
            throw new InvalidSearchException("No index yet. Rebuild the index first.");
        }
        try (Directory directory = FSDirectory.open(live);
             Analyzer analyzer = PageFields.newAnalyzer()) {
            if (!DirectoryReader.indexExists(directory)) {
                throw new InvalidSearchException("No index yet. Rebuild the index first.");
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                int total = searcher.count(luceneQuery);
                if (offset >= total) {
                    return page(query, mode, page, pageSize, total, List.of());
                }
                TopDocs top = searcher.search(luceneQuery, offset + pageSize);
                int to = Math.min(top.scoreDocs.length, offset + pageSize);
                ScoreDoc[] docs = new ScoreDoc[to - offset];
                System.arraycopy(top.scoreDocs, offset, docs, 0, docs.length);
                return page(query, mode, page, pageSize, total, hits(searcher, analyzer, luceneQuery, words, docs));
            }
        } catch (IOException e) {
            throw new IndexException("Cannot search index at " + live, e);
        }
    }

    private static List<SearchHit> hits(
            IndexSearcher searcher,
            Analyzer analyzer,
            Query luceneQuery,
            List<String> words,
            ScoreDoc[] docs
    ) throws IOException {
        int[] docIds = new int[docs.length];
        for (int i = 0; i < docs.length; i++) {
            docIds[i] = docs[i].doc;
        }
        SnippetHighlighter highlighter = new SnippetHighlighter(searcher, analyzer);
        List<FormattedSnippet> titles = highlighter.snippets(PageFields.TITLE, luceneQuery, docIds);
        List<FormattedSnippet> bodies = highlighter.snippets(PageFields.BODY, luceneQuery, docIds);
        List<SearchHit> hits = new ArrayList<>(docs.length);
        for (int i = 0; i < docs.length; i++) {
            Document stored = searcher.storedFields().document(docs[i].doc);
            String title = text(stored.get(PageFields.TITLE));
            String body = text(stored.get(PageFields.BODY));
            FormattedSnippet titleSnippet = titles.get(i);
            FormattedSnippet bodySnippet = bodies.get(i);
            String snippet = bodySnippet.text().isEmpty() ? preview(body) : bodySnippet.text();
            List<TextSpan> snippetHighlights = bodySnippet.text().isEmpty() ? List.of() : bodySnippet.highlights();
            hits.add(new SearchHit(
                    text(stored.get(PageFields.ID)),
                    title,
                    titleSnippet.highlights(),
                    text(stored.get(PageFields.PATH)),
                    docs[i].score,
                    matchedTerms(searcher, docs[i].doc, words),
                    snippet,
                    snippetHighlights,
                    null
            ));
        }
        return hits;
    }

    private static List<String> matchedTerms(IndexSearcher searcher, int docId, List<String> words) throws IOException {
        List<LeafReaderContext> leaves = searcher.getIndexReader().leaves();
        int leaf = ReaderUtil.subIndex(docId, leaves);
        LeafReaderContext context = leaves.get(leaf);
        LeafReader reader = context.reader();
        int localDoc = docId - context.docBase;
        List<String> matched = new ArrayList<>();
        for (String word : words) {
            if (contains(reader, localDoc, PageFields.TITLE, word) || contains(reader, localDoc, PageFields.BODY, word)) {
                matched.add(word);
            }
        }
        return List.copyOf(matched);
    }

    private static boolean contains(LeafReader reader, int docId, String field, String word) throws IOException {
        Terms terms = reader.terms(field);
        if (terms == null) {
            return false;
        }
        TermsEnum iterator = terms.iterator();
        if (!iterator.seekExact(new BytesRef(word))) {
            return false;
        }
        PostingsEnum postings = iterator.postings(null, PostingsEnum.NONE);
        return postings != null && postings.advance(docId) == docId;
    }

    private static Query toQuery(SearchMode mode, List<QueryToken> tokens) {
        return switch (mode) {
            case ALL -> eachWord(tokens, Occur.MUST);
            case ANY -> eachWord(tokens, Occur.SHOULD);
            case PHRASE -> phrase(tokens);
            case REGEX -> throw new InvalidSearchException("A regular expression is scanned in the page text");
        };
    }

    private static Query eachWord(List<QueryToken> tokens, Occur occur) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        if (occur == Occur.SHOULD) {
            query.setMinimumNumberShouldMatch(1);
        }
        for (String word : distinctTexts(tokens)) {
            query.add(inTitleOrBody(word), occur);
        }
        return query.build();
    }

    private static Query inTitleOrBody(String word) {
        return new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(new TermQuery(new Term(PageFields.TITLE, word)), Occur.SHOULD)
                .add(new TermQuery(new Term(PageFields.BODY, word)), Occur.SHOULD)
                .build();
    }

    private static Query phrase(List<QueryToken> tokens) {
        return new BooleanQuery.Builder()
                .setMinimumNumberShouldMatch(1)
                .add(phrase(PageFields.TITLE, tokens), Occur.SHOULD)
                .add(phrase(PageFields.BODY, tokens), Occur.SHOULD)
                .build();
    }

    private static PhraseQuery phrase(String field, List<QueryToken> tokens) {
        PhraseQuery.Builder phrase = new PhraseQuery.Builder();
        for (QueryToken token : tokens) {
            phrase.add(new Term(field, token.text()), token.position());
        }
        return phrase.build();
    }

    private static List<QueryToken> analyze(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<QueryToken> tokens = new ArrayList<>();
        try (Analyzer analyzer = PageFields.newAnalyzer();
             TokenStream stream = analyzer.tokenStream(PageFields.BODY, query)) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            PositionIncrementAttribute position = stream.addAttribute(PositionIncrementAttribute.class);
            stream.reset();
            int at = -1;
            while (stream.incrementToken()) {
                at += position.getPositionIncrement();
                tokens.add(new QueryToken(term.toString(), at));
            }
            stream.end();
        } catch (IOException e) {
            throw new IndexException("Cannot read the search text", e);
        }
        return tokens;
    }

    private static List<String> distinctTexts(List<QueryToken> tokens) {
        LinkedHashSet<String> texts = new LinkedHashSet<>();
        for (QueryToken token : tokens) {
            texts.add(token.text());
        }
        return List.copyOf(texts);
    }

    private static int offset(int page, int pageSize) {
        long start = (long) (page - 1) * pageSize;
        if (start > Integer.MAX_VALUE - pageSize) {
            throw new InvalidSearchException("page is too large");
        }
        return (int) start;
    }

    private static SearchPage page(
            String query,
            SearchMode mode,
            int page,
            int pageSize,
            long totalHits,
            List<SearchHit> hits
    ) {
        return new SearchPage(query, mode.wireName(), page, pageSize, totalHits, null, hits);
    }

    private SearchPage scan(String query, SearchMode mode, int page, boolean matchCase, boolean regex) {
        if (query == null || query.isBlank()) {
            throw new InvalidSearchException("Enter a word to search for");
        }
        if (!regex && wordsOf(query).size() > MAX_WORDS) {
            throw new InvalidSearchException("Use at most 32 words");
        }
        int pageSize = properties.getResultsPerPage();
        if (pageSize < 1) {
            throw new IndexException("results-per-page must be at least 1");
        }
        Path live = properties.getIndexDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(live)) {
            throw new InvalidSearchException("No index yet. Rebuild the index first.");
        }
        try (Directory directory = FSDirectory.open(live)) {
            if (!DirectoryReader.indexExists(directory)) {
                throw new InvalidSearchException("No index yet. Rebuild the index first.");
            }
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                List<SearchHit> found = new ArrayList<>();
                for (LeafReaderContext context : reader.leaves()) {
                    LeafReader leaf = context.reader();
                    var liveDocs = leaf.getLiveDocs();
                    var stored = leaf.storedFields();
                    for (int i = 0; i < leaf.maxDoc(); i++) {
                        if (liveDocs != null && !liveDocs.get(i)) {
                            continue;
                        }
                        Document document = stored.document(i);
                        SearchHit hit = scanDocument(document, query, mode, matchCase, regex);
                        if (hit != null) {
                            found.add(hit);
                        }
                    }
                }
                found.sort((left, right) -> {
                    int byCount = Integer.compare(right.matchCount(), left.matchCount());
                    return byCount != 0 ? byCount : left.id().compareTo(right.id());
                });
                long totalMatches = found.stream().mapToLong(SearchHit::matchCount).sum();
                int offset = offset(page, pageSize);
                List<SearchHit> pageHits = offset >= found.size()
                        ? List.of()
                        : found.subList(offset, Math.min(found.size(), offset + pageSize));
                return new SearchPage(query, mode.wireName(), page, pageSize, found.size(), totalMatches, pageHits);
            }
        } catch (IOException e) {
            throw new IndexException("Cannot search index at " + live, e);
        }
    }

    private static SearchHit scanDocument(
            Document document,
            String query,
            SearchMode mode,
            boolean matchCase,
            boolean regex
    ) {
        String title = text(document.get(PageFields.TITLE));
        String body = text(document.get(PageFields.BODY));
        TextScan.Found found = TextScan.inDocument(title, body, query, mode, matchCase, regex);
        if (!found.matched()) {
            return null;
        }
        FormattedSnippet snippet = found.snippet().text().isEmpty()
                ? new FormattedSnippet(preview(body), List.of())
                : found.snippet();
        return new SearchHit(
                text(document.get(PageFields.ID)),
                title,
                found.titleSpans(),
                text(document.get(PageFields.PATH)),
                found.matchCount(),
                found.matchedTerms(),
                snippet.text(),
                snippet.highlights(),
                found.matchCount()
        );
    }

    private static List<String> wordsOf(String query) {
        String trimmed = query.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return List.of(trimmed.split("\\s+"));
    }

    private static String preview(String body) {
        if (body.length() <= PREVIEW_CHARS) {
            return body;
        }
        return body.substring(0, PREVIEW_CHARS);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private record QueryToken(String text, int position) {
    }
}
