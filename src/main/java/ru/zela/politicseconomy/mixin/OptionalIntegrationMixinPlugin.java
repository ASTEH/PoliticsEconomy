package ru.zela.politicseconomy.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps optional integration mixins disabled when the target mod is absent.
 */
public final class OptionalIntegrationMixinPlugin implements IMixinConfigPlugin {
    private static final String MTS_WRAPPER_WORLD = "mcinterface1211.WrapperWorld";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(
        String targetClassName,
        String mixinClassName
    ) {
        if (mixinClassName.endsWith("MtsMillenaireCombatMixin")) {
            return isPresent(MTS_WRAPPER_WORLD);
        }
        return true;
    }

    @Override
    public void acceptTargets(
        Set<String> myTargets,
        Set<String> otherTargets
    ) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(
                className,
                false,
                OptionalIntegrationMixinPlugin.class.getClassLoader()
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
