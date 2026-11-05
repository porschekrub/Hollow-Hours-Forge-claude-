package net.minecraftforge.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PlayerInteractEvent extends PlayerEvent {
    private final InteractionHand hand;
    private final BlockPos pos;
    private final Direction face;
    private InteractionResult cancellationResult = InteractionResult.PASS;

    public PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, Direction face) {
        super(player);
        this.hand = hand;
        this.pos = pos;
        this.face = face;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItemStack() {
        return getEntity().getItemInHand(hand);
    }

    public BlockPos getPos() {
        return pos;
    }

    public Direction getFace() {
        return face;
    }

    public Level getLevel() {
        return getEntity().level();
    }

    public InteractionResult getCancellationResult() {
        return cancellationResult;
    }

    public void setCancellationResult(InteractionResult r) {
        cancellationResult = r;
    }

    /** Right click on a block (cancelled: nothing happens; the cancellation result is returned). */
    public static class RightClickBlock extends PlayerInteractEvent {
        private final BlockHitResult hit;

        public RightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
            super(player, hand, hit.getBlockPos(), hit.getDirection());
            this.hit = hit;
        }

        public BlockHitResult getHitVec() {
            return hit;
        }
    }

    /** Right click with an item in the air (cancelled: the item is not used). */
    public static class RightClickItem extends PlayerInteractEvent {
        public RightClickItem(Player player, InteractionHand hand) {
            super(player, hand, player.blockPosition(), null);
        }
    }
}
