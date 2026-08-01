package com.berlord.bertieblackhole;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackHoleStateTest {

    @Test
    void roundTripsPersistentMachineState() {
        BlackHoleState state = new BlackHoleState();
        state.load(fullState(2, 11, "stella", 37, 19));

        CompoundTag saved = new CompoundTag();
        state.save(saved);

        BlackHoleState restored = new BlackHoleState();
        restored.load(saved);
        assertEquals(2, restored.level());
        assertEquals(11, restored.outputTimer());
        assertEquals("stella", restored.pendingExchange());
        assertEquals(37, restored.counters().get("matter"));
        assertEquals(19, restored.buffers().get("stella"));
    }

    @Test
    void clientLevelSyncDoesNotEraseServerOnlyState() {
        BlackHoleState state = new BlackHoleState();
        state.load(fullState(1, 8, "stella", 12, 9));

        CompoundTag update = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        payload.putInt("level", 3);
        update.put(BlackHoleState.NBT_KEY, payload);
        state.load(update);

        assertEquals(3, state.level());
        assertEquals(8, state.outputTimer());
        assertEquals("stella", state.pendingExchange());
        assertEquals(12, state.counters().get("matter"));
        assertEquals(9, state.buffers().get("stella"));
    }

    private static CompoundTag fullState(int level, int timer, String pending, int counter, int buffer) {
        CompoundTag root = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        payload.putInt("level", level);
        payload.putInt("outputTimer", timer);
        payload.putString("pending", pending);
        CompoundTag counters = new CompoundTag();
        counters.putInt("matter", counter);
        payload.put("counters", counters);
        CompoundTag buffers = new CompoundTag();
        buffers.putInt("stella", buffer);
        payload.put("buffers", buffers);
        root.put(BlackHoleState.NBT_KEY, payload);
        return root;
    }
}
