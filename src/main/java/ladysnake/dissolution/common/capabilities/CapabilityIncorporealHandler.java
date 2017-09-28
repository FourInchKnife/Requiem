package ladysnake.dissolution.common.capabilities;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import ladysnake.dissolution.api.IIncorporealHandler;
import ladysnake.dissolution.common.DissolutionConfig;
import ladysnake.dissolution.common.DissolutionConfigManager;
import ladysnake.dissolution.common.DissolutionConfigManager.FlightModes;
import ladysnake.dissolution.common.entity.EntityPlayerCorpse;
import ladysnake.dissolution.common.inventory.DissolutionInventoryHelper;
import ladysnake.dissolution.common.Reference;
import ladysnake.dissolution.common.networking.IncorporealMessage;
import ladysnake.dissolution.common.networking.PacketHandler;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.GameType;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToAccessFieldException;
import net.minecraftforge.fml.relauncher.ReflectionHelper.UnableToFindFieldException;

/**
 * This set of classes handles the Incorporeal capability.
 * It is used to store and read all the additional information (related to the ghost state) on players. <br>
 * The IncorporealDataHandler class itself is used to register the capability and query the right handler
 * @author Pyrofab
 *
 */
@Mod.EventBusSubscriber(modid=Reference.MOD_ID)
public class CapabilityIncorporealHandler {

	/**this is a list of hardcoded vanilla blocks that players can interact with*/
	public static ArrayList<Block> soulInteractableBlocks = new ArrayList<Block>();

	static {
		soulInteractableBlocks.add(Blocks.LEVER);
		soulInteractableBlocks.add(Blocks.GLASS_PANE);
	}

	@CapabilityInject(IIncorporealHandler.class)
    public static final Capability<IIncorporealHandler> CAPABILITY_INCORPOREAL = null;

    public static void register() {
        CapabilityManager.INSTANCE.register(IIncorporealHandler.class, new Storage(), DefaultIncorporealHandler.class);
        MinecraftForge.EVENT_BUS.register(new CapabilityIncorporealHandler());
    }

    /**
     * This is a utility method to get the handler attached to an entity
     * @param entity an entity that has the capability attached (in this case, a player)
     * @return the IncorporealHandler attached or null if there is none
     */
    public static IIncorporealHandler getHandler(Entity entity) {

        if (entity.hasCapability(CAPABILITY_INCORPOREAL, EnumFacing.DOWN))
            return entity.getCapability(CAPABILITY_INCORPOREAL, EnumFacing.DOWN);

        return null;
    }
    
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
    	getHandler(event.player).tick();
    }

    /**
     * This is the class that does most of the work, and the one other classes interact with
     * @author Pyrofab
     *
     */
	public static class DefaultIncorporealHandler implements IIncorporealHandler {

		private boolean incorporeal = false;
		/**true if the player is a ghost because of death*/
		private boolean dead = false;
		/**How much time this entity will be intangible*/
		private int intangibleTimer = -1;
		private int lastFood = -1;
		private String lastDeathMessage;
		private boolean synced = false;
		private int prevGamemode = 0;
		/**Not used currently, allows the player to wear a different skin*/
		private Optional<UUID> disguise = Optional.empty();

		private EntityPlayer owner;

		/**Only there in case of reflection by forge*/
		public DefaultIncorporealHandler () {}

		public DefaultIncorporealHandler (EntityPlayer owner) {
			this.owner = owner;
		}

		@Override
		public void setIncorporeal(boolean enable) {
			incorporeal = enable;
			dead = enable;
			owner.setEntityInvulnerable(enable);
			if(DissolutionConfigManager.isFlightEnabled(FlightModes.CUSTOM_FLIGHT) && owner.world.isRemote)
				owner.capabilities.setFlySpeed(enable ? 0.025f : 0.05f);

			try {
				ObfuscationReflectionHelper.setPrivateValue(Entity.class, owner, enable, "isImmuneToFire", "field_70178_ae");
			} catch (UnableToFindFieldException | UnableToAccessFieldException e) {
				e.printStackTrace();
			}
			
			owner.setInvisible(enable && DissolutionConfig.ghost.invisibleGhosts);

			if(!owner.isCreative()) {
				boolean enableFlight = (!DissolutionConfigManager.isFlightEnabled(FlightModes.NO_FLIGHT)) && (!DissolutionConfigManager.isFlightEnabled(FlightModes.CUSTOM_FLIGHT));
				owner.capabilities.disableDamage = enable;
				owner.capabilities.allowFlying = (enable && (owner.experienceLevel > 0) && enableFlight);
				owner.capabilities.isFlying = (enable && owner.capabilities.isFlying && owner.experienceLevel > 0 && enableFlight);
			}
			if(!owner.world.isRemote) {
				PacketHandler.net.sendToAll(new IncorporealMessage(owner.getUniqueID().getMostSignificantBits(),
					owner.getUniqueID().getLeastSignificantBits(), enable));
			} else if(!enable){
				GuiIngameForge.renderHotbar = true;
				GuiIngameForge.renderHealth = true;
				GuiIngameForge.renderFood = true;
				GuiIngameForge.renderArmor = true;
				GuiIngameForge.renderAir = true;
			}
			setSynced(true);
		}
		
		public void split() {
			EntityPlayerCorpse body = new EntityPlayerCorpse(owner.world);
			body.setPlayer(owner.getUniqueID());
			body.setInert(true);
			body.setDecaying(false);
			DissolutionInventoryHelper.transferEquipment(owner, body);
			owner.world.spawnEntity(body);
			setIncorporeal(true);
			dead = false;
		}

		@Override
		public boolean isIncorporeal() {
			return this.incorporeal;
		}

		@Override
		public void setSynced(boolean synced) {
			this.synced = synced;
		}

		@Override
		public boolean isSynced() {
			return this.synced;
		}

		@Override
		public String getLastDeathMessage() {
			return this.lastDeathMessage;
		}

		@Override
		public void setLastDeathMessage(String deathMessage) {
			this.lastDeathMessage = deathMessage;
		}

		@Override
		public void tick() {
			if(isIncorporeal())
				if(this.lastFood < 0)
					lastFood = owner.getFoodStats().getFoodLevel();
				else
					owner.getFoodStats().setFoodLevel(lastFood);
			else
				lastFood = -1;
			if(intangibleTimer > -1000) {
				final boolean prevIntangible = isIntangible();
				intangibleTimer--;
				if(prevIntangible && !isIntangible()) {
					setIntangible(false);
				}
			}
		}

		@Override
		public boolean setIntangible(boolean intangible) {
			if(intangible && this.isIncorporeal() && intangibleTimer <= -1000) {
				this.intangibleTimer = 100;
				if(owner != null && !owner.world.isRemote) {
					this.prevGamemode = ((EntityPlayerMP)owner).interactionManager.getGameType().getID();
					owner.setGameType(GameType.SPECTATOR);
				}
			} else if (!intangible) {
				this.intangibleTimer = -1;
				if(owner != null && !owner.world.isRemote)
					owner.setGameType(GameType.getByID(this.prevGamemode));
			}
			return true;
		}

		@Override
		public boolean isIntangible() {
			return this.intangibleTimer >= 0;
		}

		@Override
		public void setDisguise(UUID usurpedId) {
			disguise = Optional.ofNullable(usurpedId);
		}

		@Override
		public Optional<UUID> getDisguise() {
			return disguise;
		}
	}

	 public static class Provider implements ICapabilitySerializable<NBTTagCompound> {

	        IIncorporealHandler instance;

	        public Provider(EntityPlayer owner) {
	        	this.instance = new DefaultIncorporealHandler(owner);
			}

	        @Override
	        public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
	            return capability == CAPABILITY_INCORPOREAL;
	        }

	        @Override
	        public <T> T getCapability(Capability<T> capability, EnumFacing facing) {

	            return hasCapability(capability, facing) ? CAPABILITY_INCORPOREAL.<T>cast(instance) : null;
	        }

	        @Override
	        public NBTTagCompound serializeNBT() {
	            return (NBTTagCompound) CAPABILITY_INCORPOREAL.getStorage().writeNBT(CAPABILITY_INCORPOREAL, instance, null);
	        }

	        @Override
	        public void deserializeNBT(NBTTagCompound nbt) {
	            CAPABILITY_INCORPOREAL.getStorage().readNBT(CAPABILITY_INCORPOREAL, instance, EnumFacing.DOWN, nbt);
	        }
	    }

	 /**
	  * This is what stores to and reads from the disk
	  * @author Pyrofab
	  *
	  */
	 public static class Storage implements Capability.IStorage<IIncorporealHandler> {

		    @Override
		    public NBTBase writeNBT (Capability<IIncorporealHandler> capability, IIncorporealHandler instance, EnumFacing side) {
		        final NBTTagCompound tag = new NBTTagCompound();
		        tag.setBoolean("incorporeal", instance.isIncorporeal());
		        if(instance instanceof DefaultIncorporealHandler) {
		        	tag.setBoolean("dead", ((DefaultIncorporealHandler)instance).dead);
		        	tag.setInteger("intangible", ((DefaultIncorporealHandler)instance).intangibleTimer);
		        	tag.setInteger("prevGamemode", ((DefaultIncorporealHandler)instance).prevGamemode);
		        } else {
		        	tag.setBoolean("intangible", instance.isIntangible());
		        }
		        tag.setString("lastDeath", instance.getLastDeathMessage() == null || instance.getLastDeathMessage().isEmpty() ? "This player has no recorded death" : instance.getLastDeathMessage());
		        return tag;
		    }

		    @Override
		    public void readNBT (Capability<IIncorporealHandler> capability, IIncorporealHandler instance, EnumFacing side, NBTBase nbt) {
		        final NBTTagCompound tag = (NBTTagCompound) nbt;
		        instance.setIncorporeal(tag.getBoolean("incorporeal"));
		        if(instance instanceof DefaultIncorporealHandler) {
		        	((DefaultIncorporealHandler)instance).dead = tag.getBoolean("dead");
		        	((DefaultIncorporealHandler)instance).intangibleTimer = tag.getInteger("intangible");
		        	((DefaultIncorporealHandler)instance).prevGamemode = tag.getInteger("prevGamemode");
		        } else {
		        	instance.setIntangible(tag.getBoolean("intangible"));
		        }
		        instance.setLastDeathMessage(tag.getString("lastDeath"));
		    }
		}

}
