package fr.thegostsniperfr.arffornia.client.crate;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateDefinition;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import fr.thegostsniperfr.arffornia.crate.CrateKey;
import fr.thegostsniperfr.arffornia.item.ModDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Gives each crate's key the model its crate definition names ({@code key_model}). Every item model named
 * {@code models/item/key_*.json} or under {@code models/item/crate_key/}, in any namespace, is loaded, since crate
 * definitions arrive with the data, after models are baked.
 */
public final class CrateKeyModels {
    private static final ModelResourceLocation CRATE_KEY = ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crate_key"));

    private CrateKeyModels() {
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        Minecraft.getInstance().getResourceManager()
                .listResources("models/item", file -> file.getPath().endsWith(".json")
                        && (file.getPath().startsWith("models/item/key_") || file.getPath().startsWith("models/item/crate_key/")))
                .keySet()
                .forEach(file -> event.register(ModelResourceLocation.standalone(file.withPath(path ->
                        path.substring("models/".length(), path.length() - ".json".length())))));
    }

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        event.getModels().computeIfPresent(CRATE_KEY, (location, model) -> new PerCrate(model));
    }

    private static final class PerCrate extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides = new ItemOverrides() {
            @Nullable
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                BakedModel keyModel = keyModel(stack);
                if (keyModel != null) {
                    return keyModel.getOverrides().resolve(keyModel, stack, level, entity, seed);
                }
                return originalModel.getOverrides().resolve(originalModel, stack, level, entity, seed);
            }
        };

        PerCrate(BakedModel original) {
            super(original);
        }

        @Nullable
        private static BakedModel keyModel(ItemStack stack) {
            CrateKey key = stack.get(ModDataComponents.CRATE_KEY.get());
            CrateDefinition.Client crate = key == null ? null : CrateDefinitions.find(key.crate());
            if (crate == null) {
                return null;
            }
            var models = Minecraft.getInstance().getModelManager();
            BakedModel model = models.getModel(ModelResourceLocation.standalone(crate.keyModel()));
            return model == models.getMissingModel() ? null : model;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
