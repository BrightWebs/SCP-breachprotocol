package com.breachprotocol.client.render;

import com.breachprotocol.BreachProtocol;
import com.breachprotocol.entity.FoundationStaff;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

/**
 * Foundation personnel, animated: a proper walk and run (lean, bob, swing), idle breathing and
 * weight-shifting, a look around now and then, blinking, hand gestures while they talk to you, a
 * wave when they greet you, and a two-handed aim when they shoot. Women have their own figure
 * (one of three builds, always in uniform).
 */
public class StaffModel extends HumanoidModel<FoundationStaff> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BreachProtocol.id("staff"), "main");

    private final ModelPart lids;
    private final ModelPart[] bust;

    public StaffModel(ModelPart root) {
        super(root);
        lids = head.getChild("lids");
        bust = new ModelPart[]{body.getChild("bust_s"), body.getChild("bust_m"), body.getChild("bust_l")};
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        PartDefinition head = root.getChild("head");
        head.addOrReplaceChild("lids", CubeListBuilder.create().texOffs(24, 32).addBox(-3.0F, -4.0F, -4.05F, 6, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("bust_s", CubeListBuilder.create().texOffs(0, 32).addBox(-3.0F, 0.0F, -1.0F, 6, 3, 1), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.12F, 0, 0));
        body.addOrReplaceChild("bust_m", CubeListBuilder.create().texOffs(0, 40).addBox(-3.0F, 0.0F, -2.0F, 6, 3, 2), PartPose.offsetAndRotation(0.0F, 2.0F, -1.6F, -0.16F, 0, 0));
        body.addOrReplaceChild("bust_l", CubeListBuilder.create().texOffs(0, 48).addBox(-3.0F, 0.0F, -3.0F, 6, 4, 3), PartPose.offsetAndRotation(0.0F, 1.8F, -1.4F, -0.2F, 0, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(FoundationStaff e, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        boolean armed = !e.getMainHandItem().isEmpty();
        boolean aiming = armed && e.isAggressive();
        rightArmPose = aiming ? ArmPose.BOW_AND_ARROW : armed ? ArmPose.ITEM : ArmPose.EMPTY;
        leftArmPose = ArmPose.EMPTY;
        super.setupAnim(e, limbSwing, amount, age, headYaw, headPitch);

        float id = e.getId() * 13.7F;
        boolean run = e.isSprinting();
        if (!riding) {
            // the gait: a lean and a bob in the step, more of both when running
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
            // standing about: breathing, shifting weight, glancing around
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
        // talking to you: hands move with what they say, a nod now and then
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
        // a wave hello
        if (e.isWaving() && !aiming) {
            rightArm.xRot = -2.7F;
            rightArm.yRot = 0;
            rightArm.zRot = -0.25F + Mth.sin(age * 0.9F) * 0.4F;
        }
        // blinking
        lids.visible = ((age + id) % 90F) < 3.5F;
        // her build
        int b = e.isFemale() ? e.bust() : -1;
        for (int i = 0; i < 3; i++) bust[i].visible = b == i;
    }
}
