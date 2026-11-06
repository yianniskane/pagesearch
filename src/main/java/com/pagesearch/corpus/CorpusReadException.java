package com.pagesearch.corpus;

public class CorpusReadException extends RuntimeException {

    public CorpusReadException(String message) {
        super(message);
    }

    public CorpusReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
