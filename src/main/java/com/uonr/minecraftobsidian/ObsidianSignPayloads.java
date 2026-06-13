package com.uonr.minecraftobsidian;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class ObsidianSignPayloads {
    private static final int MAX_URL_LENGTH = 4096;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MAX_SNAPSHOT_SIZE = 65536;

    private ObsidianSignPayloads() {
    }

    record BindSign(ResourceLocation dimension, BlockPos pos, String url) implements CustomPacketPayload {
        static final Type<BindSign> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "bind_sign"));
        static final StreamCodec<RegistryFriendlyByteBuf, BindSign> STREAM_CODEC = StreamCodec.ofMember(BindSign::write, BindSign::read);

        private static BindSign read(RegistryFriendlyByteBuf buf) {
            return new BindSign(buf.readResourceLocation(), buf.readBlockPos(), buf.readUtf(MAX_URL_LENGTH));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
            buf.writeUtf(url, MAX_URL_LENGTH);
        }

        @Override
        public Type<BindSign> type() {
            return TYPE;
        }
    }

    record RemoveSign(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
        static final Type<RemoveSign> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "remove_sign"));
        static final StreamCodec<RegistryFriendlyByteBuf, RemoveSign> STREAM_CODEC = StreamCodec.ofMember(RemoveSign::write, RemoveSign::read);

        private static RemoveSign read(RegistryFriendlyByteBuf buf) {
            return new RemoveSign(buf.readResourceLocation(), buf.readBlockPos());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
        }

        @Override
        public Type<RemoveSign> type() {
            return TYPE;
        }
    }

    record OpenSign(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
        static final Type<OpenSign> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "open_sign"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenSign> STREAM_CODEC = StreamCodec.ofMember(OpenSign::write, OpenSign::read);

        private static OpenSign read(RegistryFriendlyByteBuf buf) {
            return new OpenSign(buf.readResourceLocation(), buf.readBlockPos());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
        }

        @Override
        public Type<OpenSign> type() {
            return TYPE;
        }
    }

    record OpenSignResponse(ResourceLocation dimension, BlockPos pos, boolean found, String url) implements CustomPacketPayload {
        static final Type<OpenSignResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "open_sign_response"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenSignResponse> STREAM_CODEC = StreamCodec.ofMember(OpenSignResponse::write, OpenSignResponse::read);

        private static OpenSignResponse read(RegistryFriendlyByteBuf buf) {
            return new OpenSignResponse(buf.readResourceLocation(), buf.readBlockPos(), buf.readBoolean(), buf.readUtf(MAX_URL_LENGTH));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
            buf.writeBoolean(found);
            buf.writeUtf(url, MAX_URL_LENGTH);
        }

        @Override
        public Type<OpenSignResponse> type() {
            return TYPE;
        }
    }

    record OperationResult(boolean success, String message) implements CustomPacketPayload {
        static final Type<OperationResult> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "operation_result"));
        static final StreamCodec<RegistryFriendlyByteBuf, OperationResult> STREAM_CODEC = StreamCodec.ofMember(OperationResult::write, OperationResult::read);

        private static OperationResult read(RegistryFriendlyByteBuf buf) {
            return new OperationResult(buf.readBoolean(), buf.readUtf(MAX_MESSAGE_LENGTH));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(success);
            buf.writeUtf(message, MAX_MESSAGE_LENGTH);
        }

        @Override
        public Type<OperationResult> type() {
            return TYPE;
        }
    }

    record RequestLinkedSigns(ResourceLocation dimension) implements CustomPacketPayload {
        static final Type<RequestLinkedSigns> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "request_linked_signs"));
        static final StreamCodec<RegistryFriendlyByteBuf, RequestLinkedSigns> STREAM_CODEC = StreamCodec.ofMember(RequestLinkedSigns::write, RequestLinkedSigns::read);

        private static RequestLinkedSigns read(RegistryFriendlyByteBuf buf) {
            return new RequestLinkedSigns(buf.readResourceLocation());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
        }

        @Override
        public Type<RequestLinkedSigns> type() {
            return TYPE;
        }
    }

    record LinkedSignsSnapshot(ResourceLocation dimension, List<BlockPos> positions) implements CustomPacketPayload {
        static final Type<LinkedSignsSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "linked_signs_snapshot"));
        static final StreamCodec<RegistryFriendlyByteBuf, LinkedSignsSnapshot> STREAM_CODEC = StreamCodec.ofMember(LinkedSignsSnapshot::write, LinkedSignsSnapshot::read);

        private static LinkedSignsSnapshot read(RegistryFriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_SNAPSHOT_SIZE) {
                throw new IllegalArgumentException("Invalid linked sign snapshot size: " + size);
            }

            java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                positions.add(buf.readBlockPos());
            }
            return new LinkedSignsSnapshot(dimension, List.copyOf(positions));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            if (positions.size() > MAX_SNAPSHOT_SIZE) {
                throw new IllegalArgumentException("Linked sign snapshot is too large: " + positions.size());
            }
            buf.writeResourceLocation(dimension);
            buf.writeVarInt(positions.size());
            positions.forEach(buf::writeBlockPos);
        }

        @Override
        public Type<LinkedSignsSnapshot> type() {
            return TYPE;
        }
    }

    record LinkedSignUpdate(ResourceLocation dimension, BlockPos pos, boolean linked) implements CustomPacketPayload {
        static final Type<LinkedSignUpdate> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModConstants.MODID, "linked_sign_update"));
        static final StreamCodec<RegistryFriendlyByteBuf, LinkedSignUpdate> STREAM_CODEC = StreamCodec.ofMember(LinkedSignUpdate::write, LinkedSignUpdate::read);

        private static LinkedSignUpdate read(RegistryFriendlyByteBuf buf) {
            return new LinkedSignUpdate(buf.readResourceLocation(), buf.readBlockPos(), buf.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeResourceLocation(dimension);
            buf.writeBlockPos(pos);
            buf.writeBoolean(linked);
        }

        @Override
        public Type<LinkedSignUpdate> type() {
            return TYPE;
        }
    }
}
