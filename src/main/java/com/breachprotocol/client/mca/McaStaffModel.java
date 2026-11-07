/*
 * Breach Protocol - Foundation staff model.
 *
 * Adapted from Minecraft Comes Alive Reborn (VillagerEntityBaseModelMCA, VillagerEntityModelMCA,
 * CommonVillagerModel), Copyright (C) Conczin and the MCA Reborn contributors, licensed under the
 * GNU General Public License v3.0. https://github.com/Luke100000/minecraft-comes-alive
 * Modifications: Foundation staff entity, uniforms, gestures, aiming, gait and idle animation.
 */
package com.breachprotocol.client.mca;

import com.breachprotocol.BreachProtocol;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Map;

/** A player-shaped body with clothing layers and a chest that follows the torso, animated like a person. */
public class McaStaffModel<T extends net.minecraft.world.entity.Mob & com.breachprotocol.entity.Person> extends HumanoidModel<T> {
    public static final ModelLayerLocation BODY = layer("body"), FACE = layer("face"), CLOTHES = layer("clothes"), HAIR = layer("hair");

    private static ModelLayerLocation layer(String n) { return new ModelLayerLocation(BreachProtocol.id("mca_staff"), n); }

    protected static final String BREASTS = "breasts", BREASTPLATE = "breastplate";

    public final ModelPart breasts, breastsWear, leftArmwear, rightArmwear, leftLegwear, rightLegwear, bodyWear;
    private float breastSize;
    private boolean wearsHidden;

    public McaStaffModel(ModelPart tree) {
        super(tree);
        breasts = child(tree, BREASTS);
        breastsWear = child(tree, BREASTPLATE);
        bodyWear = tree.getChild(PartNames.JACKET);
        leftArmwear = tree.getChild("left_sleeve");
        rightArmwear = tree.getChild("right_sleeve");
        leftLegwear = tree.getChild("left_pants");
        rightLegwear = tree.getChild("right_pants");
    }

    static ModelPart child(ModelPart root, String name) { return root.hasChild(name) ? root.getChild(name) : new ModelPart(List.of(), Map.of()); }

    // ================================================================== meshes

    protected static CubeListBuilder newBreasts(CubeDeformation dilation, int oy) {
        return CubeListBuilder.create().texOffs(18, 21 + oy).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
    }

    public static MeshDefinition bodyData(CubeDeformation dilation) {
        MeshDefinition mesh = PlayerModel.createMesh(dilation, false);
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        root.addOrReplaceChild(BREASTPLATE, newBreasts(dilation.extend(0.1F), 16), PartPose.ZERO);
        return mesh;
    }

    public static MeshDefinition hairData(CubeDeformation dilation) {
        MeshDefinition mesh = bodyData(dilation);
        mesh.getRoot().addOrReplaceChild(PartNames.HAT, CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)), PartPose.ZERO);
        return mesh;
    }

    public static LayerDefinition body() { return LayerDefinition.create(bodyData(CubeDeformation.NONE), 64, 64); }

    public static LayerDefinition face() { return LayerDefinition.create(bodyData(new CubeDeformation(0.01F)), 64, 64); }

    public static LayerDefinition clothes() { return LayerDefinition.create(bodyData(new CubeDeformation(0.0625F)), 64, 64); }

    public static LayerDefinition hair() { return LayerDefinition.create(hairData(new CubeDeformation(0.125F)), 64, 64); }

    // ================================================================== parts

    @Override
    protected Iterable<ModelPart> headParts() { return ImmutableList.of(head, hat); }

    @Override
    protected Iterable<ModelPart> bodyParts() { return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, bodyWear, leftLegwear, rightLegwear, leftArmwear, rightArmwear); }

    public List<ModelPart> breastParts() { return List.of(breasts, breastsWear); }

    public McaStaffModel<T> hideWears() {
        wearsHidden = true;
        breastsWear.visible = false;
        leftArmwear.visible = rightArmwear.visible = leftLegwear.visible = rightLegwear.visible = bodyWear.visible = false;
        return this;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);
        leftArmwear.visible = rightArmwear.visible = leftLegwear.visible = rightLegwear.visible = bodyWear.visible = !wearsHidden && visible;
    }

    void syncWearParts() {
        leftLegwear.copyFrom(leftLeg);
        rightLegwear.copyFrom(rightLeg);
        leftArmwear.copyFrom(leftArm);
        rightArmwear.copyFrom(rightArm);
        bodyWear.copyFrom(body);
        breastsWear.copyFrom(breasts);
    }

    /** What the layer above shows, mirrored here. */
    public void copyVisibility(HumanoidModel<?> m) {
        boolean wear = !wearsHidden;
        head.visible = m.head.visible;
        hat.visible = m.head.visible;
        body.visible = m.body.visible;
        bodyWear.visible = wear && m.body.visible;
        breasts.visible = m.body.visible;
        breastsWear.visible = wear && m.body.visible;
        leftArm.visible = m.leftArm.visible;
        leftArmwear.visible = wear && m.leftArm.visible;
        rightArm.visible = m.rightArm.visible;
        rightArmwear.visible = wear && m.rightArm.visible;
        leftLeg.visible = m.leftLeg.visible;
        leftLegwear.visible = wear && m.leftLeg.visible;
        rightLeg.visible = m.rightLeg.visible;
        rightLegwear.visible = wear && m.rightLeg.visible;
    }

    // ================================================================== animation

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        boolean armed = !e.getMainHandItem().isEmpty();
        boolean aiming = armed && e.isAggressive();
        rightArmPose = aiming ? ArmPose.BOW_AND_ARROW : armed ? ArmPose.ITEM : ArmPose.EMPTY;
        leftArmPose = ArmPose.EMPTY;
        super.setupAnim(e, limbSwing / (0.2F + e.heightScale()), amount, age, headYaw, headPitch);

        float id = e.getId() * 13.7F;
        boolean run = e.isSprinting();
        if (!riding) {
            float step = Math.abs(Mth.cos(limbSwing * 0.6662F)) * amount;
            float bob = step * (run ? 1.4F : 0.7F);
            head.y += bob; body.y += bob; rightArm.y += bob; leftArm.y += bob;
            body.xRot += (run ? 0.28F : 0.06F) * Math.min(1, amount * 1.5F);
            if (run) {
                head.xRot -= 0.15F * amount;
                rightArm.xRot *= 1.35F;
                leftArm.xRot *= 1.35F;
                rightArm.zRot += 0.1F;
                leftArm.zRot -= 0.1F;
            }
            if (amount < 0.1F) {
                float breath = Mth.sin(age * 0.07F + id) * 0.4F;
                body.y += breath * 0.15F;
                head.y += breath * 0.15F;
                body.zRot = Mth.sin(age * 0.025F + id) * 0.025F;
                rightLeg.zRot = Math.max(0, Mth.sin(age * 0.025F + id)) * 0.04F;
                leftLeg.zRot = -Math.max(0, -Mth.sin(age * 0.025F + id)) * 0.04F;
                if (!e.isTalking()) head.yRot += Mth.sin(age * 0.018F + id) * 0.18F;
            }
        }
        if (e.isTalking() && !aiming) {
            float g = age * 0.2F + id;
            float beat = (Mth.sin(g) + 1) * 0.5F;
            rightArm.xRot = Mth.lerp(0.6F, rightArm.xRot, -0.35F - beat * 0.45F);
            rightArm.yRot = -0.3F + Mth.sin(g * 0.5F) * 0.15F;
            rightArm.zRot = 0.05F;
            if (Mth.sin(g * 0.37F) > 0.3F) {
                leftArm.xRot = Mth.lerp(0.5F, leftArm.xRot, -0.3F - (1 - beat) * 0.35F);
                leftArm.yRot = 0.25F;
            }
            head.xRot += Mth.sin(age * 0.42F + id) * 0.05F;
            head.zRot = Mth.sin(age * 0.11F + id) * 0.04F;
        }
        if (e.isWaving() && !aiming) {
            rightArm.xRot = -2.7F;
            rightArm.yRot = 0;
            rightArm.zRot = -0.25F + Mth.sin(age * 0.9F) * 0.4F;
        }
        // frightened (an anomaly loose and no gun): hands up, as MCA's panicking villagers do
        if (e.isFrightened() && !armed) {
            float r = (float) Math.PI / 180;
            float raise = ((Mth.sin(age / 5) * 30 - 180) + Mth.sin(age / 3) * 3) * r;
            float side = (Mth.sin(age / 2) * 12 - 17) * r;
            leftArm.xRot = raise;
            leftArm.zRot = -side;
            rightArm.xRot = -raise;
            rightArm.zRot = side;
        }

        // the chest: shown for women, sized and placed by her figure (MCA's geometry)
        breastSize = e.isFemale() ? e.bustSize() : 0;
        breasts.visible = e.isFemale() && body.visible;
        float by = (float) (5.0F - Math.pow(breastSize, 0.5) * 2.5F), bz = -1.5F + breastSize * 0.25F;
        for (ModelPart part : breastParts()) {
            part.setRotation((float) Math.PI * 0.3F, 0, 0);
            part.setPos(0.25F, by, bz);
        }
        hat.copyFrom(head);
        syncWearParts();
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof McaStaffModel<?> m) {
            m.breastSize = breastSize;
            m.breasts.visible = breasts.visible;
            m.breasts.copyFrom(breasts);
            m.hat.copyFrom(m.head);
            m.syncWearParts();
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int colour) {
        for (ModelPart p : headParts()) p.render(pose, vc, light, overlay, colour);
        syncWearParts();
        for (ModelPart p : bodyParts()) p.render(pose, vc, light, overlay, colour);
        if (breasts.visible && body.visible && breastSize > 0) {
            pose.pushPose();
            body.translateAndRotate(pose);
            pose.scale(breastSize * 0.2F + 1.05F, breastSize * 0.75F + 0.75F, breastSize * 0.75F + 0.75F);
            breasts.render(pose, vc, light, overlay, colour);
            if (breastsWear.visible) breastsWear.render(pose, vc, light, overlay, colour);
            pose.popPose();
        }
    }
}
