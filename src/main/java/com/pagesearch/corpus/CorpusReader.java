package com.pagesearch.corpus;

import com.pagesearch.config.SearchProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Reads the corpus folder. One UTF-8 {@code .txt} file is one page, including files in subfolders.
 * The returned stream reads a file only when that page is pulled, and the caller closes it.
 */
@Component
public class CorpusReader {

    private static final String EXTENSION = ".txt";

    private final Path corpusDir;

    public CorpusReader(SearchProperties properties) {
        this.corpusDir = properties.getCorpusDir();
    }

    public Stream<PageDocument> pages() {
        Path root = corpusDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new CorpusReadException("Corpus directory does not exist: " + root);
        }
        try {
            return Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && isTextPage(path))
                    .sorted(Comparator.comparing(path -> relativeId(root, path)))
                    .map(path -> read(root, path));
        } catch (IOException e) {
            throw new CorpusReadException("Cannot list corpus directory: " + root, e);
        }
    }

    private static boolean isTextPage(Path path) {
        String name = path.getFileName().toString();
        return name.length() > EXTENSION.length()
                && name.regionMatches(true, name.length() - EXTENSION.length(), EXTENSION, 0, EXTENSION.length());
    }

    private static PageDocument read(Path root, Path file) {
        try {
            String body = Files.readString(file, StandardCharsets.UTF_8);
            if (body.startsWith("\uFEFF")) {
                body = body.substring(1);
            }
            String fileName = file.getFileName().toString();
            String title = fileName.substring(0, fileName.length() - EXTENSION.length());
            return new PageDocument(relativeId(root, file), title, file.toAbsolutePath().normalize(), body);
        } catch (CharacterCodingException e) {
            throw new CorpusReadException("Page is not UTF-8: " + file, e);
        } catch (IOException e) {
            throw new CorpusReadException("Cannot read page " + file, e);
        }
    }

    private static String relativeId(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
