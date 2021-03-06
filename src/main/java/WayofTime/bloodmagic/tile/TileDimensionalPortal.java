package WayofTime.bloodmagic.tile;

import WayofTime.bloodmagic.ritual.RitualPortal;
import com.google.common.base.Strings;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public class TileDimensionalPortal extends TileEntity
{
    public String portalID = "";
    public int masterStoneX;
    public int masterStoneY;
    public int masterStoneZ;

    public TileDimensionalPortal()
    {
        ;
    }

    public void readFromNBT(NBTTagCompound tagCompound)
    {
        super.readFromNBT(tagCompound);

        portalID = tagCompound.getString(RitualPortal.PORTAL_ID_TAG);

        masterStoneX = tagCompound.getInteger("masterStoneX");
        masterStoneY = tagCompound.getInteger("masterStoneY");
        masterStoneZ = tagCompound.getInteger("masterStoneZ");
    }

    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound)
    {
        super.writeToNBT(tagCompound);

        tagCompound.setString(RitualPortal.PORTAL_ID_TAG, Strings.isNullOrEmpty(portalID) ? "" : portalID);

        tagCompound.setInteger("masterStoneX", masterStoneX);
        tagCompound.setInteger("masterStoneY", masterStoneY);
        tagCompound.setInteger("masterStoneZ", masterStoneZ);
        return tagCompound;
    }

    public BlockPos getMasterStonePos()
    {
        return new BlockPos(masterStoneX, masterStoneY, masterStoneZ);
    }

    public void setMasterStonePos(BlockPos blockPos)
    {
        this.masterStoneX = blockPos.getX();
        this.masterStoneY = blockPos.getY();
        this.masterStoneZ = blockPos.getZ();
    }
}
