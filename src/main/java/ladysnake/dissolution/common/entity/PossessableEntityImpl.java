package ladysnake.dissolution.common.entity;

import ladysnake.dissolution.api.possession.Possessable;
import ladysnake.dissolution.api.possession.Possessor;
import ladysnake.dissolution.common.entity.ai.InertGoal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableTextComponent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class PossessableEntityImpl extends PossessableEntityBase implements Possessable {
    private @Nullable UUID possessorUuid;

    public PossessableEntityImpl(World world) {
        super(world);
    }

    public PossessableEntityImpl(EntityType<?> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
//        if (this.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
//            AttributeHelper.substituteAttributeInstance(this.getAttributeContainer(), new CooldownStrengthAttribute(this));
//        }
    }

    @Override
    protected void method_5959() {
        super.method_5959();
        this.goalSelector.add(99, new InertGoal(this));
    }

    @Override
    public Optional<UUID> getPossessingEntityUuid() {
        return Optional.ofNullable(possessorUuid);
    }

    @Override
    public Optional<PlayerEntity> getPossessingEntity() {
        return getPossessingEntityUuid().map(world::getPlayerByUuid);
    }

    @Override
    public boolean canBePossessedBy(PlayerEntity player) {
        return !this.isBeingPossessed();
    }

    @Override
    public void setPossessingEntity(@Nullable UUID possessorUuid) {
        this.possessorUuid = possessorUuid;
    }

    @Override
    public void update() {
        if (!this.world.isClient) {
            this.getPossessingEntity().ifPresent(player -> {
                if (this instanceof Monster && this.world.getDifficulty() == Difficulty.PEACEFUL) {
                    player.addChatMessage(new TranslatableTextComponent("dissolution.message.peaceful_despawn"), true);
                }
            });
        }
        super.update();
    }

    @Override
    public void method_6091(float strafe, float vertical, float forward) {
        Optional<PlayerEntity> optionalPlayer = this.getPossessingEntity();
        if (optionalPlayer.isPresent()) {
            PlayerEntity player = optionalPlayer.get();
            this.yaw = player.yaw;
            this.prevYaw = this.yaw;
            this.pitch = player.pitch;
            this.setRotation(this.yaw, this.pitch);
            this.field_6283 = this.yaw;
            this.headYaw = this.field_6283;
            this.fallDistance = player.fallDistance;

            super.method_6091(strafe, vertical, forward);
            this.setPosition(player.x, player.y, player.z);
            this.field_6249 = player.field_6249;
            this.field_6225 = player.field_6225;
        } else {
            super.method_6091(strafe, vertical, forward);
        }
    }

    @Nullable
    @Override
    public Entity getPrimaryPassenger() {
        // Allows this entity to move client-side, in conjunction with #method_5956
        return this.getPossessingEntity().orElse(null);
    }

    @Override
    public boolean method_5956() {
        // Allows this entity to move client side
        return super.method_5956() || this.isBeingPossessed();
    }

    @Override
    public void pushAwayFrom(Entity entityIn) {
        // Prevent infinite propulsion through self collision
        if (!getPossessingEntityUuid().filter(entityIn.getUuid()::equals).isPresent()) {
            super.pushAwayFrom(entityIn);
        }
    }

    @Override
    protected void pushAway(Entity entityIn) {
        // Prevent infinite propulsion through self collision
        if (!getPossessingEntityUuid().filter(entityIn.getUuid()::equals).isPresent()) {
            super.pushAwayFrom(entityIn);
        }
    }

    @Override
    public boolean canUsePortals() {
        // This entity's dimension should always be changed by the player
        return false;
    }

    @Override
    public void onDeath(DamageSource damageSource_1) {
        super.onDeath(damageSource_1);
        this.getPossessingEntity().ifPresent(possessor -> {
            ((Possessor)possessor).stopPossessing();
            if (!world.isClient && !world.getGameRules().getBoolean("keepInventory")) {
                possessor.inventory.dropAll();
            }
        });

    }

    /* * * * * * * * * * *
      Plenty of delegation
     * * * * * * * * * * * */

    /**
     * Knockback
     */
    @Override
    public void method_6005(Entity entity_1, float float_1, double double_1, double double_2) {
        Optional<PlayerEntity> possessing = getPossessingEntity();
        if (possessing.isPresent()) {
            possessing.get().method_6005(entity_1, float_1, double_1, double_2);
        } else {
            super.method_6005(entity_1, float_1, double_1, double_2);
        }
    }

    @Override
    public boolean startRiding(Entity entity_1) {
        Optional<PlayerEntity> possessing = getPossessingEntity();
        return possessing
                .map(playerEntity -> playerEntity.startRiding(entity_1))
                .orElseGet(() -> super.startRiding(entity_1));
    }

    /**
     * @return Whether this entity is using a shield or equivalent
     */
    @Override
    public boolean method_6039() {
        Optional<PlayerEntity> possessing = getPossessingEntity();
        return possessing
                .map(LivingEntity::method_6039)
                .orElseGet(super::method_6039);
    }

    @Override
    public Iterable<ItemStack> getItemsHand() {
        Optional<PlayerEntity> possessing = getPossessingEntity();
        return possessing
                .map(PlayerEntity::getItemsHand)
                .orElseGet(super::getItemsHand);
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot var1) {
        return getPossessingEntity()
                .map(playerEntity -> playerEntity.getEquippedStack(var1))
                .orElseGet(() -> super.getEquippedStack(var1));
    }

    @Override
    public ItemStack getActiveItem() {
        return getPossessingEntity()
                .map(LivingEntity::getActiveItem)
                .orElseGet(super::getActiveItem);
    }

    @Override
    public void setEquippedStack(EquipmentSlot var1, ItemStack var2) {
        Optional<PlayerEntity> possessing = getPossessingEntity();
        if (possessing.filter(p -> !p.world.isClient).isPresent()) {
            possessing.get().setEquippedStack(var1, var2);
        } else {
            super.setEquippedStack(var1, var2);
        }
    }

    @Override
    public boolean isEquippedStackValid(EquipmentSlot equipmentSlot_1) {
        return getPossessingEntity()
                .map(playerEntity -> playerEntity.isEquippedStackValid(equipmentSlot_1))
                .orElseGet(() -> super.isEquippedStackValid(equipmentSlot_1));
    }

    /**
     * @return true if this entity's main hand is active
     */
    @Override
    public boolean method_6115() {
        return getPossessingEntity()
                .map(LivingEntity::method_6115)
                .orElseGet(super::method_6115);
    }

    /**
     * Updates the logic for the held item when the hand is active
     */
    @Override
    public void method_6076() {
        // Not actual delegation, we just avoid updating the item twice
        if (!getPossessingEntityUuid().isPresent()) {
            super.method_6076();
        }
    }
}