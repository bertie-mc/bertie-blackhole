package com.berlord.bertieblackhole.client;

import com.berlord.bertieblackhole.config.LevelDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Recolours the black hole's swirl without changing it.
 *
 * <p>Spawning a different particle type loses the inward flight - that motion belongs to
 * PortalParticle, which lerps itself back to its spawn point over its lifetime. So this still
 * spawns the real portal particle, at F&amp;A's own positions and velocities, and only overrides
 * the colour on the returned instance. PortalParticle sets its colour once in the constructor and
 * never touches it again, so the override sticks for the particle's whole life.
 */
public final class BbhParticles {

    private BbhParticles() {
    }

    public static void spawn(Level level, BlockPos pos, RandomSource random, LevelDef def) {
        int rgb = def.particleColor();
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float jitter = def.shadeJitter();

        for (int i = 0; i < 3; i++) {
            int spreadX = random.nextInt(2) * 2 - 1;
            int spreadZ = random.nextInt(2) * 2 - 1;

            Particle particle = Minecraft.getInstance().particleEngine.createParticle(
                    ParticleTypes.PORTAL,
                    pos.getX() + 0.5D + 0.25D * spreadX,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D + 0.25D * spreadZ,
                    random.nextFloat() * spreadX,
                    (random.nextFloat() - 0.5D) * 0.125D,
                    random.nextFloat() * spreadZ);

            if (particle != null) {
                float shade = 1.0F + (random.nextFloat() * 2.0F - 1.0F) * jitter;
                particle.setColor(clamp(red * shade), clamp(green * shade), clamp(blue * shade));
            }
        }
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
