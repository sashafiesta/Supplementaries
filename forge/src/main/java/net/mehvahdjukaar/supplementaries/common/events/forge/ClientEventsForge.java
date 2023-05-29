package net.mehvahdjukaar.supplementaries.common.events.forge;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.FrameBufferBackedDynamicTexture;
import net.mehvahdjukaar.moonlight.api.client.texture_renderer.RenderedTexturesManager;
import net.mehvahdjukaar.moonlight.api.map.client.MapDecorationClientManager;
import net.mehvahdjukaar.moonlight.api.resources.textures.SpriteUtils;
import net.mehvahdjukaar.supplementaries.Supplementaries;
import net.mehvahdjukaar.supplementaries.client.QuiverArrowSelectGui;
import net.mehvahdjukaar.supplementaries.client.renderers.CapturedMobCache;
import net.mehvahdjukaar.supplementaries.client.renderers.entities.layers.QuiverLayer;
import net.mehvahdjukaar.supplementaries.client.renderers.forge.QuiverArrowSelectGuiImpl;
import net.mehvahdjukaar.supplementaries.common.block.blocks.EndermanSkullBlock;
import net.mehvahdjukaar.supplementaries.common.block.blocks.SugarBlock;
import net.mehvahdjukaar.supplementaries.common.events.ClientEvents;
import net.mehvahdjukaar.supplementaries.configs.ClientConfigs;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.reg.ClientRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public class ClientEventsForge {

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientEventsForge.class);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEventsForge::onAddLayers);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEventsForge::onAddGuiLayers);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ClientEventsForge::onRegisterSkullModels);
    }

    public static void onRegisterSkullModels(EntityRenderersEvent.CreateSkullModels event) {
        event.registerSkullModel(EndermanSkullBlock.TYPE,
                new SkullModel(event.getEntityModelSet().bakeLayer(ModelLayers.SKELETON_SKULL)));
        SkullBlockRenderer.SKIN_BY_TYPE.put(EndermanSkullBlock.TYPE,
                Supplementaries.res("textures/entity/enderman_head.png"));
    }


    @SuppressWarnings("unchecked")
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinType : event.getSkins()) {
            var renderer = event.getSkin(skinType);
            if (renderer != null) {
                renderer.addLayer(new QuiverLayer(renderer, false));
            }
        }
        var renderer = event.getRenderer(EntityType.SKELETON);
        if (renderer != null) {
            renderer.addLayer(new QuiverLayer(renderer, true));
        }
        var renderer2 = event.getRenderer(EntityType.STRAY);
        if (renderer2 != null) {
            renderer2.addLayer(new QuiverLayer(renderer2, true));
        }
    }

    public static void onAddGuiLayers(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "quiver_overlay",
                new QuiverArrowSelectGuiImpl());
    }

    @SubscribeEvent
    public static void itemTooltip(ItemTooltipEvent event) {
        if (event.getEntity() != null) {
            ClientEvents.onItemTooltip(event.getItemStack(), event.getFlags(), event.getToolTip());
        }
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        if (CompatHandler.CONFIGURED) {
            ClientEvents.addConfigButton(event.getScreen(), event.getListenersList(), event::addListener);
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientEvents.onClientTick(Minecraft.getInstance());

        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        if (QuiverArrowSelectGui.isActive() && QuiverArrowSelectGui.onMouseScrolled(event.getScrollDelta())) {
            event.setCanceled(true);
        }
    }

    //forge only below

    //TODO: add to fabric

    private static double wobble; // from 0 to 1

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        Player p = Minecraft.getInstance().player;
        if (p != null && !Minecraft.getInstance().isPaused()) {
            boolean isOnRope = ClientEvents.isIsOnRope();
            if (isOnRope || wobble != 0) {
                double period = ClientConfigs.Blocks.ROPE_WOBBLE_PERIOD.get();
                double newWobble = (((p.tickCount + event.getPartialTick()) / period) % 1);
                if (!isOnRope && newWobble < wobble) {
                    wobble = 0;
                } else {
                    wobble = newWobble;
                }
                event.setRoll((float) (event.getRoll() + Mth.sin((float) (wobble * 2 * Math.PI)) * ClientConfigs.Blocks.ROPE_WOBBLE_AMPLITUDE.get()));
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (Minecraft.getInstance().screen == null &&
                event.getKey() == ClientRegistry.QUIVER_KEYBIND.getKey().getValue()) {
            int a = event.getAction();
            if (a < 2) {
                QuiverArrowSelectGui.setUsingKeybind(a == 1);
            }
        }
    }


    @SubscribeEvent
    public static void onPlayerDeath(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof DeathScreen && event.getCurrentScreen() instanceof ChatScreen cs
                && ClientConfigs.Tweaks.DEATH_CHAT.get()) {
            cs.charTyped((char) GLFW.GLFW_KEY_MINUS, 0);
            cs.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
    }

}
