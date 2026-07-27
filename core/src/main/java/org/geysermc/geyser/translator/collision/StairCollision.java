/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.collision;

import lombok.EqualsAndHashCode;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.BoundingBox;
import org.geysermc.geyser.level.physics.Direction;

@EqualsAndHashCode(callSuper = true)
@CollisionRemapper(regex = "_stairs$", usesParams = true, passDefaultBoxes = true)
public final class StairCollision extends BlockCollision {
    private final Direction facing;
    public StairCollision(BlockState state, BoundingBox[] defaultBoxes) {
        this(state.getValue(Properties.HORIZONTAL_FACING), defaultBoxes);
    }

    StairCollision(Direction facing, BoundingBox[] defaultBoxes) {
        super(defaultBoxes);
        this.facing = facing;
    }

    @Override
    protected boolean canCorrectPositionUp(int x, int y, int z, BoundingBox playerCollision) {
        // The facing side is the stair's full-height back. A player whose center is
        // still outside that block face must be separated horizontally, not lifted.
        return switch (facing) {
            case NORTH -> playerCollision.getMiddleZ() > z;
            case SOUTH -> playerCollision.getMiddleZ() < z + 1.0D;
            case WEST -> playerCollision.getMiddleX() > x;
            case EAST -> playerCollision.getMiddleX() < x + 1.0D;
            default -> true;
        };
    }
}
