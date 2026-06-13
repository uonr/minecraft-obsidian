package com.uonr.minecraftobsidian;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworking {
    private static final String NETWORK_VERSION = "2";

    private ModNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        registrar.playToServer(ObsidianSignPayloads.BindSign.TYPE, ObsidianSignPayloads.BindSign.STREAM_CODEC, ServerSignPayloadHandler::handleBind);
        registrar.playToServer(ObsidianSignPayloads.RemoveSign.TYPE, ObsidianSignPayloads.RemoveSign.STREAM_CODEC, ServerSignPayloadHandler::handleRemove);
        registrar.playToServer(ObsidianSignPayloads.OpenSign.TYPE, ObsidianSignPayloads.OpenSign.STREAM_CODEC, ServerSignPayloadHandler::handleOpen);
        registrar.playToServer(ObsidianSignPayloads.RequestLinkedSigns.TYPE, ObsidianSignPayloads.RequestLinkedSigns.STREAM_CODEC, ServerSignPayloadHandler::handleRequestLinkedSigns);
        registrar.playToClient(ObsidianSignPayloads.OpenSignResponse.TYPE, ObsidianSignPayloads.OpenSignResponse.STREAM_CODEC, ModNetworking::handleOpenSignResponse);
        registrar.playToClient(ObsidianSignPayloads.OperationResult.TYPE, ObsidianSignPayloads.OperationResult.STREAM_CODEC, ModNetworking::handleOperationResult);
        registrar.playToClient(ObsidianSignPayloads.LinkedSignsSnapshot.TYPE, ObsidianSignPayloads.LinkedSignsSnapshot.STREAM_CODEC, ModNetworking::handleLinkedSignsSnapshot);
        registrar.playToClient(ObsidianSignPayloads.LinkedSignUpdate.TYPE, ObsidianSignPayloads.LinkedSignUpdate.STREAM_CODEC, ModNetworking::handleLinkedSignUpdate);
    }

    private static void handleOpenSignResponse(ObsidianSignPayloads.OpenSignResponse payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        callClientHandler("handleOpenSignResponse", ObsidianSignPayloads.OpenSignResponse.class, payload);
    }

    private static void handleOperationResult(ObsidianSignPayloads.OperationResult payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        callClientHandler("handleOperationResult", ObsidianSignPayloads.OperationResult.class, payload);
    }

    private static void handleLinkedSignsSnapshot(ObsidianSignPayloads.LinkedSignsSnapshot payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        callClientHandler("handleLinkedSignsSnapshot", ObsidianSignPayloads.LinkedSignsSnapshot.class, payload);
    }

    private static void handleLinkedSignUpdate(ObsidianSignPayloads.LinkedSignUpdate payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        callClientHandler("handleLinkedSignUpdate", ObsidianSignPayloads.LinkedSignUpdate.class, payload);
    }

    private static void callClientHandler(String methodName, Class<?> parameterType, Object payload) {
        try {
            Class<?> handler = Class.forName("com.uonr.minecraftobsidian.ClientSignPayloadHandler");
            Method method = handler.getDeclaredMethod(methodName, parameterType);
            method.invoke(null, payload);
        } catch (ClassNotFoundException ignored) {
            MinecraftObsidian.LOGGER.debug("Ignoring client payload on non-client distribution");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            MinecraftObsidian.LOGGER.warn("Could not dispatch client payload {}", methodName, exception);
        }
    }
}
