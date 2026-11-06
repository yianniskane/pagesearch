package com.pagesearch.search;

import org.apache.lucene.search.uhighlight.Passage;
import org.apache.lucene.search.uhighlight.PassageFormatter;

import java.util.ArrayList;
import java.util.List;

final class SnippetFormatter extends PassageFormatter {

    static final SnippetFormatter INSTANCE = new SnippetFormatter();

    private SnippetFormatter() {
    }

    @Override
    public FormattedSnippet format(Passage[] passages, String content) {
        if (passages == null || passages.length == 0 || passages[0] == null || content == null) {
            return FormattedSnippet.EMPTY;
        }
        Passage passage = passages[0];
        int start = Math.max(0, passage.getStartOffset());
        int end = Math.min(content.length(), passage.getEndOffset());
        if (end <= start) {
            return FormattedSnippet.EMPTY;
        }
        String text = content.substring(start, end);
        List<TextSpan> spans = new ArrayList<>();
        int[] starts = passage.getMatchStarts();
        int[] ends = passage.getMatchEnds();
        for (int i = 0; i < passage.getNumMatches(); i++) {
            int matchStart = starts[i] - start;
            int matchEnd = ends[i] - start;
            if (matchStart >= 0 && matchEnd <= text.length() && matchStart < matchEnd) {
                spans.add(new TextSpan(matchStart, matchEnd));
            }
        }
        return new FormattedSnippet(text, List.copyOf(spans));
    }
}
