package io.github.willrieske.spellcraft;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.SpellDeclarations;
import io.github.willrieske.spellcraft.spell.rules.SpellRules;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every declared spell ships its assets; a gap would show as the purple-and-black missing texture in game. */
class SpellAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/spellcraft");

    static Stream<String> spellNames() {
        return SpellDeclarations.ALL.stream().map(SpellRules::name);
    }

    private static void assertSquarePng(Path path, int size) throws IOException {
        assertThat(path).exists();
        BufferedImage image = ImageIO.read(path.toFile());
        assertThat(image).as("readable PNG: " + path).isNotNull();
        assertThat(image.getWidth()).as("width of " + path).isEqualTo(size);
        assertThat(image.getHeight()).as("height of " + path).isEqualTo(size);
    }

    @ParameterizedTest
    @MethodSource("spellNames")
    void scrollTextureIs16x16(String name) throws IOException {
        assertSquarePng(ASSETS.resolve("textures/item/" + name + "_scroll.png"), 16);
    }

    @ParameterizedTest
    @MethodSource("spellNames")
    void effectIconIs18x18(String name) throws IOException {
        assertSquarePng(ASSETS.resolve("textures/mob_effect/" + name + ".png"), 18);
    }

    @ParameterizedTest
    @MethodSource("spellNames")
    void scrollHasAnItemDefinitionAndModel(String name) {
        assertThat(ASSETS.resolve("items/" + name + "_scroll.json")).exists();
        assertThat(ASSETS.resolve("models/item/" + name + "_scroll.json")).exists();
    }

    @ParameterizedTest
    @MethodSource("spellNames")
    void scrollAndEffectHaveEnglishNames(String name) throws IOException {
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        assertThat(lang).contains("\"item.spellcraft." + name + "_scroll\"", "\"effect.spellcraft." + name + "\"");
    }

    /**
     * When {@code EvokingKeys.java} is read as text, every casting-key name it spells out should be a key in the lang
     * file, and the old fangs name should be gone: a mismatch would show a raw key in Controls. The client class is
     * never loaded, so the client-only rule holds.
     *
     * <p>Method under test: the lang file against {@code EvokingKeys}
     */
    @Test
    void castingKeysHaveEnglishNames() throws IOException {
        // Arrange
        String keys = Files.readString(
                Path.of("src/main/java/io/github/willrieske/spellcraft/client/evoking/EvokingKeys.java"));
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"(key\\.spellcraft\\.[a-z_]+)\"").matcher(keys);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        // Act & Assert
        assertThat(names).as("key names found in EvokingKeys").hasSizeGreaterThanOrEqualTo(2);
        for (String name : names) {
            assertThat(lang).as("lang key for " + name).contains("\"" + name + "\"");
        }
        assertThat(lang).doesNotContain("\"key.spellcraft.fangs\"");
    }
}
