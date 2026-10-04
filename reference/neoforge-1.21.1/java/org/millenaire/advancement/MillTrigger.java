package org.millenaire.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.server.level.ServerPlayer;

public class MillTrigger extends SimpleCriterionTrigger<MillTrigger.Instance> {
   public Codec<MillTrigger.Instance> codec() {
      return MillTrigger.Instance.CODEC;
   }

   public void trigger(ServerPlayer player) {
      super.trigger(player, instance -> true);
   }

   public record Instance(Optional<ContextAwarePredicate> player) implements SimpleInstance {
      public static final Codec<MillTrigger.Instance> CODEC = RecordCodecBuilder.create(
         i -> i.group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(MillTrigger.Instance::player)).apply(i, MillTrigger.Instance::new)
      );
   }
}
