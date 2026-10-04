package org.millenaire.fabric.villager.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import org.millenaire.fabric.villager.VillagerProfile;

public class MillVillagerRenderState extends HumanoidRenderState {
    public Identifier texture;
    public Identifier cloth0;
    public Identifier cloth1;
    public VillagerProfile.Model model = VillagerProfile.Model.MALE;
}
