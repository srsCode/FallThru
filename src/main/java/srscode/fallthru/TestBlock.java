package srscode.fallthru;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

class TestBlock extends Block
{
    static final String NAME        = "ft_block";
    static final String RESLOC_NAME = FallThru.MOD_ID + ":" + NAME;

    TestBlock()
    {
        super(Block.Properties.create(Material.SNOW).sound(SoundType.SNOW).lootFrom(Blocks.SNOW_BLOCK).hardnessAndResistance(0.5f).variableOpacity());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onEntityCollision(final BlockState state, final World worldIn, final BlockPos pos, final Entity entityIn)
    {
        if (worldIn.isRemote() && entityIn instanceof PlayerEntity && worldIn.getGameTime() % 60 == 0) {
            FallThru.LOGGER.debug(FallThru.MARKER_DEBUG, "Native collision handling of {} @ {}", RESLOC_NAME, pos);
        }
        super.onEntityCollision(state, worldIn, pos, entityIn);
    }
}
