package org.millenaire.fabric.villager;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/**
 * A Millénaire villager. Identity (culture, type, names, home building) is server-side state; the skin,
 * clothing layers and body model are synchronised so the client renderer can draw the legacy 64x32 skins.
 */
public class MillVillagerEntity extends PathfinderMob implements net.minecraft.world.item.trading.Merchant {
    private static final EntityDataAccessor<String> TEXTURE = SynchedEntityData.defineId(MillVillagerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CLOTH_0 = SynchedEntityData.defineId(MillVillagerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CLOTH_1 = SynchedEntityData.defineId(MillVillagerEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> MODEL = SynchedEntityData.defineId(MillVillagerEntity.class, EntityDataSerializers.BYTE);
    public static final String DEFAULT_TEXTURE = "textures/entity/norman/male/nor_peasant_0.png";

    private String culture = "";
    private String villagerType = "";
    private String firstName = "";
    private String familyName = "";
    private String building = "";
    /** Goods carried by the villager, by itemlist alias. */
    private final java.util.Map<String, Integer> inventory = new java.util.TreeMap<>();
    /** Created in registerGoals, which the Mob constructor calls before field initializers run. */
    private org.millenaire.fabric.goal.MillenaireBrain brain;

    public MillVillagerEntity(EntityType<? extends MillVillagerEntity> type, Level level) {
        super(type, level);
        getNavigation().setCanOpenDoors(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TEXTURE, DEFAULT_TEXTURE).define(CLOTH_0, "").define(CLOTH_1, "").define(MODEL, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        brain = new org.millenaire.fabric.goal.MillenaireBrain(this);
        goalSelector.addGoal(2, brain);
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Applies a freshly rolled identity; scale uses the vanilla scale attribute so the hitbox follows. */
    public void initialize(VillagerProfile profile, VillagerProfile.Appearance appearance, String building) {
        culture = profile.culture();
        villagerType = profile.type();
        firstName = appearance.firstName();
        familyName = appearance.familyName();
        this.building = building == null ? "" : building;
        entityData.set(TEXTURE, appearance.texture());
        entityData.set(CLOTH_0, appearance.cloth0().orElse(""));
        entityData.set(CLOTH_1, appearance.cloth1().orElse(""));
        entityData.set(MODEL, (byte) profile.model().ordinal());
        inventory.clear();
        inventory.putAll(profile.startingInventory());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(profile.health());
        getAttribute(Attributes.SCALE).setBaseValue(appearance.scale());
        setHealth(profile.health());
        updateName();
    }

    private void updateName() {
        String name = familyName.isEmpty() ? firstName : firstName + " " + familyName;
        if (!name.isEmpty()) {
            setCustomName(Component.literal(name));
            setCustomNameVisible(false);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("culture", culture);
        output.putString("villager_type", villagerType);
        output.putString("first_name", firstName);
        output.putString("family_name", familyName);
        output.putString("building", building);
        output.putString("texture", entityData.get(TEXTURE));
        output.putString("cloth_0", entityData.get(CLOTH_0));
        output.putString("cloth_1", entityData.get(CLOTH_1));
        output.putInt("model", entityData.get(MODEL));
        output.store("inventory", com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.INT), inventory);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        culture = input.getStringOr("culture", "");
        villagerType = input.getStringOr("villager_type", "");
        firstName = input.getStringOr("first_name", "");
        familyName = input.getStringOr("family_name", "");
        building = input.getStringOr("building", "");
        entityData.set(TEXTURE, input.getStringOr("texture", DEFAULT_TEXTURE));
        entityData.set(CLOTH_0, input.getStringOr("cloth_0", ""));
        entityData.set(CLOTH_1, input.getStringOr("cloth_1", ""));
        entityData.set(MODEL, (byte) VillagerProfile.Model.byId(input.getIntOr("model", 0)).ordinal());
        inventory.clear();
        input.read("inventory", com.mojang.serialization.Codec.unboundedMap(com.mojang.serialization.Codec.STRING, com.mojang.serialization.Codec.INT))
                .ifPresent(inventory::putAll);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    // Trading: the shop of the villager's building, shown in the vanilla merchant screen.
    private net.minecraft.world.entity.player.Player tradingPlayer;
    private VillagerTrading.Session tradeSession;
    private net.minecraft.world.item.trading.MerchantOffers offers = new net.minecraft.world.item.trading.MerchantOffers();

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (!isAlive() || isSleeping() || tradingPlayer != null || player.isSecondaryUseActive()) return super.mobInteract(player, hand);
        if (level().isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        var session = VillagerTrading.open(this, player);
        if (session.isEmpty()) {
            player.sendSystemMessage(VillagerTrading.noShop(this));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        tradeSession = session.get();
        offers = tradeSession.offers();
        setTradingPlayer(player);
        getNavigation().stop();
        openTradingScreen(player, getDisplayName(), 1);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override public void setTradingPlayer(net.minecraft.world.entity.player.Player player) {
        tradingPlayer = player;
        if (player == null) tradeSession = null;
    }
    @Override public net.minecraft.world.entity.player.Player getTradingPlayer() { return tradingPlayer; }
    @Override public net.minecraft.world.item.trading.MerchantOffers getOffers() { return offers; }
    @Override public void overrideOffers(net.minecraft.world.item.trading.MerchantOffers offers) { this.offers = offers; }
    @Override public void notifyTrade(net.minecraft.world.item.trading.MerchantOffer offer) {
        offer.increaseUses();
        if (tradeSession != null && tradingPlayer != null) VillagerTrading.completed(tradeSession, offer, tradingPlayer);
    }
    @Override public void notifyTradeUpdated(net.minecraft.world.item.ItemStack stack) {}
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) {}
    @Override public boolean showProgressBar() { return false; }
    @Override public net.minecraft.sounds.SoundEvent getNotifyTradeSound() { return net.minecraft.sounds.SoundEvents.VILLAGER_YES; }
    @Override public boolean isClientSide() { return level().isClientSide(); }
    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return tradingPlayer == player && isAlive() && player.distanceToSqr(this) < 64.0;
    }

    public int carried(String good) { return inventory.getOrDefault(good, 0); }
    public java.util.Map<String, Integer> carriedGoods() { return java.util.Collections.unmodifiableMap(inventory); }
    /** Adds or removes carried goods; never goes below zero. Returns the applied change. */
    public int changeCarried(String good, int delta) {
        int current = inventory.getOrDefault(good, 0);
        int next = Math.max(0, current + delta);
        if (next == 0) inventory.remove(good); else inventory.put(good, next);
        return next - current;
    }

    public java.util.Optional<String> activity() { return brain == null ? java.util.Optional.empty() : brain.currentLabel(); }
    public String culture() { return culture; }
    public String villagerType() { return villagerType; }
    public String profileId() { return culture + "/" + villagerType; }
    public String building() { return building; }
    public String firstName() { return firstName; }
    public String familyName() { return familyName; }
    public String texture() { return entityData.get(TEXTURE); }
    public Optional<String> cloth(int layer) {
        String value = entityData.get(layer == 0 ? CLOTH_0 : CLOTH_1);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
    public VillagerProfile.Model model() { return VillagerProfile.Model.byId(entityData.get(MODEL)); }
}
