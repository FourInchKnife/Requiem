package ladysnake.dissolution.common.handlers;

import ladylib.misc.ReflectionFailedException;
import ladylib.reflection.LLMethodHandle;
import ladylib.reflection.LLReflectionHelper;
import ladysnake.dissolution.api.corporeality.ICorporealityStatus;
import ladysnake.dissolution.api.corporeality.IIncorporealHandler;
import ladysnake.dissolution.api.corporeality.IPossessable;
import ladysnake.dissolution.common.Dissolution;
import ladysnake.dissolution.common.capabilities.CapabilityIncorporealHandler;
import ladysnake.dissolution.common.config.DissolutionConfigManager;
import ladysnake.dissolution.common.registries.SoulStates;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;


/**
 * This class handles basic events-related logic
 *
 * @author Pyrofab
 */
public class EventHandlerCommon {

    private static final LLMethodHandle.LLMethodHandle1<AbstractSkeleton, Float, EntityArrow> abstractSkeleton$getArrow = LLReflectionHelper.findMethod(AbstractSkeleton.class, "func_190726_a", EntityArrow.class, float.class);

    public EventHandlerCommon() {

    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        event.player.inventoryContainer.addListener(new PlayerInventoryListener((EntityPlayerMP) event.player));
    }

    @SubscribeEvent
    public void clonePlayer(PlayerEvent.Clone event) {
        if (event.isWasDeath() && !event.getEntityPlayer().isCreative()) {
            final IIncorporealHandler corpse = CapabilityIncorporealHandler.getHandler(event.getOriginal());
            final IIncorporealHandler clone = CapabilityIncorporealHandler.getHandler(event.getEntityPlayer());
            clone.setStrongSoul(corpse.isStrongSoul());
            clone.setCorporealityStatus(corpse.getCorporealityStatus());
            clone.getDialogueStats().deserializeNBT(corpse.getDialogueStats().serializeNBT());
            clone.setSynced(false);

            if (clone.isStrongSoul()) {
                event.getEntityPlayer().experienceLevel = event.getOriginal().experienceLevel;
                clone.getDeathStats().setDeathDimension(corpse.getDeathStats().getDeathDimension());
                clone.getDeathStats().setDeathLocation(new BlockPos(event.getOriginal().posX, event.getOriginal().posY, event.getOriginal().posZ));
            }
            // avoid accumulation of tracked players and allow garbage collection
            corpse.getCorporealityStatus().resetState(event.getOriginal());
        }
    }

    /**
     * Makes the player practically invisible to mobs
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onVisibilityPlayer(PlayerEvent.Visibility event) {
        final ICorporealityStatus playerCorp = CapabilityIncorporealHandler.getHandler(event.getEntityPlayer()).getCorporealityStatus();
        if (playerCorp.isIncorporeal()) {
            event.modifyVisibility(0D);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() instanceof EntityArrow && !event.getWorld().isRemote) {
            EntityArrow arrow = (EntityArrow) event.getEntity();
            CapabilityIncorporealHandler.getHandler(arrow.shootingEntity).ifPresent(handler -> {
                EntityLivingBase possessed = handler.getPossessed();
                if (possessed instanceof AbstractSkeleton) {
                    try {
                        EntityArrow mobArrow = abstractSkeleton$getArrow.invoke((AbstractSkeleton) possessed, 0f);
                        mobArrow.setDamage(arrow.getDamage());
                        mobArrow.copyLocationAndAnglesFrom(arrow);
                        mobArrow.motionX = arrow.motionX;
                        mobArrow.motionY = arrow.motionY;
                        mobArrow.motionZ = arrow.motionZ;
                        arrow.world.spawnEntity(mobArrow);
                        event.setCanceled(true);
                    } catch (ReflectionFailedException e) {
                        Dissolution.LOGGER.warn("Failed to get an arrow from a skeleton", e);
                    }
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof EntityPlayer && !event.getSource().canHarmInCreative()) {
            ICorporealityStatus status = CapabilityIncorporealHandler.getHandler((EntityPlayer) event.getEntity()).getCorporealityStatus();
            if (status.allowsInvulnerability()) {
                if (event.getSource().getTrueSource() == null || DissolutionConfigManager.isEctoplasmImmuneTo(event.getSource().getTrueSource())) {
                    event.setCanceled(!event.getSource().canHarmInCreative());
                }
            }
        } else {
            CapabilityIncorporealHandler.getHandler(event.getSource().getTrueSource()).ifPresent(handler -> {
                IPossessable possessed = handler.getPossessed();
                if (possessed != null) {
                    if (possessed.proxyAttack(event.getEntityLiving(), event.getSource(), event.getAmount())) {
                        event.setCanceled(true);
                    }
                }
            });
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntity(AttackEntityEvent event) {
        final IIncorporealHandler playerCorp = CapabilityIncorporealHandler.getHandler(event.getEntityPlayer());
        if (playerCorp.getCorporealityStatus().isIncorporeal() && !event.getEntityPlayer().isCreative()) {
            final EntityLivingBase possessed = playerCorp.getPossessed();
            if (possessed != null && !possessed.isDead) {
                if (event.getTarget() instanceof EntityLivingBase) {
                    event.getEntityPlayer().getHeldItemMainhand().hitEntity((EntityLivingBase) event.getTarget(), event.getEntityPlayer());
                }
                possessed.attackEntityAsMob(event.getTarget());
                return;
            }
        }
        if (event.getTarget() instanceof EntityPlayer) {
            final IIncorporealHandler targetCorp = CapabilityIncorporealHandler.getHandler((EntityPlayer) event.getTarget());
            if (targetCorp.getCorporealityStatus().isIncorporeal() && !event.getEntityPlayer().isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLivingSetAttackTarget(LivingSetAttackTargetEvent event) {
        CapabilityIncorporealHandler.getHandler(event.getTarget()).ifPresent(handler -> {
            if (event.getEntity() instanceof EntityLiving && handler.getCorporealityStatus().isIncorporeal() && DissolutionConfigManager.isEctoplasmImmuneTo(event.getEntity())) {
                ((EntityLiving) event.getEntity()).setAttackTarget(null);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityItemPickup(EntityItemPickupEvent event) {
        final IIncorporealHandler playerCorp = CapabilityIncorporealHandler.getHandler(event.getEntityPlayer());
        if (playerCorp.getCorporealityStatus().isIncorporeal() && playerCorp.getPossessed() == null && !event.getEntityPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    /**
     * Makes the players tangible again when stroke by lightning. Just because we can.
     */
    @SubscribeEvent
    public void onEntityStruckByLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof EntityPlayer) {
            final IIncorporealHandler playerCorp = CapabilityIncorporealHandler.getHandler((EntityPlayer) event.getEntity());
            if (playerCorp.getCorporealityStatus().isIncorporeal()) {
                playerCorp.setCorporealityStatus(SoulStates.BODY);
            }
        }
    }
}
