package com.uonr.minecraftobsidian;

import com.mojang.serialization.Codec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ModConstants.MODID);

    static final DeferredHolder<AttachmentType<?>, AttachmentType<String>> SIGN_URL =
            ATTACHMENTS.register("sign_url", () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING, url -> !url.isBlank())
                    .build());

    private ModAttachments() {
    }

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
