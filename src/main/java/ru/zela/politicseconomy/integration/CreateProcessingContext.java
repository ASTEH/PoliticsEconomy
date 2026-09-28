package ru.zela.politicseconomy.integration;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-lived thread-local context used while Create applies one Basin recipe.
 *
 * The context stores the result of the simulation pass so the real pass can use
 * exactly the same scaled fluid outputs. Nothing in the recipe itself is mutated.
 */
public final class CreateProcessingContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private CreateProcessingContext() {}

    public static void begin(ServerLevel level, net.minecraft.core.BlockPos pos) {
        CURRENT.set(new Context(level, pos));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Context current() {
        return CURRENT.get();
    }

    public static final class Context {
        private final ServerLevel level;
        private final net.minecraft.core.BlockPos machinePos;
        private final Map<String, Double> pendingFluidRemainders = new LinkedHashMap<>();
        private List<FluidStack> plannedFluidOutputs = new ArrayList<>();

        public Context(ServerLevel level, net.minecraft.core.BlockPos machinePos) {
            this.level = level;
            this.machinePos = machinePos;
        }

        public ServerLevel level() { return level; }
        public net.minecraft.core.BlockPos machinePos() { return machinePos; }
        public Map<String, Double> pendingFluidRemainders() { return pendingFluidRemainders; }

        public List<FluidStack> plannedFluidOutputs() {
            return plannedFluidOutputs;
        }

        public void setPlannedFluidOutputs(List<FluidStack> outputs) {
            this.plannedFluidOutputs = outputs;
        }
    }
}
