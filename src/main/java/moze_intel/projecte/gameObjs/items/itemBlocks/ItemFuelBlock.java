package moze_intel.projecte.gameObjs.items.itemBlocks;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;

public class ItemFuelBlock extends ItemBlock
{
	public ItemFuelBlock(Block block) 
	{
		super(block);
		this.setMaxDamage(0);
		this.hasSubtypes = true;
	}
	
	@Nonnull
	@Override
	public String getTranslationKey(ItemStack stack)
	{
		switch (stack.getItemDamage())
		{
			case 0:
				return "tile.pe_fuel_block_0";
			case 1:
				return "tile.pe_fuel_block_1";
			case 2:
				return "tile.pe_fuel_block_2";
			default:
				return "tile.pe_fuel_block_null";
		}
	}
	
	@Override
	public int getMetadata(int meta)
	{
		return meta;
	}
}
