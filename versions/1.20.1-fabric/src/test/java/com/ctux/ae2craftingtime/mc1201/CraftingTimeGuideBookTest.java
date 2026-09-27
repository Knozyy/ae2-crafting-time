package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CraftingTimeGuideBookTest {
    @Test
    void onlyGuideRecipeMarksVanillaBook() {
        try (var resource = getClass().getResourceAsStream("/data/ae2craftingtime/recipes/guide_book.json")) {
            assertNotNull(resource);
            var recipe = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:book", recipe.getAsJsonObject("result").get("item").getAsString());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(CraftingTimeGuideBook.shouldMark("ae2craftingtime:guide_book", true));
        assertFalse(CraftingTimeGuideBook.shouldMark("minecraft:book", true));
        assertFalse(CraftingTimeGuideBook.shouldMark("ae2craftingtime:guide_book", false));
        assertTrue(CraftingTimeGuideBook.shouldOpen(true, true));
        assertFalse(CraftingTimeGuideBook.shouldOpen(true, false));
        assertFalse(CraftingTimeGuideBook.shouldOpen(false, true));
    }
}
