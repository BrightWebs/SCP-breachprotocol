package com.breachprotocol.client;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.network.ClientCareerState;
import com.breachprotocol.network.PhoneActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The command key (V by default), for the Administrator and SCP-Ω.
 *
 * <ul>
 * <li><b>Tap</b>: answer whoever is waiting on you (your secretary, the Council). <b>Shift + tap</b>: the console.</li>
 * <li><b>Hold</b>: the command stance. Whatever living thing you look at is marked; scroll to choose an
 *     order (Calm, Follow, Stay, Hunt, Release, End) and left-click to give it. Hunt asks for its prey:
 *     look at it and click. Right-click cancels. While the key is held, clicks and the scroll wheel
 *     belong to the stance, so nothing else you look at reacts to them: no trading screens, no other
 *     mod's menus, no swinging at what you meant to calm. Let go and everything is normal again.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BreachProtocol.MODID, value = Dist.CLIENT)
public final class CommandStance {
    private CommandStance() {}

    private static final int HOLD = 5;
    static final String[] ORDERS = {"calm", "follow", "stay", "hunt", "release", "end"};
    private static final String[] LABELS = {"Calm", "Follow", "Stay", "Hunt", "Release", "End"};
    private static final String[] HINTS = {"It will never raise a hand to you", "It walks at your side", "It holds its ground",
            "Then look at its prey", "Back to its own nature", "It dies"};
    private static final int[] COLOURS = {0x7FE0A0, 0x9FD8FF, 0xC8D0DA, 0xFFB050, 0xB8B8B8, 0xFF5A5A};

    private static int held, sel, pickTicks;
    private static boolean shiftAtPress;
    /** The key closed a screen: ignore it until it's let go. */
    static boolean suppress;
    private static boolean acted, active;
    private static int huntSubject = -1;
    private static LivingEntity looked;

    static int dom() { return ClientCareerState.tag.getInt("dom"); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { held = 0; active = false; return; }
        if (suppress) {
            boolean raw = !DialogueHud.ANSWER.isUnbound() && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), DialogueHud.ANSWER.getKey().getValue());
            while (DialogueHud.ANSWER.consumeClick()) { }
            if (raw || DialogueHud.ANSWER.isDown()) { held = 0; active = false; return; }
            suppress = false;
        }
        boolean down = DialogueHud.ANSWER.isDown() && mc.screen == null;
        while (DialogueHud.ANSWER.consumeClick()) { /* handled by press and release below */ }
        if (down) { if (held == 0) shiftAtPress = net.minecraft.client.gui.screens.Screen.hasShiftDown(); held++; }
        else if (held > 0) {
            if (!acted && held < HOLD) {
                // Shift + the key: the console. The key alone: answer whoever's waiting (your secretary, the Council)
                if (shiftAtPress) PowerKeys.openConsole();
                else if (DialogueHud.pending()) mc.setScreen(DialogueScreen.open(DialogueHud.hud));
            }
            held = 0;
            acted = false;
        }
        boolean was = active;
        active = down && held >= HOLD && dom() > 0 && !shiftAtPress;
        if (active && !was) mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.6F);
        // choosing prey outlives the key: it ends with a click, a right-click, or after twenty seconds
        if (huntSubject >= 0) {
            Entity s = mc.level == null ? null : mc.level.getEntity(huntSubject);
            if (s == null || !s.isAlive() || ++pickTicks > 400) { huntSubject = -1; note("Hunt cancelled."); }
            else if (mc.screen != null) huntSubject = -1;
        }
        boolean picking = huntSubject >= 0;
        looked = active || picking ? aim(mc) : null;
        if (picking && looked != null && looked.getId() == huntSubject) looked = null;
        if (picking && looked != null && mc.level != null && mc.level.getGameTime() % 2 == 0) {
            // the line from hunter to prey
            Entity s = mc.level.getEntity(huntSubject);
            if (s != null) {
                Vec3 a = s.position().add(0, s.getBbHeight() * 0.7, 0), b = looked.position().add(0, looked.getBbHeight() * 0.6, 0);
                int n = (int) Math.min(40, a.distanceTo(b) * 1.5);
                for (int i = 0; i <= n; i++) {
                    Vec3 q = a.lerp(b, i / (double) Math.max(1, n));
                    mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1F, 0.15F, 0.15F), 0.8F), q.x, q.y, q.z, 0, 0, 0);
                }
            }
        }
        if (looked != null && mc.level != null && mc.level.getGameTime() % 3 == 0) {
            Entity e = looked;
            for (int i = 0; i < 3; i++)
                mc.level.addParticle(huntSubject >= 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.END_ROD,
                        e.getX() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth() * 1.4, e.getY() + mc.level.random.nextDouble() * e.getBbHeight(),
                        e.getZ() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth() * 1.4, 0, 0.02, 0);
        }
        if (picking && mc.level != null) {
            Entity s = mc.level.getEntity(huntSubject);
            if (s != null && mc.level.getGameTime() % 4 == 0) mc.level.addParticle(ParticleTypes.ANGRY_VILLAGER, s.getX(), s.getY() + s.getBbHeight() + 0.4, s.getZ(), 0, 0, 0);
        }
    }

    /** The living thing under the crosshair, up to 48 blocks away and not behind a wall. */
    private static LivingEntity aim(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        if (cam == null) return null;
        Vec3 eye = cam.getEyePosition(1F), look = cam.getViewVector(1F), end = eye.add(look.scale(48));
        HitResult block = cam.pick(48, 1F, false);
        double max = block.getType() == HitResult.Type.MISS ? 48 * 48 : block.getLocation().distanceToSqr(eye);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, cam.getBoundingBox().expandTowards(look.scale(48)).inflate(1),
                e -> e instanceof LivingEntity && e != mc.player && !e.isSpectator() && e.isAlive(), max);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static void send(String action, int arg) { PacketDistributor.sendToServer(new PhoneActionPayload(action, arg)); }

    // ================================================================== the mouse belongs to the stance

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active && huntSubject < 0) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft mc = Minecraft.getInstance();
        if (event.isUseItem()) {
            if (huntSubject >= 0) { huntSubject = -1; note("Hunt cancelled."); mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.6F); }
            return;
        }
        if (!event.isAttack()) return;
        if (looked == null) { mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.4F, 0.6F); return; }
        String order = ORDERS[sel];
        if (huntSubject >= 0) {
            if (looked.getId() == huntSubject) return;
            send("dom:hunt:" + looked.getId(), huntSubject);
            huntSubject = -1;
            acted = held > 0;
            mc.player.playSound(SoundEvents.WARDEN_ANGRY, 0.4F, 1.6F);
            return;
        }
        if (order.equals("hunt")) {
            huntSubject = looked.getId();
            pickTicks = 0;
            acted = true;
            note("Now look at its prey and left-click. You can let go of " + DialogueHud.ANSWER.getTranslatedKeyMessage().getString() + ".");
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F);
            return;
        }
        send("dom:" + order, looked.getId());
        acted = true;
        mc.player.playSound(order.equals("end") ? SoundEvents.WARDEN_SONIC_CHARGE : SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, order.equals("end") ? 1.4F : 1.2F);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!active && huntSubject < 0) return;
        event.setCanceled(true);
        if (huntSubject >= 0) return;
        sel = Math.floorMod(sel + (event.getScrollDeltaY() > 0 ? -1 : 1), ORDERS.length);
        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.8F);
    }

    private static void note(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ================================================================== drawing

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        if (!active && huntSubject < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2, cy = h / 2;
        g.fillGradient(0, 0, w, h / 5, 0x66200030, 0x00000000);
        g.fillGradient(0, h - h / 5, w, h, 0x00000000, 0x66200030);
        // the reticle
        int r = 9;
        int rc = huntSubject >= 0 ? 0xFFFF6A6A : looked != null ? 0xFFD8A8FF : 0x88FFFFFF;
        g.fill(cx - r, cy - r, cx - r + 4, cy - r + 1, rc); g.fill(cx - r, cy - r, cx - r + 1, cy - r + 4, rc);
        g.fill(cx + r - 4, cy - r, cx + r, cy - r + 1, rc); g.fill(cx + r - 1, cy - r, cx + r, cy - r + 4, rc);
        g.fill(cx - r, cy + r - 1, cx - r + 4, cy + r, rc); g.fill(cx - r, cy + r - 4, cx - r + 1, cy + r, rc);
        g.fill(cx + r - 4, cy + r - 1, cx + r, cy + r, rc); g.fill(cx + r - 1, cy + r - 4, cx + r, cy + r, rc);
        // the orders, in a ring round the crosshair (not while choosing prey)
        int ring = 58;
        for (int i = 0; i < ORDERS.length && huntSubject < 0; i++) {
            double a = -Math.PI / 2 + i * (Math.PI * 2 / ORDERS.length);
            int ox = cx + (int) (Math.cos(a) * ring), oy = cy + (int) (Math.sin(a) * ring * 0.72);
            boolean on = i == sel && huntSubject < 0;
            String label = LABELS[i];
            int bw = f.width(label) + 12;
            int fill = on ? 0xE0301848 : 0x90101018, edge = on ? 0xFF000000 | COLOURS[i] : 0x60FFFFFF;
            g.fill(ox - bw / 2, oy - 7, ox + bw / 2, oy + 7, fill);
            g.fill(ox - bw / 2, oy - 7, ox + bw / 2, oy - 6, edge);
            g.fill(ox - bw / 2, oy + 6, ox + bw / 2, oy + 7, edge);
            g.drawString(f, label, ox - f.width(label) / 2, oy - 4, on ? 0xFF000000 | COLOURS[i] : 0xFFB8B8C8, false);
        }
        // what you're looking at, and what the order will do
        String top, sub;
        if (huntSubject >= 0) {
            Entity s = mc.level == null ? null : mc.level.getEntity(huntSubject);
            top = "CHOOSE PREY FOR " + (s == null ? "IT" : s.getName().getString().toUpperCase(java.util.Locale.ROOT));
            sub = looked == null ? "Look at what it should hunt" : "Left-click: " + (s == null ? "it" : s.getName().getString()) + " hunts " + looked.getName().getString();
        } else if (looked != null) {
            top = looked.getName().getString() + "  ·  " + (int) Math.ceil(looked.getHealth()) + " / " + (int) Math.ceil(looked.getMaxHealth());
            sub = LABELS[sel] + ": " + HINTS[sel];
        } else {
            top = "Look at a living thing";
            sub = LABELS[sel] + ": " + HINTS[sel];
        }
        int ty = cy + ring + 18;
        g.drawString(f, top, cx - f.width(top) / 2, ty, huntSubject >= 0 ? 0xFFFF8A8A : 0xFFEAD8FF, true);
        g.drawString(f, sub, cx - f.width(sub) / 2, ty + 11, 0xFFA898C0, true);
        String help = huntSubject >= 0 ? "Left-click: hunt  ·  Right-click: cancel  ·  " + (20 - pickTicks / 20) + "s"
                : "Scroll: choose  ·  Left-click: command  ·  Right-click: cancel  ·  Release " + DialogueHud.ANSWER.getTranslatedKeyMessage().getString() + ": done";
        g.drawString(f, help, cx - f.width(help) / 2, h - 18, 0xFFB8A8D0, true);
        String title = dom() >= 2 ? "SCP-Ω  ·  DOMINION" : "ADMINISTRATOR  ·  COMMAND";
        g.drawString(f, title, cx - f.width(title) / 2, 10, 0xFFD8A8FF, true);
    }
}
