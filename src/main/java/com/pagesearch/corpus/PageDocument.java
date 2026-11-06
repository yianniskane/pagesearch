package com.pagesearch.corpus;

import java.nio.file.Path;

/**
 * One text page from the corpus folder.
 *
 * @param id    path relative to the corpus folder, with {@code /} separators
 * @param title file name without the {@code .txt} suffix
 * @param path  absolute path of the file
 * @param body  file text, without a leading byte-order mark
 */
public record PageDocument(String id, String title, Path path, String body) {
}
