package com.berlord.bertieblackhole.test;

import com.berlord.bertieblackhole.StatefulBlackHole;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Mod(value = BertieBlackHoleClientTestMod.MOD_ID, dist = Dist.CLIENT)
public final class BertieBlackHoleClientTestMod {
    static final String MOD_ID = "bertieblackholetest";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BertieBlackHoleClientTestMod(IEventBus modBus) {
        modBus.addListener(this::onLoadComplete);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            try {
                Class<?> blockEntity = Class.forName(
                        "com.stal111.forbidden_arcanus.common.block.entity.BlackHoleBlockEntity");
                assertMethods(blockEntity, "bbh$onReachCentre", "bbh$serverTick", "bbh$save", "bbh$load");
                if (!StatefulBlackHole.class.isAssignableFrom(blockEntity)) {
                    throw new IllegalStateException("F&A black hole does not implement StatefulBlackHole");
                }

                assertMethods(Class.forName("com.stal111.forbidden_arcanus.common.block.BlackHoleBlock"),
                        "bbh$recolour");
                assertMethods(Class.forName("com.stal111.forbidden_arcanus.client.renderer.block.BlackHoleRenderer"),
                        "bbh$tintAura");
                LOGGER.info("BERTIE_BLACKHOLE_MIXINS_OK");
            } catch (ClassNotFoundException failure) {
                throw new IllegalStateException("Forbidden & Arcanus black hole classes are unavailable", failure);
            }
        });
    }

    private static void assertMethods(Class<?> target, String... fragments) {
        Set<String> methods = Arrays.stream(target.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
        for (String fragment : fragments) {
            if (methods.stream().noneMatch(name -> name.contains(fragment))) {
                throw new IllegalStateException(target.getName() + " is missing " + fragment);
            }
        }
    }
}
