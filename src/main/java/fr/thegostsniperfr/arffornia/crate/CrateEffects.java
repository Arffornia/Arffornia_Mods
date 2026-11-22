package fr.thegostsniperfr.arffornia.crate;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Particles and sounds of the opening sequence, played by each client from the shared timeline. Only uses
 * {@link Level} methods, which do nothing on a server, so it lives in common code next to the timeline.
 */
public final class CrateEffects {
    private CrateEffects() {
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CrateSequence.Show show, int tick) {
        if (!level.isClientSide) {
            return;
        }
        Vec3 centre = pos.getCenter();
        Direction facing = state.getValue(CrateBlock.FACING);
        Vec3 lock = centre.add(facing.getStepX() * 0.52, 0.05, facing.getStepZ() * 0.52);
        float[] crateColour = rgb(show.crateRarity().colour());
        float[] rollColour = rgb(show.rarity().colour());

        if (tick == CrateSequence.KEY_IN) {
            sound(level, pos, SoundEvents.VAULT_INSERT_ITEM, 1.0F, 1.0F);
        }
        if (tick == CrateSequence.CLICK) {
            sound(level, pos, SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.5F);
            sound(level, pos, SoundEvents.VAULT_ACTIVATE, 1.0F, 0.8F);
            for (int i = 0; i < 12; i++) {
                level.addParticle(new DustParticleOptions(new Vector3f(crateColour[0], crateColour[1], crateColour[2]), 1.0F),
                        lock.x + (level.random.nextDouble() - 0.5) * 0.3, lock.y + (level.random.nextDouble() - 0.5) * 0.3,
                        lock.z + (level.random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            }
        }

        if (tick >= CrateSequence.CHARGE && tick < CrateSequence.ROULETTE) {
            int step = tick - CrateSequence.CHARGE;
            if (tick == CrateSequence.CHARGE) {
                sound(level, pos, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.2F);
            }
            if (step % 2 == 0) {
                float pitch = 0.5F + 1.5F * step / (CrateSequence.ROULETTE - CrateSequence.CHARGE);
                sound(level, pos, SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F, pitch);
            }
            // Enchant particles fly from their offset to the given point: a spiral closing on the crate.
            for (int arm = 0; arm < 3; arm++) {
                double angle = step * 0.45 + arm * Math.PI * 2 / 3;
                double radius = 2.2 - step * 0.06;
                level.addParticle(ParticleTypes.ENCHANT, centre.x, centre.y + 0.4, centre.z,
                        Math.cos(angle) * radius, 1.2 - step * 0.04, Math.sin(angle) * radius);
            }
            level.addParticle(ParticleTypes.END_ROD, centre.x + (level.random.nextDouble() - 0.5) * 0.4, centre.y + 0.5,
                    centre.z + (level.random.nextDouble() - 0.5) * 0.4, 0, 0.12, 0);
        }

        if (tick == CrateSequence.ROULETTE) {
            sound(level, pos, SoundEvents.VAULT_OPEN_SHUTTER, 1.0F, 1.0F);
        }
        if (tick > CrateSequence.ROULETTE && tick <= CrateSequence.REVEAL) {
            int previous = (int) Math.round(CrateSequence.reelPosition(show, tick - 1));
            int current = (int) Math.round(CrateSequence.reelPosition(show, tick));
            if (current != previous) {
                float progress = (float) CrateSequence.rouletteProgress(tick);
                sound(level, pos, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F, 1.6F - 0.6F * progress);
                sound(level, pos, SoundEvents.UI_BUTTON_CLICK.value(), 0.25F, 1.8F);
            }
            if (tick % 3 == 0) {
                level.addParticle(ParticleTypes.WAX_OFF, centre.x + (level.random.nextDouble() - 0.5), centre.y + 1.0,
                        centre.z + (level.random.nextDouble() - 0.5), 0, 0.05, 0);
            }
        }

        if (tick == CrateSequence.REVEAL) {
            reveal(level, pos, centre, show, rollColour);
        }
        if (show.rarity().atLeast(CrateRarity.RARE) && tick >= CrateSequence.REVEAL && tick < CrateSequence.REVEAL + 10) {
            shockwave(level, centre, (tick - CrateSequence.REVEAL + 1) * 0.4, rollColour);
        }
        if (show.rarity() == CrateRarity.LEGENDARY && tick >= CrateSequence.REVEAL && tick < CrateSequence.FLY + 5) {
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, centre.x + (level.random.nextDouble() - 0.5) * 5,
                        centre.y + 4 + level.random.nextDouble() * 3, centre.z + (level.random.nextDouble() - 0.5) * 5,
                        0, -0.25, 0);
            }
        }
        if (tick >= CrateSequence.REVEAL && tick < CrateSequence.FLY && tick % 2 == 0) {
            level.addParticle(new DustParticleOptions(new Vector3f(rollColour[0], rollColour[1], rollColour[2]), 1.2F),
                    centre.x + (level.random.nextDouble() - 0.5) * 0.8, centre.y + 1.2 + level.random.nextDouble() * 0.6,
                    centre.z + (level.random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }

        if (tick == CrateSequence.FLY - 1) {
            sound(level, pos, SoundEvents.VAULT_EJECT_ITEM, 1.0F, 1.0F);
        }
        if (tick == CrateSequence.END - 1) {
            Entity opener = level.getEntity(show.openerId());
            if (opener != null) {
                level.playLocalSound(opener.getX(), opener.getY(), opener.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F,
                        1.4F + level.random.nextFloat() * 0.4F, false);
            }
        }
        if (tick == CrateSequence.END + 4) {
            sound(level, pos, SoundEvents.VAULT_CLOSE_SHUTTER, 0.8F, 1.0F);
        }
    }

    private static void reveal(Level level, BlockPos pos, Vec3 centre, CrateSequence.Show show, float[] colour) {
        boolean rare = show.rarity().atLeast(CrateRarity.RARE);
        FireworkExplosion burst = new FireworkExplosion(rare ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.BURST,
                IntList.of(show.rarity().colour()), IntList.of(0xFFFFFF), true, rare);
        level.createFireworks(centre.x, centre.y + 1.4, centre.z, 0, 0, 0, List.of(burst));
        sound(level, pos, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.2F);
        sound(level, pos, SoundEvents.VAULT_OPEN_SHUTTER, 1.0F, 0.6F);
        for (int i = 0; i < 20; i++) {
            level.addParticle(ParticleTypes.FIREWORK, centre.x, centre.y + 0.8, centre.z,
                    (level.random.nextDouble() - 0.5) * 0.4, level.random.nextDouble() * 0.4, (level.random.nextDouble() - 0.5) * 0.4);
        }
        if (rare) {
            sound(level, pos, SoundEvents.BELL_RESONATE, 1.5F, 0.6F);
            sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F);
        }
        if (show.rarity() == CrateRarity.LEGENDARY) {
            level.playLocalSound(centre.x, centre.y, centre.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.8F, false);
            sound(level, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }
    }

    private static void shockwave(Level level, Vec3 centre, double radius, float[] colour) {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(colour[0], colour[1], colour[2]), 1.6F);
        int points = Mth.ceil(radius * 14);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            level.addParticle(dust, centre.x + Math.cos(angle) * radius, centre.y - 0.4, centre.z + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    private static void sound(Level level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(pos, sound, SoundSource.BLOCKS, volume, pitch, false);
    }

    private static float[] rgb(int colour) {
        return new float[]{FastColor.ARGB32.red(colour) / 255F, FastColor.ARGB32.green(colour) / 255F, FastColor.ARGB32.blue(colour) / 255F};
    }
}
