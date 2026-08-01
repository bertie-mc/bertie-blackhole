package com.berlord.bertieblackhole;

import com.berlord.bertieblackhole.command.BbhCommand;
import com.berlord.bertieblackhole.config.BbhConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(BertieBlackHole.MOD_ID)
public class BertieBlackHole {

    public static final String MOD_ID = "bertie_blackhole";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BertieBlackHole(IEventBus ignored) {
        NeoForge.EVENT_BUS.addListener(BertieBlackHole::onServerStarting);
        NeoForge.EVENT_BUS.addListener(BertieBlackHole::onRegisterCommands);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        BbhConfig.reload();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        BbhCommand.register(event.getDispatcher());
    }
}
