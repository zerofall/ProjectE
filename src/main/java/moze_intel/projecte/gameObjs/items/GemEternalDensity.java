package moze_intel.projecte.gameObjs.items;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import com.google.common.collect.Lists;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.item.IAlchBagItem;
import moze_intel.projecte.api.item.IAlchChestItem;
import moze_intel.projecte.api.item.IModeChanger;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.gameObjs.tiles.AlchChestTile;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PEKeybind;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Optional.Interface(iface = "baubles.api.IBauble", modid = "baubles")
public class GemEternalDensity extends ItemPE implements IAlchBagItem, IAlchChestItem, IModeChanger, IBauble
{
	public GemEternalDensity()
	{
		this.setTranslationKey("gem_density");
		this.setMaxStackSize(1);
		this.addPropertyOverride(ACTIVE_NAME, ACTIVE_GETTER);
	}
	
	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) 
	{
		if (world.isRemote || !(entity instanceof EntityPlayer))
		{
			return;
		}
		
		condense(stack, entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP));
	}

	/**
	 * @return Whether the inventory was changed
	 */
	private static boolean condense(ItemStack gem, IItemHandler inv)
	{
		if (!ItemHelper.getOrCreateCompound(gem).getBoolean(TAG_ACTIVE) || ItemPE.getEmc(gem) >= Constants.TILE_MAX_EMC)
		{
			return false;
		}

		boolean hasChanged = false;
		boolean isWhitelist = isWhitelistMode(gem);
		List<ItemStack> whitelist = getWhitelist(gem);
		
		ItemStack target = getTarget(gem);
		
		for (int i = 0; i < inv.getSlots(); i++)
		{
			ItemStack s = inv.getStackInSlot(i);
			
			if (s.isEmpty()
					|| !EMCHelper.doesItemHaveEmc(s) || s.getMaxStackSize() == 1
					|| EMCHelper.getEmcValue(s) >= EMCHelper.getEmcValue(target)
					|| inv.extractItem(i, s.getCount() == 1 ? 1 : s.getCount() / 2, true).isEmpty())
			{
				continue;
			}

			if ((isWhitelist && listContains(whitelist, s)) || (!isWhitelist && !listContains(whitelist, s)))
			{
				ItemStack copy = inv.extractItem(i, s.getCount() == 1 ? 1 : s.getCount() / 2, false);

				addToList(gem, copy);
				
				ItemPE.addEmcToStack(gem, EMCHelper.getEmcValue(copy) * copy.getCount());
				hasChanged = true;
				break;
			}
		}
		
		long value = EMCHelper.getEmcValue(target);

		if (!EMCHelper.doesItemHaveEmc(target))
		{
			return hasChanged;
		}

		while (getEmc(gem) >= value)
		{
			ItemStack remain = ItemHandlerHelper.insertItemStacked(inv, target.copy(), false);

			if (!remain.isEmpty())
			{
				return false;
			}
			
			ItemPE.removeEmc(gem, value);
			setItems(gem, new ArrayList<>());
			hasChanged = true;
		}

		return hasChanged;
	}
	
	@Nonnull
	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, @Nonnull EnumHand hand)
	{
		ItemStack stack = player.getHeldItem(hand);
		if (!world.isRemote)
		{
			if (player.isSneaking())
			{
				if (ItemHelper.getOrCreateCompound(stack).getBoolean(TAG_ACTIVE))
				{
					List<ItemStack> items = getItems(stack);
					
					if (!items.isEmpty())
					{
						WorldHelper.createLootDrop(items, world, player.posX, player.posY, player.posZ);

						setItems(stack, new ArrayList<>());
						ItemPE.setEmc(stack, 0);
					}

					stack.getTagCompound().setBoolean(TAG_ACTIVE, false);
				}
				else
				{
					stack.getTagCompound().setBoolean(TAG_ACTIVE, true);
				}
			}
			else
			{
				player.openGui(PECore.instance, Constants.ETERNAL_DENSITY_GUI, world, hand == EnumHand.MAIN_HAND ? 0 : 1, -1, -1);
			}
		}
		
		return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
	}
	
	private String getTargetName(ItemStack stack)
	{
		switch(ItemHelper.getOrCreateCompound(stack).getByte("Target"))
		{
			case 0:
				return "item.ingotIron.name";
			case 1:
				return "item.ingotGold.name";
			case 2:
				return "item.diamond.name";
			case 3:
				return "item.pe_matter_dark.name";
			case 4:
				return "item.pe_matter_red.name";
			default:
				return "INVALID";
		}
	}
	
	private static ItemStack getTarget(ItemStack stack)
	{
		byte target = ItemHelper.getOrCreateCompound(stack).getByte("Target");
		switch (target)
		{
			case 0:
				return new ItemStack(Items.IRON_INGOT);
			case 1:
				return new ItemStack(Items.GOLD_INGOT);
			case 2:
				return new ItemStack(Items.DIAMOND);
			case 3:
				return new ItemStack(ObjHandler.matter, 1, 0);
			case 4:
				return new ItemStack(ObjHandler.matter, 1, 1);
			default:
				PECore.LOGGER.fatal("Invalid target for gem of eternal density: {}", target);
				return ItemStack.EMPTY;
		}
	}
	
	private static void setItems(ItemStack stack, List<ItemStack> list)
	{
		NBTTagList tList = new NBTTagList();
		
		for (ItemStack s : list)
		{
			NBTTagCompound nbt = new NBTTagCompound();
			s.writeToNBT(nbt);
			tList.appendTag(nbt);
		}
		
		ItemHelper.getOrCreateCompound(stack).setTag("Consumed", tList);
	}
	
	private static List<ItemStack> getItems(ItemStack stack)
	{
		List<ItemStack> list = new ArrayList<>();
		NBTTagList tList = ItemHelper.getOrCreateCompound(stack).getTagList("Consumed", NBT.TAG_COMPOUND);
		
		for (int i = 0; i < tList.tagCount(); i++)
		{
			list.add(new ItemStack(tList.getCompoundTagAt(i)));
		}
		
		return list;
	}
	
	private static void addToList(ItemStack gem, ItemStack stack)
	{
		List<ItemStack> list = getItems(gem);
		
		addToList(list, stack);
		
		setItems(gem, list);
	}
	
	private static void addToList(List<ItemStack> list, ItemStack stack)
	{
		boolean hasFound = false;

		for (ItemStack s : list)
		{
			if (s.getCount() < s.getMaxStackSize() && ItemHelper.areItemStacksEqual(s, stack))
			{
				int remain = s.getMaxStackSize() - s.getCount();

				if (stack.getCount() <= remain)
				{
					s.grow(stack.getCount());
					hasFound = true;
					break;
				}
				else
				{
					s.grow(remain);
					stack.shrink(remain);
				}
			}
		}

		if (!hasFound)
		{
			list.add(stack);
		}
	}
	
	private static boolean isWhitelistMode(ItemStack stack)
	{
		return ItemHelper.getOrCreateCompound(stack).getBoolean("Whitelist");
	}
	
	private static List<ItemStack> getWhitelist(ItemStack stack)
	{
		List<ItemStack> result = new ArrayList<>();
		NBTTagList list = ItemHelper.getOrCreateCompound(stack).getTagList("Items", NBT.TAG_COMPOUND);
		
		for (int i = 0; i < list.tagCount(); i++)
		{
			result.add(new ItemStack(list.getCompoundTagAt(i)));
		}
		
		return result;
	}
	
	private static boolean listContains(List<ItemStack> list, ItemStack stack)
	{
		for (ItemStack s : list)
		{
			if (ItemHelper.areItemStacksEqual(s, stack))
			{
				return true;
			}
		}
		
		return false;
	}

	@Override
	public byte getMode(@Nonnull ItemStack stack)
	{
		return ItemHelper.getOrCreateCompound(stack).getByte("Target");
	}

	@Override
	public boolean changeMode(@Nonnull EntityPlayer player, @Nonnull ItemStack stack, EnumHand hand)
	{
		byte oldMode = getMode(stack);

		if (oldMode == 4)
		{
			ItemHelper.getOrCreateCompound(stack).setByte("Target", (byte) 0);
		}
		else
		{
			ItemHelper.getOrCreateCompound(stack).setByte("Target", (byte) (oldMode + 1));
		}

		player.sendMessage(new TextComponentTranslation("pe.gemdensity.mode_switch").appendText(" ").appendSibling(new TextComponentTranslation(getTargetName(stack))));
		return true;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World world, List<String> list, ITooltipFlag flags)
	{
		list.add(I18n.format("pe.gemdensity.tooltip1"));
		
		if (stack.hasTagCompound())
		{
			list.add(I18n.format("pe.gemdensity.tooltip2", I18n.format(getTargetName(stack))));
		}
		list.add(I18n.format("pe.gemdensity.tooltip3", ClientKeyHelper.getKeyName(PEKeybind.MODE)));
		list.add(I18n.format("pe.gemdensity.tooltip4"));
		list.add(I18n.format("pe.gemdensity.tooltip5"));
	}
	
	@Override
	@Optional.Method(modid = "baubles")
	public baubles.api.BaubleType getBaubleType(ItemStack itemstack)
	{
		return BaubleType.RING;
	}

	@Override
	@Optional.Method(modid = "baubles")
	public void onWornTick(ItemStack stack, EntityLivingBase player) 
	{
		this.onUpdate(stack, player.getEntityWorld(), player, 0, false);
	}

	@Override
	@Optional.Method(modid = "baubles")
	public void onEquipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "baubles")
	public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {}

	@Override
	@Optional.Method(modid = "baubles")
	public boolean canEquip(ItemStack itemstack, EntityLivingBase player) 
	{
		return true;
	}

	@Override
	@Optional.Method(modid = "baubles")
	public boolean canUnequip(ItemStack itemstack, EntityLivingBase player) 
	{
		return true;
	}

	@Override
	public void updateInAlchChest(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull ItemStack stack)
	{
		if (!world.isRemote && ItemHelper.getOrCreateCompound(stack).getBoolean(TAG_ACTIVE))
		{
			TileEntity te = world.getTileEntity(pos);
			if (te instanceof AlchChestTile)
			{
				AlchChestTile tile = (AlchChestTile) te;
				condense(stack, tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null));
				tile.markDirty();
			}
		}
	}

	@Override
	public boolean updateInAlchBag(@Nonnull IItemHandler inv, @Nonnull EntityPlayer player, @Nonnull ItemStack stack)
	{
		return !player.getEntityWorld().isRemote && condense(stack, inv);
	}
}
