package com.pagesearch.search;

/**
 * A marked range in a title or snippet. {@code start} is inclusive and {@code end} is exclusive.
 */
public record TextSpan(int start, int end) {
}
