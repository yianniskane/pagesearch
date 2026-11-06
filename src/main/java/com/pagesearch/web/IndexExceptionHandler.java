package com.pagesearch.web;

import com.pagesearch.corpus.CorpusReadException;
import com.pagesearch.index.IndexException;
import com.pagesearch.index.IndexInProgressException;
import com.pagesearch.search.InvalidSearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class IndexExceptionHandler {

    @ExceptionHandler(CorpusReadException.class)
    public ResponseEntity<Map<String, String>> corpusUnreadable(CorpusReadException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IndexInProgressException.class)
    public ResponseEntity<Map<String, String>> indexInProgress(IndexInProgressException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IndexException.class)
    public ResponseEntity<Map<String, String>> indexFailed(IndexException exception) {
        return ResponseEntity.internalServerError().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSearchException.class)
    public ResponseEntity<Map<String, String>> invalidSearch(InvalidSearchException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
