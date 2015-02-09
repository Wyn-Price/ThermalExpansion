package cofh.thermalexpansion.block.machine;

import cofh.api.energy.IEnergyContainerItem;
import cofh.lib.util.helpers.EnergyHelper;
import cofh.lib.util.helpers.ItemHelper;
import cofh.lib.util.helpers.MathHelper;
import cofh.lib.util.helpers.ServerHelper;
import cofh.mod.updater.ModVersion;
import cofh.thermalexpansion.ThermalExpansion;
import cofh.thermalexpansion.gui.client.machine.GuiCharger;
import cofh.thermalexpansion.gui.container.machine.ContainerCharger;
import cofh.thermalexpansion.util.crafting.ChargerManager;
import cofh.thermalexpansion.util.crafting.ChargerManager.RecipeCharger;
import cpw.mods.fml.common.registry.GameRegistry;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class TileCharger extends TileMachineBase {

	static final int TYPE = BlockMachine.Types.CHARGER.ordinal();

	public static void initialize() {

		defaultSideConfig[TYPE] = new SideConfig();
		defaultSideConfig[TYPE].numGroup = 3;
		defaultSideConfig[TYPE].slotGroups = new int[][] { {}, { 0 }, { 2 } };
		defaultSideConfig[TYPE].allowInsertion = new boolean[] { false, true, false };
		defaultSideConfig[TYPE].allowExtraction = new boolean[] { false, true, true };
		defaultSideConfig[TYPE].sideTex = new int[] { 0, 1, 4 };
		defaultSideConfig[TYPE].defaultSides = new byte[] { 1, 1, 2, 2, 2, 2 };

		String category = "Machine.Charger";
		int basePower = MathHelper.clampI(ThermalExpansion.config.get(category, "BasePower", 10000), 100, 20000);
		ThermalExpansion.config.set(category, "BasePower", basePower);
		defaultEnergyConfig[TYPE] = new EnergyConfig();
		defaultEnergyConfig[TYPE].setParams(1, basePower, Math.max(480000, basePower * 60));

		GameRegistry.registerTileEntity(TileCharger.class, "thermalexpansion.Charger");
	}

	int outputTracker;
	IEnergyContainerItem containerItem = null;

	public TileCharger() {

		super();

		inventory = new ItemStack[1 + 1 + 1 + 1];
	}

	@Override
	public int getType() {

		return TYPE;
	}

	@Override
	public void updateEntity() {

		if (ServerHelper.isClientWorld(worldObj)) {
			if (inventory[1] == null) {
				processRem = 0;
				containerItem = null;
			} else if (EnergyHelper.isEnergyContainerItem(inventory[1])) {
				containerItem = (IEnergyContainerItem) inventory[1].getItem();
			}
			return;
		}
		if (containerItem == null) {
			if (EnergyHelper.isEnergyContainerItem(inventory[1])) {
				updateContainerItem();
			}
		}
		if (containerItem != null) {
			boolean curActive = isActive;
			processContainerItem();
			updateIfChanged(curActive);
			chargeEnergy();
		} else {
			super.updateEntity();
		}
	}

	@Override
	protected int calcEnergy() {

		if (!isActive) {
			return 0;
		}
		int power = 0;

		if (energyStorage.getEnergyStored() > energyConfig.maxPowerLevel) {
			power = energyConfig.maxPower;
		} else if (energyStorage.getEnergyStored() < energyConfig.energyRamp) {
			power = energyConfig.minPower;
		} else {
			power = energyStorage.getEnergyStored() / energyConfig.energyRamp;
		}
		return containerItem != null ? Math.min(power, containerItem.receiveEnergy(inventory[1], power, true)) : power;
	}

	@Override
	protected int getMaxInputSlot() {

		// This is a hack to prevent super() logic from working.
		return -1;
	}

	@Override
	protected boolean canStart() {

		if (inventory[0] == null) {
			return false;
		}
		if (EnergyHelper.isEnergyContainerItem(inventory[0])) {
			inventory[1] = ItemHelper.cloneStack(inventory[0], 1);
			inventory[0].stackSize--;

			if (inventory[0].stackSize <= 0) {
				inventory[0] = null;
			}
		}
		RecipeCharger recipe = ChargerManager.getRecipe(inventory[0]);

		if (recipe == null || energyStorage.getEnergyStored() < recipe.getEnergy()) {
			return false;
		}
		if (inventory[0].stackSize < recipe.getInput().stackSize) {
			return false;
		}
		ItemStack output = recipe.getOutput();

		if (inventory[2] == null) {
			return true;
		}
		if (!inventory[2].isItemEqual(output)) {
			return false;
		}
		return inventory[2].stackSize + output.stackSize <= output.getMaxStackSize();
	}

	@Override
	protected boolean hasValidInput() {

		if (containerItem != null) {
			return true;
		}
		RecipeCharger recipe = ChargerManager.getRecipe(inventory[1]);
		return recipe == null ? false : recipe.getInput().stackSize <= inventory[1].stackSize;
	}

	@Override
	protected void processStart() {

		RecipeCharger recipe = ChargerManager.getRecipe(inventory[0]);
		processMax = recipe.getEnergy();
		processRem = processMax;

		inventory[1] = ItemHelper.cloneStack(inventory[0], recipe.getInput().stackSize);
		inventory[0].stackSize -= recipe.getInput().stackSize;

		if (inventory[0].stackSize <= 0) {
			inventory[0] = null;
		}
	}

	@Override
	protected void processFinish() {

		RecipeCharger recipe = ChargerManager.getRecipe(inventory[1]);
		ItemStack output = recipe.getOutput();
		if (inventory[2] == null) {
			inventory[2] = output;
		} else {
			inventory[2].stackSize += output.stackSize;
		}
		inventory[1] = null;
	}

	@Override
	protected void transferProducts() {

		if (!augmentAutoTransfer) {
			return;
		}
		if (containerItem != null) {
			if (inventory[2] == null) {
				inventory[2] = ItemHelper.cloneStack(inventory[1], 1);
				inventory[1] = null;
				containerItem = null;
			} else {
				if (inventory[1].getMaxStackSize() > 1 && ItemHelper.itemsIdentical(inventory[1], inventory[2])
						&& inventory[2].stackSize + 1 <= inventory[2].getMaxStackSize()) {
					inventory[2].stackSize++;
					inventory[1] = null;
					containerItem = null;
				}
			}
		}
		if (containerItem == null && EnergyHelper.isEnergyContainerItem(inventory[0])) {
			inventory[1] = ItemHelper.cloneStack(inventory[0], 1);
			inventory[0].stackSize--;

			if (inventory[0].stackSize <= 0) {
				inventory[0] = null;
			}
		}
		int side;
		for (int i = outputTracker + 1; i <= outputTracker + 6; i++) {
			side = i % 6;

			if (sideCache[side] == 2) {
				if (transferItem(2, 4, side)) {
					outputTracker = side;
					break;
				}
			}
		}
	}

	protected void processContainerItem() {

		if (isActive) {
			updateContainerCharge();
			if (!redstoneControlOrDisable()) {
				isActive = false;
				wasActive = true;
				tracker.markTime(worldObj);
			} else {
				if (containerItem == null) {
					if (EnergyHelper.isEnergyContainerItem(inventory[1])) {
						updateContainerItem();
						isActive = true;
					} else {
						isActive = false;
						wasActive = true;
						tracker.markTime(worldObj);
					}
				}
			}
		} else if (redstoneControlOrDisable()) {
			if (timeCheck()) {
				transferProducts();
			}
			if (containerItem == null) {
				if (EnergyHelper.isEnergyContainerItem(inventory[1])) {
					updateContainerItem();
				}
			}
			if (containerItem != null) {
				isActive = true;
			}
		}
	}

	protected void updateContainerItem() {

		containerItem = (IEnergyContainerItem) inventory[1].getItem();

		if (containerItem != null) {
			processMax = containerItem.getMaxEnergyStored(inventory[1]);
			processRem = processMax - containerItem.getEnergyStored(inventory[1]);
		}
	}

	protected void updateContainerCharge() {

		int energy = Math.min(energyStorage.getEnergyStored(), calcEnergy());
		int received = energyStorage.extractEnergy(containerItem.receiveEnergy(inventory[1], energy, false), false);
		processRem -= received;

		if (processRem <= 0) {
			transferProducts();

			if (!redstoneControlOrDisable()) {
				isActive = false;
				wasActive = true;
				tracker.markTime(worldObj);
			}
		}
	}

	@Override
	public boolean isItemValid(ItemStack stack, int slot, int side) {

		return slot == 0 ? EnergyHelper.isEnergyContainerItem(stack) || ChargerManager.recipeExists(stack) : true;
	}

	/* GUI METHODS */
	@Override
	public Object getGuiClient(InventoryPlayer inventory) {

		return new GuiCharger(inventory, this);
	}

	@Override
	public Object getGuiServer(InventoryPlayer inventory) {

		return new ContainerCharger(inventory, this);
	}

	/* NBT METHODS */
	@Override
	public void readFromNBT(NBTTagCompound nbt) {

		super.readFromNBT(nbt);

		outputTracker = nbt.getInteger("Tracker");

		// TODO:
		/** PATCH LOGIC for B9 Slot Addition - to be removed in RELEASE */
		String version = nbt.getString("Version");

		if (new ModVersion("", version).compareTo(new ModVersion("", "1.7.10R4.0.0B9")) < 0) {
			inventory[2] = ItemHelper.cloneStack(inventory[1]);
			inventory[1] = null;

			if (inventory[0] != null) {
				inventory[1] = ItemHelper.cloneStack(inventory[0], 1);
				inventory[0].stackSize--;

				if (inventory[0].stackSize <= 0) {
					inventory[0] = null;
				}
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {

		super.writeToNBT(nbt);

		nbt.setInteger("Tracker", outputTracker);
	}

	/* IInventory */
	@Override
	public ItemStack decrStackSize(int slot, int amount) {

		ItemStack stack = super.decrStackSize(slot, amount);

		if (ServerHelper.isServerWorld(worldObj) && slot == 1) {
			if (isActive && (inventory[slot] == null || !hasValidInput())) {
				isActive = false;
				wasActive = true;
				tracker.markTime(worldObj);
				processRem = 0;
				containerItem = null;
			}
		}
		return stack;
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {

		if (ServerHelper.isServerWorld(worldObj) && slot == 1) {
			if (isActive && inventory[slot] != null) {
				if (stack == null || !stack.isItemEqual(inventory[slot]) || !hasValidInput()) {
					isActive = false;
					wasActive = true;
					tracker.markTime(worldObj);
					processRem = 0;
				}
			}
			containerItem = null;
		}
		inventory[slot] = stack;

		if (stack != null && stack.stackSize > getInventoryStackLimit()) {
			stack.stackSize = getInventoryStackLimit();
		}
	}

	@Override
	public void markDirty() {

		if (isActive && !hasValidInput()) {
			containerItem = null;
		}
		super.markDirty();
	}

	/* IEnergyInfo */
	@Override
	public int getInfoEnergyPerTick() {

		return calcEnergy();
	}

	@Override
	public int getInfoMaxEnergyPerTick() {

		return energyConfig.maxPower;
	}

}
