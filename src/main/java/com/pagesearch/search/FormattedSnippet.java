package com.pagesearch.search;

import java.util.List;

record FormattedSnippet(String text, List<TextSpan> highlights) {

    static final FormattedSnippet EMPTY = new FormattedSnippet("", List.of());
}
