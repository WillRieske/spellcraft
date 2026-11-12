package io.github.willrieske.spellcraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Evoking functional spec numbers its paragraphs, and the code and tests that implement a paragraph name it in a
 * comment: the words "Functional spec" followed by one or more paragraph numbers, each written with a section sign and
 * separated by commas (no ranges). These tests keep the two in step.
 */
class FunctionalSpecReferencesTest {
    private static final Path SPEC = Path.of("docs/superpowers/specs/evoking/evoking-functional-spec.md");
    private static final Path SOURCES = Path.of("src");
    private static final Path THIS_FILE = Path.of(
            "src/test/java/io/github/willrieske/spellcraft",
            FunctionalSpecReferencesTest.class.getSimpleName() + ".java");

    /** A numbered paragraph of the spec starts a line with its number in bold: two asterisks, "3.4", a space. */
    private static final Pattern PARAGRAPH = Pattern.compile("^\\*\\*(\\d+\\.\\d+) ", Pattern.MULTILINE);

    /** One reference list: the marker words, then numbers separated by commas, "and", or a comment's line break. */
    private static final Pattern REFERENCE = Pattern.compile("Functional spec((?:(?:[\\s*/,]|and\\b)*§\\d+\\.\\d+)+)");

    private static final Pattern NUMBER = Pattern.compile("§(\\d+\\.\\d+)");

    private static Set<String> specParagraphs() throws IOException {
        Set<String> paragraphs = new TreeSet<>();
        Matcher matcher = PARAGRAPH.matcher(Files.readString(SPEC, StandardCharsets.UTF_8));
        while (matcher.find()) {
            paragraphs.add(matcher.group(1));
        }
        return paragraphs;
    }

    /** Every paragraph number named under {@code root}, with the files that name it. */
    private static Map<String, Set<String>> references(Path root) throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(THIS_FILE))
                    .toList();
        }
        for (Path file : files) {
            Matcher list = REFERENCE.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (list.find()) {
                Matcher number = NUMBER.matcher(list.group(1));
                while (number.find()) {
                    found.computeIfAbsent(number.group(1), key -> new TreeSet<>())
                            .add(file.toString());
                }
            }
        }
        return found;
    }

    /**
     * When the spec is read, should it have numbered paragraphs: an empty set would make the other two tests pass
     * while checking nothing (a moved file, a changed heading style).
     *
     * <p>Method under test: the spec file's paragraph numbering
     */
    @Test
    void specHasNumberedParagraphs() throws IOException {
        assertThat(specParagraphs()).hasSizeGreaterThan(100);
    }

    /**
     * When a comment names a paragraph, should that paragraph exist in the spec: a renumbered or removed paragraph
     * must not leave comments pointing at nothing, or at a different rule.
     *
     * <p>Method under test: the references in {@code src}
     */
    @Test
    void everyReferenceNamesAnExistingParagraph() throws IOException {
        Set<String> paragraphs = specParagraphs();

        Map<String, Set<String>> dangling = references(SOURCES);
        dangling.keySet().removeAll(paragraphs);

        assertThat(dangling)
                .as("paragraph numbers named in comments that the spec does not have, with the files naming them")
                .isEmpty();
    }

    /**
     * When the spec has a paragraph, should some code under {@code src/main} name it: a rule with no code reference
     * is either not implemented or not findable from the spec.
     *
     * <p>Method under test: the references in {@code src/main}
     */
    @Test
    void everyParagraphIsNamedByCode() throws IOException {
        Set<String> unreferenced = specParagraphs();

        unreferenced.removeAll(references(SOURCES.resolve("main")).keySet());

        assertThat(unreferenced)
                .as("spec paragraphs that no comment under src/main names")
                .isEmpty();
    }
}
