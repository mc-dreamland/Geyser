/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.inventory.recipe;

import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.RecipeData;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.SmithingRecipeDisplay;
import org.geysermc.mcprotocollib.protocol.data.game.recipe.display.slot.SlotDisplay;

import java.util.List;

public record GeyserSmithingRecipe(SlotDisplay template,
                                   SlotDisplay base,
                                   SlotDisplay addition,
                                   SlotDisplay result,
                                   List<RecipeData> recipeData) implements GeyserRecipe {
    public GeyserSmithingRecipe(SlotDisplay template, SlotDisplay base, SlotDisplay addition, SlotDisplay result) {
        this(template, base, addition, result, List.of());
    }

    public GeyserSmithingRecipe(SmithingRecipeDisplay display, List<RecipeData> recipeData) {
        this(display.template(), display.base(), display.addition(), display.result(), recipeData);
    }

    @Override
    public boolean isShaped() {
        return false;
    }
}
