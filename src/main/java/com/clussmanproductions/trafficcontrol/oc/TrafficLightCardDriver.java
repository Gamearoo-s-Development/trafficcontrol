package com.clussmanproductions.trafficcontrol.oc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.clussmanproductions.trafficcontrol.Config;
import com.clussmanproductions.trafficcontrol.ModItems;
import com.clussmanproductions.trafficcontrol.blocks.BlockPedestrianButton;
import com.clussmanproductions.trafficcontrol.blocks.BlockTrafficSensorLeft;
import com.clussmanproductions.trafficcontrol.blocks.BlockTrafficSensorRight;
import com.clussmanproductions.trafficcontrol.blocks.BlockTrafficSensorStraight;
import com.clussmanproductions.trafficcontrol.item.ItemTrafficLightCard;
import com.clussmanproductions.trafficcontrol.tileentity.BaseTrafficLightTileEntity;
import com.clussmanproductions.trafficcontrol.tileentity.PedestrianButtonTileEntity;
import com.clussmanproductions.trafficcontrol.tileentity.TrafficLightControlBoxTileEntity;
import com.clussmanproductions.trafficcontrol.util.CustomAngleCalculator;
import com.clussmanproductions.trafficcontrol.util.EnumTrafficLightBulbTypes;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.item.Slot;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.EnvironmentHost;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.DriverItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

public class TrafficLightCardDriver extends DriverItem {

	private static boolean entityMatchesSensorClasses(net.minecraft.entity.Entity e) {
		return Arrays.stream(Config.sensorClasses).anyMatch(eName -> {
			Class<?> nextClass = e.getClass();
			while (nextClass != null) {
				if (eName.equals(nextClass.getName())) {
					return true;
				}
				nextClass = nextClass.getSuperclass();
			}
			return false;
		});
	}

	public TrafficLightCardDriver()
	{
		super(new ItemStack(ModItems.traffic_light_card, 1, 0),
			  new ItemStack(ModItems.traffic_light_card, 1, 1),
			  new ItemStack(ModItems.traffic_light_card, 1, 2),
			  new ItemStack(ModItems.traffic_light_card, 1, 3));
	}
	
	@Override
	public ManagedEnvironment createEnvironment(ItemStack stack, EnvironmentHost host) {
		return new CardEnvironment(stack, host);
	}

	@Override
	public String slot(ItemStack stack) {
		return Slot.Card;
	}
	
	@Override
	public int tier(ItemStack stack) {
		switch(stack.getMetadata())
		{
			case 0:
				return 0;
			case 1:
				return 1;
			default:
				return 2;
		}
	}

	public class CardEnvironment extends AbstractManagedEnvironment
	{
		private final ItemStack card;
		private final EnvironmentHost host;
		public CardEnvironment(ItemStack card, EnvironmentHost host)
		{
			this.card = card;
			this.host = host;
			setNode(Network.newNode(this, Visibility.Neighbors).withComponent("traffic_light_card").withConnector(300).create());
		}

		private static final int MAX_SENSORS_AND_PED_BUTTONS = 8;

		@Callback(doc = "pairSensor(x:int, y:int, z:int):boolean OR pairSensor(id:long):boolean -- Pairs or unpairs a sensor to this card")
		public Object[] pairSensor(Context c, Arguments args) throws Exception {
			BlockPos pos = getBlockPosFromArgs(args);
			NBTTagCompound tag = card.getTagCompound();
			if (tag == null) {
				tag = new NBTTagCompound();
				card.setTagCompound(tag);
			}

			long id = pos.toLong();
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("sensor" + i) && tag.getLong("sensor" + i) == id) {
					tag.removeTag("sensor" + i);
					return new Object[] { false, "Unpaired" };
				}
			}

			int count = 0;
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("sensor" + i)) {
					count++;
				}
			}
			if (count >= MAX_SENSORS_AND_PED_BUTTONS) {
				return new Object[] { false, "Max sensors reached" };
			}

			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (!tag.hasKey("sensor" + i)) {
					tag.setLong("sensor" + i, id);
					return new Object[] { true, "Paired" };
				}
			}

			return new Object[] { false, "Unexpected" };
		}

		@Callback(doc = "pairPedButton(x:int, y:int, z:int):boolean, string OR pairPedButton(id:long):boolean, string -- Pairs or unpairs a pedestrian button to this card")
		public Object[] pairPedButton(Context c, Arguments args) throws Exception {
			BlockPos pos = getBlockPosFromArgs(args);
			NBTTagCompound tag = card.getTagCompound();
			if (tag == null) {
				tag = new NBTTagCompound();
				card.setTagCompound(tag);
			}

			long id = pos.toLong();
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("pedButton" + i) && tag.getLong("pedButton" + i) == id) {
					tag.removeTag("pedButton" + i);
					return new Object[] { false, "Unpaired" };
				}
			}

			int count = 0;
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("pedButton" + i)) {
					count++;
				}
			}
			if (count >= MAX_SENSORS_AND_PED_BUTTONS) {
				return new Object[] { false, "Max ped buttons reached" };
			}

			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (!tag.hasKey("pedButton" + i)) {
					tag.setLong("pedButton" + i, id);
					return new Object[] { true, "Paired" };
				}
			}

			return new Object[] { false, "Unexpected" };
		}

		@Callback(doc = "listPedButtons():array -- Returns a list of pedestrian button positions paired to this card")
		public Object[] listPedButtons(Context c, Arguments args) {
			NBTTagCompound tag = card.getTagCompound();
			if (tag == null) {
				return new Object[] { new ArrayList<Integer[]>() };
			}

			ArrayList<Integer[]> buttons = new ArrayList<>();
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("pedButton" + i)) {
					BlockPos p = BlockPos.fromLong(tag.getLong("pedButton" + i));
					buttons.add(new Integer[] { p.getX(), p.getY(), p.getZ() });
				}
			}

			return new Object[] { buttons };
		}

		@Callback(doc = "pressPedButton(x:int, y:int, z:int):boolean, string OR pressPedButton(id:long):boolean, string -- Simulates pressing a pedestrian button")
		public Object[] pressPedButton(Context c, Arguments args) throws Exception {
			BlockPos pos = getBlockPosFromArgs(args);

			TileEntity te = host.world().getTileEntity(pos);
			if (!(te instanceof PedestrianButtonTileEntity)
					|| !(host.world().getBlockState(pos).getBlock() instanceof BlockPedestrianButton)) {
				return new Object[] { false, "No pedestrian button at given position" };
			}

			int rotation = host.world().getBlockState(pos).getValue(BlockPedestrianButton.ROTATION);
			PedestrianButtonTileEntity pedTE = (PedestrianButtonTileEntity) te;
			int queued = 0;

			for (BlockPos controller : new ArrayList<>(pedTE.getPairedBoxes())) {
				TileEntity ctrlTE = host.world().getTileEntity(controller);
				if (!(ctrlTE instanceof TrafficLightControlBoxTileEntity)) {
					pedTE.removePairedBox(controller);
					continue;
				}

				TrafficLightControlBoxTileEntity ctrlr = (TrafficLightControlBoxTileEntity) ctrlTE;
				if (CustomAngleCalculator.isNorthSouth(rotation)) {
					ctrlr.getAutomator().setWestEastPedQueued(true);
				} else {
					ctrlr.getAutomator().setNorthSouthPedQueued(true);
				}

				ctrlr.markDirty();
				host.world().notifyBlockUpdate(controller, host.world().getBlockState(controller),
						host.world().getBlockState(controller), 3);
				queued++;
			}

			return new Object[] { true, "Queued for " + queued + " controller(s)" };
		}

		@Callback(doc = "listSensors():array -- Returns a list of sensor positions")
		public Object[] listSensors(Context c, Arguments args) {
			NBTTagCompound tag = card.getTagCompound();
			if (tag == null) {
				return new Object[] { new ArrayList<Integer[]>() };
			}

			ArrayList<Integer[]> sensors = new ArrayList<>();
			for (int i = 0; i < MAX_SENSORS_AND_PED_BUTTONS; i++) {
				if (tag.hasKey("sensor" + i)) {
					BlockPos p = BlockPos.fromLong(tag.getLong("sensor" + i));
					sensors.add(new Integer[] { p.getX(), p.getY(), p.getZ() });
				}
			}

			return new Object[] { sensors };
		}

		@Callback(doc = "isSensorTripped(x:int, y:int, z:int):boolean OR isSensorTripped(id:long):boolean -- Optional width, height, length after coordinates (defaults -1, sensorScanHeight, 1)")
		public Object[] isSensorTripped(Context c, Arguments args) throws Exception {
			BlockPos pos = getBlockPosFromArgs2(args);

			IBlockState state = host.world().getBlockState(pos);
			if (!(state.getBlock() instanceof BlockTrafficSensorLeft
					|| state.getBlock() instanceof BlockTrafficSensorRight
					|| state.getBlock() instanceof BlockTrafficSensorStraight)) {
				return new Object[] { false };
			}

			int width = args.count() >= 4 ? args.checkInteger(3) : -1;
			int height = args.count() >= 5 ? args.checkInteger(4) : Config.sensorScanHeight;
			int length = args.count() >= 6 ? args.checkInteger(5) : 1;

			boolean tripped = host.world()
					.getEntitiesWithinAABBExcludingEntity(null,
							new AxisAlignedBB(pos).grow(width / 2.0, height / 2.0, length / 2.0))
					.stream()
					.anyMatch(e -> (e instanceof EntityPlayerMP) || TrafficLightCardDriver.entityMatchesSensorClasses(e));

			return new Object[] { tripped };
		}

		@Callback(direct = true, doc = "listBlockPos():array -- Retrieves a list of block positions currently in use by the card")
		public Object[] listBlockPos(Context c, Arguments args)
		{
			NBTTagCompound cardTag = card.getTagCompound();
			if (cardTag == null)
			{
				return new Object[] { new ArrayList<Integer[]>() };
			}
			
			int maxTrafficLights = ItemTrafficLightCard.getMaxTrafficLights(card.getMetadata());
			ArrayList<Integer[]> blockPositions = new ArrayList<>();
			
			for(int i = 0; i < maxTrafficLights; i++)
			{
				if (cardTag.hasKey("light" + i) && cardTag.getLong("light" + i) != 0)
				{
					BlockPos position = BlockPos.fromLong(cardTag.getLong("light" + i));
					blockPositions.add(new Integer[] { position.getX(), position.getY(), position.getZ() });
				}
			}
			
			return new Object[] { blockPositions };
		}
		
		@Callback(direct = true, doc = "listBlockIDs():array -- Retrieves a list of block ids currently in use by the card")
		public Object[] listBlockIDs(Context c, Arguments args)
		{
			NBTTagCompound cardTag = card.getTagCompound();
			if (cardTag == null)
			{
				return new Object[] { new ArrayList<Long>() };
			}
			
			int maxTrafficLights = ItemTrafficLightCard.getMaxTrafficLights(card.getMetadata());
			ArrayList<Long> blockPositions = new ArrayList<>();
			
			for(int i = 0; i < maxTrafficLights; i++)
			{
				if (cardTag.hasKey("light" + i) && cardTag.getLong("light" + i) != 0)
				{
					blockPositions.add(cardTag.getLong("light" + i));
				}
			}
			
			return new Object[] { blockPositions };
		}
		
		private final Map<String, EnumTrafficLightBulbTypes> bulbTypesByString = Arrays.stream(EnumTrafficLightBulbTypes.values()).collect(Collectors.toMap(EnumTrafficLightBulbTypes::toString, Function.identity()));
		private final Map<String, String> bulbTypeStringsByString = Arrays.stream(EnumTrafficLightBulbTypes.values()).collect(Collectors.toMap(EnumTrafficLightBulbTypes::toString, EnumTrafficLightBulbTypes::toString));
		@Callback(direct = true, getter = true, doc = "states():array -- Retrieves a list of all possible states")
		public Object[] states(Context c, Arguments args)
		{
			return new Object[] { bulbTypeStringsByString };
		}
		
		@Callback(doc = "setState(x:int, y:int, z:int, state:string, active:boolean, flash:boolean):boolean, string OR setState(id:long, state:string, active:boolean, flash:boolean) -- Sets the state of the specified traffic light")
		public Object[] setState(Context c, Arguments args) throws Exception
		{
			BlockPos pos = getBlockPosFromArgs(args);
			if (!(args.isString(3) && args.isBoolean(4) && args.isBoolean(5)) &&
				!(args.isString(1) && args.isBoolean(2) && args.isBoolean(3)))
			{
				throw new IllegalArgumentException("Invalid argument format");
			}
			
			if (!cardContainsPos(pos))
			{
				return new Object[] { false, "Card does not contain this block position" };
			}
			
			String state;
			boolean active;
			boolean flash;
			if (args.isInteger(1))
			{
				state = args.checkString(3);
				active = args.checkBoolean(4);
				flash = args.checkBoolean(5);
			}
			else
			{
				state = args.checkString(1);
				active = args.checkBoolean(2);
				flash = args.checkBoolean(3);
			}
			
			if (!bulbTypesByString.containsKey(state))
			{
				return new Object[] { false, "Invalid state specified" };
			}
			
			TileEntity posTE = host.world().getTileEntity(pos);
			if (!(posTE instanceof BaseTrafficLightTileEntity))
			{
				return new Object[] { false, "A traffic light no longer exists at this block position" };
			}
			
			BaseTrafficLightTileEntity trafficLight = (BaseTrafficLightTileEntity)posTE;
			if (!trafficLight.hasBulb(bulbTypesByString.get(state)))
			{
				return new Object[] { false, "Traffic light does not contain the specified bulb" };
			}
			
			BlockPos hostPos = new BlockPos(host.xPosition(), host.yPosition(), host.zPosition());
			
			double draw = (Config.trafficLightCardDrawPerBlock * hostPos.distanceSq(pos));
			if (!((ComponentConnector)node()).tryChangeBuffer(-draw))
			{
				return new Object[] { false, "Not enough energy" };
			}
			
			trafficLight.setActive(bulbTypesByString.get(state), active, flash);
			return new Object[] { true };
		}
		
		@Callback(doc = "clearStates(x:int, y:int, z:int):boolean, string OR clearStates(id:long):boolean, string -- Sets all states to inactive for the specified traffic light")
		public Object[] clearStates(Context c, Arguments args) throws Exception
		{
			BlockPos pos = getBlockPosFromArgs(args);
			
			if (!cardContainsPos(pos))
			{
				return new Object[] { false, "Card does not contain this block position" };
			}
			
			TileEntity posTE = host.world().getTileEntity(pos);
			if (!(posTE instanceof BaseTrafficLightTileEntity))
			{
				return new Object[] { false, "A traffic light no longer exists at this block position" };
			}
			
			BlockPos hostPos = new BlockPos(host.xPosition(), host.yPosition(), host.zPosition());
			double draw = (Config.trafficLightCardDrawPerBlock * hostPos.distanceSq(pos));
			if (!((ComponentConnector)node()).tryChangeBuffer(-draw))
			{
				return new Object[] { false, "Not enough energy" };
			}
			
			BaseTrafficLightTileEntity trafficLight = (BaseTrafficLightTileEntity)posTE;
			trafficLight.powerOff();
			trafficLight.setActive(EnumTrafficLightBulbTypes.DontCross, false, false); // Power off automatically turns on Dont Cross, which is wrong in this context
			return new Object[] { true };
		}
		
		@Callback(doc = "getStates(x:int, y:int, z:int):boolean, string/table OR getStates(id:long):boolean, string/table -- Returns a table where the key is the state and value is a table that has an active and flash key")
		public Object[] getStates(Context c, Arguments args) throws Exception
		{
			BlockPos pos = getBlockPosFromArgs(args);
			
			if (!cardContainsPos(pos))
			{
				return new Object[] { false, "Card does not contain this block position" };
			}
			
			TileEntity posTE = host.world().getTileEntity(pos);
			if (!(posTE instanceof BaseTrafficLightTileEntity))
			{
				return new Object[] { false, "A traffic light no longer exists at this block position" };
			}
			
			BlockPos hostPos = new BlockPos(host.xPosition(), host.yPosition(), host.zPosition());
			double draw = (Config.trafficLightCardDrawPerBlock * hostPos.distanceSq(pos));
			if (!((ComponentConnector)node()).tryChangeBuffer(-draw))
			{
				return new Object[] { false, "Not enough energy" };
			}
			
			BaseTrafficLightTileEntity trafficLight = (BaseTrafficLightTileEntity)posTE;
			HashMap<String, StateInfo> stateInfos = new HashMap<>();
			for(String bulbTypeString : bulbTypeStringsByString.keySet())
			{
				stateInfos.put(bulbTypeString, new StateInfo());
			}
			
			HashSet<String> discoveredStates = new HashSet<>();
			
			for(int i = 0; i < trafficLight.getBulbCount(); i++)
			{
				EnumTrafficLightBulbTypes bulbType = trafficLight.getBulbTypeBySlot(i); 
				if (bulbType == null)
				{
					continue;
				}
				
				if (discoveredStates.add(bulbType.toString()))
				{
					stateInfos.get(bulbType.toString()).active = trafficLight.getActiveBySlot(i);
					stateInfos.get(bulbType.toString()).flash = trafficLight.getFlashBySlot(i);
				}
			}
			
			return new Object[] { true, stateInfos };
		}
		
		private class StateInfo implements Value
		{
			public boolean active;
			public boolean flash;
			
			@Override
			public void load(NBTTagCompound nbt) {
				active = nbt.getBoolean("active");
				flash = nbt.getBoolean("flash");
			}
			@Override
			public void save(NBTTagCompound nbt) {
				nbt.setBoolean("active", active);
				nbt.setBoolean("flash", flash);
			}
			@Override
			public Object apply(Context context, Arguments arguments) {
				switch(arguments.checkString(0))
				{
					case "active":
						return active;
					case "flash":
						return flash;
				}
				
				return null;
			}
			@Override
			public void unapply(Context context, Arguments arguments) { }
			@Override
			public Object[] call(Context context, Arguments arguments) {
				// TODO Auto-generated method stub
				return null;
			}
			@Override
			public void dispose(Context context) {
				// TODO Auto-generated method stub
				
			}
		}
		
		private BlockPos getBlockPosFromArgs2(Arguments args) throws Exception {
			if (args.count() >= 3 && args.isInteger(0) && args.isInteger(1) && args.isInteger(2)) {
				return new BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2));
			}
			if (args.count() >= 1 && args.isDouble(0)) {
				double posIDDouble = args.checkDouble(0);
				return BlockPos.fromLong((long) posIDDouble);
			}
			throw new IllegalArgumentException("Could not determine block position");
		}

		private BlockPos getBlockPosFromArgs(Arguments args) throws Exception {
			if (args.count() >= 3 && args.isInteger(0) && args.isInteger(1) && args.isInteger(2)) {
				return new BlockPos(args.checkInteger(0), args.checkInteger(1), args.checkInteger(2));
			}
			if (args.count() >= 1 && args.isDouble(0)) {
				double posIDDouble = args.checkDouble(0);
				return BlockPos.fromLong((long) posIDDouble);
			}
			throw new IllegalArgumentException("Could not determine block position");
		}

		private boolean cardContainsPos(BlockPos pos) {
			NBTTagCompound tag = card.getTagCompound();
			if (tag == null) {
				return false;
			}
			long id = pos.toLong();
			for (String lightKey : tag.getKeySet().stream().filter(key -> key.startsWith("light")).collect(Collectors.toList())) {
				long tagKey = tag.getLong(lightKey);
				if (tagKey == id) {
					return true;
				}
			}

			return false;
		}
	}
}
