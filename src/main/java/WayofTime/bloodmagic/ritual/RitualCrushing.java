package WayofTime.bloodmagic.ritual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import WayofTime.bloodmagic.api.Constants;
import WayofTime.bloodmagic.api.recipe.AlchemyTableRecipe;
import WayofTime.bloodmagic.api.registry.AlchemyTableRecipeRegistry;
import WayofTime.bloodmagic.api.ritual.AreaDescriptor;
import WayofTime.bloodmagic.api.ritual.EnumRuneType;
import WayofTime.bloodmagic.api.ritual.IMasterRitualStone;
import WayofTime.bloodmagic.api.ritual.Ritual;
import WayofTime.bloodmagic.api.ritual.RitualComponent;
import WayofTime.bloodmagic.api.saving.SoulNetwork;
import WayofTime.bloodmagic.api.soul.EnumDemonWillType;
import WayofTime.bloodmagic.api.util.helper.NetworkHelper;
import WayofTime.bloodmagic.demonAura.WorldDemonWillHandler;
import WayofTime.bloodmagic.registry.ModBlocks;
import WayofTime.bloodmagic.util.Utils;

public class RitualCrushing extends Ritual
{
    public static final String CRUSHING_RANGE = "crushingRange";
    public static final String CHEST_RANGE = "chest";

    public static double rawWillDrain = 0.5;
    public static double steadfastWillDrain = 0.2;
    public static double destructiveWillDrain = 0.2;

    public static Map<ItemStack, Integer> cuttingFluidLPMap = new HashMap<ItemStack, Integer>();
    public static Map<ItemStack, Double> cuttingFluidWillMap = new HashMap<ItemStack, Double>();

    public RitualCrushing()
    {
        super("ritualCrushing", 0, 5000, "ritual." + Constants.Mod.MODID + ".crushingRitual");
        addBlockRange(CRUSHING_RANGE, new AreaDescriptor.Rectangle(new BlockPos(-1, -3, -1), 3));
        addBlockRange(CHEST_RANGE, new AreaDescriptor.Rectangle(new BlockPos(0, 1, 0), 1));

        setMaximumVolumeAndDistanceOfRange(CRUSHING_RANGE, 50, 10, 10);
        setMaximumVolumeAndDistanceOfRange(CHEST_RANGE, 1, 3, 3);
    }

    public static void registerCuttingFluid(ItemStack stack, int lpDrain, double willDrain)
    {
        cuttingFluidLPMap.put(stack, lpDrain);
        cuttingFluidWillMap.put(stack, willDrain);
    }

    @Override
    public void performRitual(IMasterRitualStone masterRitualStone)
    {
        World world = masterRitualStone.getWorldObj();
        SoulNetwork network = NetworkHelper.getSoulNetwork(masterRitualStone.getOwner());
        int currentEssence = network.getCurrentEssence();

        if (currentEssence < getRefreshCost())
        {
            network.causeNausea();
            return;
        }

        BlockPos pos = masterRitualStone.getBlockPos();
        AreaDescriptor chestRange = getBlockRange(CHEST_RANGE);
        TileEntity tile = world.getTileEntity(chestRange.getContainedPositions(pos).get(0));

        if (tile != null && Utils.getNumberOfFreeSlots(tile, EnumFacing.DOWN) < 1)
        {
            return;
        }

        List<EnumDemonWillType> willConfig = masterRitualStone.getActiveWillConfig();

        double steadfastWill = this.getWillRespectingConfig(world, pos, EnumDemonWillType.STEADFAST, willConfig);
        double corrosiveWill = this.getWillRespectingConfig(world, pos, EnumDemonWillType.CORROSIVE, willConfig);
        double destructiveWill = this.getWillRespectingConfig(world, pos, EnumDemonWillType.DESTRUCTIVE, willConfig);

        boolean isSilkTouch = steadfastWill >= steadfastWillDrain;
        boolean useCuttingFluid = corrosiveWill > 0;

        int fortune = destructiveWill > 0 ? 3 : 0;

        AreaDescriptor crushingRange = getBlockRange(CRUSHING_RANGE);

        for (BlockPos newPos : crushingRange.getContainedPositions(pos))
        {
            if (world.isAirBlock(newPos))
            {
                continue;
            }

            IBlockState state = world.getBlockState(newPos);
            Block block = state.getBlock();
            if (block.equals(ModBlocks.ritualController) || block.equals(ModBlocks.ritualStone) || block.getBlockHardness(state, world, newPos) == -1.0F || Utils.isBlockLiquid(state))
            {
                continue;
            }

            boolean isBlockClaimed = false;
            if (useCuttingFluid)
            {
                ItemStack checkStack = block.getItem(world, newPos, state);
                if (checkStack == null)
                {
                    continue;
                }

                ItemStack copyStack = checkStack.copy();

                for (Entry<ItemStack, Integer> entry : cuttingFluidLPMap.entrySet())
                {
                    ItemStack cuttingStack = entry.getKey();
                    int lpDrain = entry.getValue();
                    double willDrain = cuttingFluidWillMap.containsKey(cuttingStack) ? cuttingFluidWillMap.get(cuttingStack) : 0;

                    if (corrosiveWill < willDrain || currentEssence < lpDrain + getRefreshCost())
                    {
                        continue;
                    }

                    cuttingStack = cuttingStack.copy();
                    List<ItemStack> input = new ArrayList<ItemStack>();
                    input.add(cuttingStack);
                    input.add(copyStack);

                    AlchemyTableRecipe recipe = AlchemyTableRecipeRegistry.getMatchingRecipe(input, world, pos);
                    if (recipe == null)
                    {
                        continue;
                    }

                    ItemStack result = recipe.getRecipeOutput(input);
                    if (result == null)
                    {
                        continue;
                    }

                    if (tile != null)
                        result = Utils.insertStackIntoTile(result, tile, EnumFacing.DOWN);
                    else
                        Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, result);

                    if (result != null && result.stackSize > 0)
                    {
                        Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, result);
                    }

                    WorldDemonWillHandler.drainWill(world, pos, EnumDemonWillType.CORROSIVE, willDrain, true);
                    corrosiveWill -= willDrain;

                    network.syphon(lpDrain);
                    currentEssence -= lpDrain;

                    isBlockClaimed = true;
                }
            }

            if (!isBlockClaimed && isSilkTouch && block.canSilkHarvest(world, newPos, state, null))
            {
                ItemStack checkStack = block.getItem(world, newPos, state);
                if (checkStack == null)
                {
                    continue;
                }

                ItemStack copyStack = checkStack.copy();

                if (steadfastWill >= steadfastWillDrain)
                {
                    WorldDemonWillHandler.drainWill(world, pos, EnumDemonWillType.STEADFAST, steadfastWillDrain, true);
                    steadfastWill -= steadfastWillDrain;
                } else
                {
                    continue;
                }

                if (tile != null)
                    copyStack = Utils.insertStackIntoTile(copyStack, tile, EnumFacing.DOWN);
                else
                    Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, copyStack);

                if (copyStack != null && copyStack.stackSize > 0)
                {
                    Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, copyStack);
                }
            } else if (!isBlockClaimed)
            {
                if (fortune > 0 && destructiveWill < destructiveWillDrain)
                {
                    fortune = 0;
                }

                List<ItemStack> stackList = block.getDrops(world, newPos, state, fortune);

                if (stackList != null && !stackList.isEmpty())
                {
                    for (ItemStack item : stackList)
                    {
                        ItemStack copyStack = ItemStack.copyItemStack(item);

                        if (tile != null)
                        {
                            copyStack = Utils.insertStackIntoTile(copyStack, tile, EnumFacing.DOWN);
                        } else
                        {
                            Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, copyStack);
                            continue;
                        }
                        if (copyStack != null && copyStack.stackSize > 0)
                        {
                            Utils.spawnStackAtBlock(world, pos, EnumFacing.UP, copyStack);
                        }
                    }

                    if (fortune > 0)
                    {
                        WorldDemonWillHandler.drainWill(world, pos, EnumDemonWillType.DESTRUCTIVE, destructiveWillDrain, true);
                        destructiveWill -= destructiveWillDrain;
                    }
                }
            }

            world.destroyBlock(newPos, false);
            network.syphon(getRefreshCost());

            break;
        }
    }

    @Override
    public int getRefreshTime()
    {
        return 40;
    }

    @Override
    public int getRefreshCost()
    {
        return 7;
    }

    @Override
    public ArrayList<RitualComponent> getComponents()
    {
        ArrayList<RitualComponent> components = new ArrayList<RitualComponent>();

        this.addParallelRunes(components, 1, 0, EnumRuneType.EARTH);
        this.addParallelRunes(components, 2, 0, EnumRuneType.FIRE);
        this.addCornerRunes(components, 2, 0, EnumRuneType.DUSK);
        this.addParallelRunes(components, 2, 1, EnumRuneType.AIR);

        return components;
    }

    @Override
    public ITextComponent[] provideInformationOfRitualToPlayer(EntityPlayer player)
    {
        return new ITextComponent[] { new TextComponentTranslation(this.getUnlocalizedName() + ".info"), new TextComponentTranslation(this.getUnlocalizedName() + ".destructive.info"), new TextComponentTranslation(this.getUnlocalizedName() + ".corrosive.info"), new TextComponentTranslation(this.getUnlocalizedName() + ".steadfast.info") };
    }

    @Override
    public Ritual getNewCopy()
    {
        return new RitualCrushing();
    }
}
