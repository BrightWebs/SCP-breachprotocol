package com.breachprotocol.entity;

import com.breachprotocol.career.Career;
import com.breachprotocol.career.Rank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Foundation personnel who work at your sites: researchers, security, medics, technicians, clerks
 * and janitors. They go about their floor, sit down on the site's chairs now and then, and speak to
 * you when they see you, according to your rank (a D-Class gets ordered about; the Administrator
 * gets everyone on their feet). Right-click one to talk; some can help.
 */
public class FoundationStaff extends PathfinderMob {
    public enum Role {
        RESEARCHER("Researcher", "Dr."), GUARD("Security", "Officer"), MEDIC("Medical", "Dr."),
        TECHNICIAN("Technician", "Tech"), CLERK("Administration", ""), JANITOR("Janitorial", "");

        public final String title, prefix;

        Role(String title, String prefix) { this.title = title; this.prefix = prefix; }
    }

    private static final EntityDataAccessor<Integer> ROLE = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(FoundationStaff.class, EntityDataSerializers.INT);

    private static final String[] SURNAMES = {"Mbeki", "Okafor", "Lindqvist", "Tanaka", "Morales", "Kowalski", "Haddad", "Novak", "Reyes", "Ivanova",
            "Clef", "Bright", "Gears", "Glass", "Kondraki", "Light", "Rights", "King", "Crow", "Everwood", "Harrow", "Vance", "Mercer", "Osei"};

    private final Map<UUID, Long> greeted = new HashMap<>();
    private BlockPos post;
    private long healReady;

    public FoundationStaff(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.FOLLOW_RANGE, 24);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ROLE, 0);
        builder.define(VARIANT, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3));
        goalSelector.addGoal(2, new SitGoal(this, null, 0.006F, 20 * 30, 20 * 150, 8, pos -> com.breachprotocol.world.Site01.mayUseChair(this, pos)));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8F, 0.6F));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.01F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    public Role role() { return Role.values()[Math.floorMod(entityData.get(ROLE), Role.values().length)]; }

    public int variant() { return entityData.get(VARIANT); }

    /** Sets who they are and where they work (they stay within 12 blocks of it). */
    public void setup(Role role, BlockPos post) {
        entityData.set(ROLE, role.ordinal());
        entityData.set(VARIANT, random.nextInt(3));
        this.post = post;
        String sur = SURNAMES[random.nextInt(SURNAMES.length)];
        setCustomName(Component.literal((role.prefix.isEmpty() ? "" : role.prefix + " ") + sur).withStyle(role == Role.GUARD ? ChatFormatting.DARK_AQUA : ChatFormatting.WHITE));
        setCustomNameVisible(false);
        restrictTo(post, 12);
    }

    private String name() { return getCustomName() == null ? role().title : getCustomName().getString(); }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 20 == 1 && com.breachprotocol.world.Site01.stale(this)) { Seats.stand(this); discard(); return; }
        if (level().isClientSide || tickCount % 20 != 0) return;
        if (post != null && tickCount % 200 == 0) restrictTo(post, 12);
        Player near = level().getNearestPlayer(this, 6);
        if (!(near instanceof ServerPlayer p) || p.isSpectator() || !hasLineOfSight(p)) return;
        long now = level().getGameTime();
        Long last = greeted.get(p.getUUID());
        if (last != null && now - last < 20 * 150) return;
        greeted.put(p.getUUID(), now);
        getLookControl().setLookAt(p, 30, 30);
        Rank r = Career.rank(p);
        // the top brass gets everyone on their feet
        if ((r.atLeast(Rank.OVERSEER) || Career.isAscended(p)) && Seats.seated(this)) Seats.stand(this);
        say(p, greeting(p, r));
    }

    private String greeting(ServerPlayer p, Rank r) {
        String a = Career.address(p);
        if (Career.isAscended(p)) return pick("...You. Please. I have a family.", "Don't look at it. Don't look at it.", "S-sir. Whatever you need.");
        return switch (r) {
            case CLASS_D -> switch (role()) {
                case GUARD -> pick("Eyes down, " + a + ". Keep moving.", "Hands where I can see them, " + a + ".", "You're out of your block, " + a + ". Back. Now.");
                case RESEARCHER -> pick("Ah, a test subject. Stay there.", a + ", you're on my schedule for later. Hydrate.");
                case MEDIC -> pick("Orange jumpsuit. Sit, I'll check your vitals.", "Still breathing, " + a + "? Good.");
                default -> pick("Mind the wet floor, " + a + ".", "Don't touch anything, " + a + ".");
            };
            case LEVEL_1, LEVEL_2 -> pick("Morning, " + a + ".", "Badge on you, " + a + "? Good.", "Coffee's fresh on the ground floor, " + a + ".");
            case LEVEL_3, LEVEL_4 -> pick("Officer " + a + ". Cell checks are done.", a + ". All quiet on this floor.", "Good to see you, " + a + ".");
            case LEVEL_5 -> pick("Director. The reports are on your desk.", "Director " + a + ". Everything's on schedule.", "Morning, Director.");
            case OVERSEER -> pick("O5. It's... an honour.", "Overseer. Anything you need.", "Overseer " + a + ". We're ready.");
            case ADMINISTRATOR -> pick("Administrator! Everyone up.", "Administrator. The site is yours.", "Administrator. We didn't know you were coming.");
        };
    }

    private String pick(String... s) { return s[random.nextInt(s.length)]; }

    private void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal(name() + ": ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(line).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p)) return InteractionResult.sidedSuccess(level().isClientSide);
        getLookControl().setLookAt(p, 30, 30);
        Rank r = Career.rank(p);
        String line = switch (role()) {
            case MEDIC -> {
                long now = level().getGameTime();
                if (r != Rank.CLASS_D && now >= healReady) {
                    healReady = now + 20 * 120;
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
                    yield "Hold still. There. You'll live, " + Career.address(p) + ".";
                }
                yield r == Rank.CLASS_D ? "Back of the queue, D-Class." : "Come back in a couple of minutes. I'm out of stims.";
            }
            case GUARD -> r.atLeast(Rank.LEVEL_3) ? "Containment's holding. If anything breaches, I'm right behind you." : "Keep your keycard visible.";
            case RESEARCHER -> pick("Never look away from 173. I mean it.", "Stasis pods only hold what they're rated for. Check the label.",
                    "A pumpkin on your head beats a rifle against 096.", "SCP-999 can clean 106's corrosion off you.", "Read the dossier before you open the door.");
            case TECHNICIAN -> pick("Power's stable. The creative supply in the plant feeds the whole site.", "If a door won't open, check the scanner on its jamb.",
                    "Server room's running cool today.");
            case CLERK -> pick("Your assignments are on your phone, " + Career.address(p) + ".", "Paperwork never ends here.", "The archive is on floor four.");
            case JANITOR -> pick("I don't clean the cells on B3. Nobody pays enough.", "Watch your step.", "Someone left 999 in the break room again.");
        };
        say(p, line);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Role", entityData.get(ROLE));
        tag.putInt("Variant", entityData.get(VARIANT));
        if (post != null) { tag.putInt("PostX", post.getX()); tag.putInt("PostY", post.getY()); tag.putInt("PostZ", post.getZ()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLE, tag.getInt("Role"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        if (tag.contains("PostX")) { post = new BlockPos(tag.getInt("PostX"), tag.getInt("PostY"), tag.getInt("PostZ")); restrictTo(post, 12); }
    }
}
