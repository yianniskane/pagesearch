package com.pagesearch.corpus;

import com.pagesearch.config.SearchProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusReaderTest {

    @TempDir
    Path corpusDir;

    @Test
    void readsTextPagesInSubfoldersAndSkipsOtherFiles() throws IOException {
        Files.writeString(corpusDir.resolve("b.txt"), "second");
        Files.createDirectories(corpusDir.resolve("notes"));
        Files.writeString(corpusDir.resolve("notes/a.txt"), "first — page");
        Files.writeString(corpusDir.resolve("notes/picture.png"), "not a page");
        Files.writeString(corpusDir.resolve(".gitkeep"), "");
        Files.write(corpusDir.resolve("notes/bom.TXT"), bom("hello"));

        List<PageDocument> pages = read(corpusDir);

        assertEquals(List.of("b.txt", "notes/a.txt", "notes/bom.TXT"),
                pages.stream().map(PageDocument::id).toList());
        assertEquals(List.of("b", "a", "bom"),
                pages.stream().map(PageDocument::title).toList());
        assertEquals("second", pages.get(0).body());
        assertEquals("first — page", pages.get(1).body());
        assertEquals("hello", pages.get(2).body());
        assertTrue(pages.get(1).path().endsWith(Path.of("notes", "a.txt")));
    }

    @Test
    void emptyDirectoryHasNoPages() {
        assertEquals(List.of(), read(corpusDir));
    }

    @Test
    void missingDirectoryFails() {
        Path missing = corpusDir.resolve("absent");
        CorpusReadException error = assertThrows(CorpusReadException.class, () -> read(missing));
        assertTrue(error.getMessage().contains(missing.toAbsolutePath().normalize().toString()));
    }

    @Test
    void pageThatIsNotUtf8Fails() throws IOException {
        Files.write(corpusDir.resolve("bad.txt"), new byte[] {(byte) 0xFF, 0x41});

        CorpusReadException error = assertThrows(CorpusReadException.class, () -> read(corpusDir));
        assertTrue(error.getMessage().contains("bad.txt"));
    }

    private static List<PageDocument> read(Path corpusDir) {
        SearchProperties properties = new SearchProperties();
        properties.setCorpusDir(corpusDir);
        try (var pages = new CorpusReader(properties).pages()) {
            return pages.toList();
        }
    }

    private static byte[] bom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        return withBom;
    }
}
