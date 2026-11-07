package com.breachprotocol.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/** Walking that plans through the facility's doors a person is cleared for (they open them on arrival). */
public class StaffNavigation extends GroundPathNavigation {
    public StaffNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setCanPassDoors(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new Evaluator();
        nodeEvaluator.setCanPassDoors(true);
        nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    /** Treats a closed door this person may open as an open doorway. */
    static final class Evaluator extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext ctx, int x, int y, int z) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState st = ctx.getBlockState(p);
            if (Doors.isDoor(st) && !Doors.isOpen(st) && mob != null && Doors.may(mob, ctx.level(), p)) {
                BlockState below = ctx.getBlockState(p.below());
                return Doors.isDoor(below) ? PathType.OPEN : PathType.WALKABLE;
            }
            return super.getPathType(ctx, x, y, z);
        }
    }
}
