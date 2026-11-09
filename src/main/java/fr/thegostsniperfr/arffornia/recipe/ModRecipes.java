package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Arffornia.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Arffornia.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<MachineRecipe>> MACHINE_TYPE =
            TYPES.register("machine", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "machine")));
    public static final DeferredHolder<RecipeSerializer<?>, MachineRecipe.Serializer> MACHINE_SERIALIZER =
            SERIALIZERS.register("machine", MachineRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus eventBus) {
        TYPES.register(eventBus);
        SERIALIZERS.register(eventBus);
    }
}
