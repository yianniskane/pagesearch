package com.pagesearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "page-search")
public class SearchProperties {

    /**
     * Folder of source pages. One UTF-8 text file is one page.
     */
    private Path corpusDir = Path.of("corpus");

    /**
     * Folder where the Lucene index is stored.
     */
    private Path indexDir = Path.of("index");

    /**
     * Hits returned for one search page.
     */
    private int resultsPerPage = 20;

    public Path getCorpusDir() {
        return corpusDir;
    }

    public void setCorpusDir(Path corpusDir) {
        this.corpusDir = corpusDir;
    }

    public Path getIndexDir() {
        return indexDir;
    }

    public void setIndexDir(Path indexDir) {
        this.indexDir = indexDir;
    }

    public int getResultsPerPage() {
        return resultsPerPage;
    }

    public void setResultsPerPage(int resultsPerPage) {
        this.resultsPerPage = resultsPerPage;
    }
}
