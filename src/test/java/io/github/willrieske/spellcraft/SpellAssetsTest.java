package io.github.willrieske.spellcraft;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.willrieske.spellcraft.spell.rules.SpellDeclarations;
import io.github.willrieske.spellcraft.spell.rules.SpellRules;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * When the lang file is read, it should name both casting keys by their current mapping names and not the old
     * fangs name: a mismatch with {@code EvokingKeys} would show a raw key in Controls (the client class cannot be
     * loaded here, so the names are spelled out).
     *
     * <p>Method under test: the lang file against {@code EvokingKeys}
     */
    @Test
    void castingKeysHaveEnglishNames() throws IOException {
        // Act
        String lang = Files.readString(ASSETS.resolve("lang/en_us.json"));
        // Assert
        assertThat(lang).contains("\"key.spellcraft.fangs_y\"", "\"key.spellcraft.vexes\"");
        assertThat(lang).doesNotContain("\"key.spellcraft.fangs\"");
    }
}
