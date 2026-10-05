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
public class MillVillagerEntity extends PathfinderMob implements net.minecraft.world.item.trading.Merchant,
        net.minecraft.world.entity.monster.RangedAttackMob {
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
    /** The player who hired this villager, and until which game time. */
    private String hiredBy = "";
    private long hiredUntil;
    /** UUIDs of the parents, for villagers born in the village. */
    private String mother = "", father = "";
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
                .add(Attributes.FOLLOW_RANGE, 48.0).add(Attributes.ATTACK_DAMAGE, 1.0);
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
        VillagerCombat.register(this);
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
        adultScale = appearance.scale();
        childAge = profile.child() ? 0 : -1;
        updateScale();
        setHealth(profile.health());
        combatRole = null;
        setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, VillagerCombat.weapon(profile, good ->
                new org.millenaire.fabric.goal.ChestGoodsStore(null, java.util.List.of(), org.millenaire.fabric.MillenaireCommands.contentCatalog().goods()).prototype(good)));
        setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0.0F);
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
        output.putString("hired_by", hiredBy);
        output.putLong("hired_until", hiredUntil);
        output.putString("mother", mother);
        output.putString("father", father);
        output.putString("texture", entityData.get(TEXTURE));
        output.putString("cloth_0", entityData.get(CLOTH_0));
        output.putString("cloth_1", entityData.get(CLOTH_1));
        output.putInt("model", entityData.get(MODEL));
        output.putInt("child_age", childAge);
        output.putFloat("adult_scale", adultScale);
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
        hiredBy = input.getStringOr("hired_by", "");
        hiredUntil = input.getLongOr("hired_until", 0L);
        mother = input.getStringOr("mother", "");
        father = input.getStringOr("father", "");
        combatRole = null;
        childAge = input.getIntOr("child_age", -1);
        adultScale = input.getFloatOr("adult_scale", (float) getAttributeBaseValue(Attributes.SCALE));
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
        if (player.isSecondaryUseActive() && isAlive() && !level().isClientSide() && VillagerHiring.hireable(this)
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            VillagerHiring.offer(serverPlayer, this);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (!isAlive() || isSleeping() || tradingPlayer != null || player.isSecondaryUseActive() || isHired()
                || combatRole() == VillagerCombat.Role.HOSTILE || getTarget() != null) return super.mobInteract(player, hand);
        if (level().isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        // A quest step with this villager takes precedence over trading.
        if (org.millenaire.fabric.quest.QuestService.talk(this, player)) return net.minecraft.world.InteractionResult.SUCCESS;
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
    public String mother() { return mother; }
    public String father() { return father; }

    /** Sets the family a child is born into. */
    public void setFamily(String familyName, String mother, String father) {
        this.familyName = familyName == null ? "" : familyName;
        this.mother = mother == null ? "" : mother;
        this.father = father == null ? "" : father;
        updateName();
    }
    public String familyName() { return familyName; }
    public String texture() { return entityData.get(TEXTURE); }
    public Optional<String> cloth(int layer) {
        String value = entityData.get(layer == 0 ? CLOTH_0 : CLOTH_1);
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
    public VillagerProfile.Model model() { return VillagerProfile.Model.byId(entityData.get(MODEL)); }

    // Combat: roles come from the legacy profile tags, see VillagerCombat.
    private VillagerCombat.Role combatRole;

    public boolean isHired() { return !hiredBy.isEmpty(); }
    public boolean hiredBy(net.minecraft.world.entity.player.Player player) { return hiredBy.equals(player.getStringUUID()); }
    public java.util.UUID hirer() { return java.util.UUID.fromString(hiredBy); }
    public long hiredUntil() { return hiredUntil; }

    /** Hires the villager for a player until {@code until}, or releases it with a null player. */
    public void hire(java.util.UUID player, long until) {
        hiredBy = player == null ? "" : player.toString();
        hiredUntil = player == null ? 0 : until;
        if (player == null) setTarget(null);
    }

    /** Fighters by type, and anyone hired. */
    public boolean fights() { return combatRole() != VillagerCombat.Role.CIVILIAN || isHired(); }

    public java.util.Optional<VillagerProfile> profile() {
        return java.util.Optional.ofNullable(VillagerSpawning.snapshot().profiles().get(profileId()));
    }

    public VillagerCombat.Role combatRole() {
        if (combatRole == null) combatRole = VillagerCombat.role(profile().orElse(null));
        return combatRole;
    }

    public boolean isArcher() { return getMainHandItem().is(net.minecraft.world.item.Items.BOW); }

    net.minecraft.world.entity.ai.goal.GoalSelector goalSelector() { return goalSelector; }
    net.minecraft.world.entity.ai.goal.GoalSelector targetSelector() { return targetSelector; }

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            VillagerCombat.alert(this, attacker);
            if (attacker instanceof net.minecraft.world.entity.player.Player player && combatRole() != VillagerCombat.Role.HOSTILE)
                adjustReputation(level, player, -Math.max(1, Math.round(amount * VillagerCombat.REPUTATION_PER_DAMAGE)));
        }
        return hurt;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel && !isRemoved())
            profile().ifPresent(profile -> VillagerSpawning.record(serverLevel, this, profile, false));
        if (level() instanceof net.minecraft.server.level.ServerLevel level && combatRole() != VillagerCombat.Role.HOSTILE
                && source.getEntity() instanceof net.minecraft.world.entity.player.Player player)
            adjustReputation(level, player, -VillagerCombat.REPUTATION_PER_KILL);
        super.die(source);
    }

    private void adjustReputation(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.player.Player player, int delta) {
        if (player.isCreative() || building.isEmpty()) return;
        org.millenaire.fabric.goal.VillageContext.of(level, org.millenaire.fabric.MillenaireCommands.contentCatalog(), building)
                .ifPresent(context -> org.millenaire.fabric.FabricReputationState.get(level.getServer())
                        .add(VillagerTrading.villageKey(context), player.getUUID(), delta));
    }

    @Override
    public void performRangedAttack(net.minecraft.world.entity.LivingEntity target, float power) {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var bow = getMainHandItem();
        var arrow = net.minecraft.world.entity.projectile.ProjectileUtil.getMobArrow(this, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), power, bow);
        arrow.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
        double dx = target.getX() - getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        net.minecraft.world.entity.projectile.Projectile.spawnProjectileUsingShoot(arrow, level, bow, dx, dy + distance * 0.2, dz, 1.6F,
                rangedAttackUncertainty(level));
        playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
    }

    // Children: they grow over GROW_TICKS and then take a free adult place in the village (VillagePopulation).
    public static final int GROW_TICKS = 3 * 24000;
    private static final float CHILD_START_SCALE = 0.55F;
    private int childAge = -1;
    private float adultScale = 1.0F;

    public boolean isChildVillager() { return childAge >= 0; }

    private void updateScale() {
        float progress = childAge < 0 ? 1.0F : Math.min(1.0F, childAge / (float) GROW_TICKS);
        getAttribute(Attributes.SCALE).setBaseValue(adultScale * (CHILD_START_SCALE + (1.0F - CHILD_START_SCALE) * progress));
    }

    @Override
    protected void customServerAiStep(net.minecraft.server.level.ServerLevel level) {
        super.customServerAiStep(level);
        if (isHired() && tickCount % 100 == 0 && level.getGameTime() >= hiredUntil) {
            var hirer = level.getPlayerByUUID(hirer());
            if (hirer != null) hirer.sendSystemMessage(Component.literal(getName().getString() + "'s service is over; they go back home."));
            hire(null, 0);
        }
        if (childAge < 0) return;
        childAge++;
        if (childAge % 200 == 0) updateScale();
        if (childAge >= GROW_TICKS && childAge % 1200 == 0) growUp(level);
    }

    /** Becomes the adult type of a free place of the child's gender, preferring its own house. */
    public boolean growUp(net.minecraft.server.level.ServerLevel level) {
        var own = profile().orElse(null);
        if (own == null) return false;
        var settlement = org.millenaire.fabric.village.VillagePopulation.settlementOf(level.getServer(), level.dimension().identifier(), building);
        if (settlement.isEmpty()) return false;
        var profiles = VillagerSpawning.snapshot().profiles();
        String home = org.millenaire.fabric.village.VillagePopulation.baseKey(building);
        var vacancy = org.millenaire.fabric.village.VillagePopulation.vacancies(level.getServer(), settlement.get()).stream()
                .filter(v -> profiles.containsKey(v.profileId()) && profiles.get(v.profileId()).female() == own.female()
                        && !profiles.get(v.profileId()).child() && profiles.get(v.profileId()).culture().equals(own.culture()))
                .min(java.util.Comparator.comparing(v -> !org.millenaire.fabric.village.VillagePopulation.baseKey(v.building()).equals(home)));
        if (vacancy.isEmpty()) return false;
        var adult = profiles.get(vacancy.get().profileId());
        var names = VillagerSpawning.snapshot().nameLists().getOrDefault(adult.culture(), java.util.Map.of());
        var rolled = adult.roll(new java.util.SplittableRandom(getUUID().getLeastSignificantBits()), names);
        var appearance = new VillagerProfile.Appearance(rolled.texture(), rolled.cloth0(), rolled.cloth1(), rolled.scale(), firstName, familyName);
        initialize(adult, appearance, vacancy.get().building());
        VillagerSpawning.record(level, this, adult, true);
        return true;
    }
}
