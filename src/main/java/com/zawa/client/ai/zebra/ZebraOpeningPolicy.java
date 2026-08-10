package com.zawa.client.ai.zebra;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class ZebraOpeningPolicy {
    public static final String DEFAULT_FILE_NAME = "zebra_wthor_policy.bin";
    public static final int MAGIC = 0x5A574850;
    public static final int VERSION = 1;

    private static final ZebraOpeningPolicy DEFAULT = loadBundled();

    private final Map<PositionKey, Integer> moves;

    private ZebraOpeningPolicy(Map<PositionKey, Integer> moves) {
        this.moves = moves;
    }

    public static ZebraOpeningPolicy defaultPolicy() {
        return DEFAULT;
    }

    public int find(long player, long opponent) {
        Canonical canonical = canonical(player, opponent);
        Integer canonicalMove = moves.get(new PositionKey(canonical.player(), canonical.opponent()));
        if (canonicalMove == null) {
            return -1;
        }
        return ZebraBitBoard.transformSquare(canonicalMove, inverseTransform(canonical.transform()));
    }

    public int size() {
        return moves.size();
    }

    public static Canonical canonical(long player, long opponent) {
        long bestPlayer = player;
        long bestOpponent = opponent;
        int bestTransform = 0;
        for (int transform = 1; transform < 8; transform++) {
            long transformedPlayer = ZebraBitBoard.transform(player, transform);
            long transformedOpponent = ZebraBitBoard.transform(opponent, transform);
            int playerOrder = Long.compareUnsigned(transformedPlayer, bestPlayer);
            if (playerOrder < 0 || (playerOrder == 0
                    && Long.compareUnsigned(transformedOpponent, bestOpponent) < 0)) {
                bestPlayer = transformedPlayer;
                bestOpponent = transformedOpponent;
                bestTransform = transform;
            }
        }
        return new Canonical(bestPlayer, bestOpponent, bestTransform);
    }

    private static ZebraOpeningPolicy loadBundled() {
        try (InputStream resource = ZebraOpeningPolicy.class.getResourceAsStream("/" + DEFAULT_FILE_NAME)) {
            return resource == null ? new ZebraOpeningPolicy(Map.of()) : read(resource);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError("Cannot load WTHOR policy: " + exception.getMessage());
        }
    }

    private static ZebraOpeningPolicy read(InputStream raw) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(raw))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported WTHOR policy header");
            }
            int entries = input.readInt();
            if (entries < 0 || entries > 2_000_000) {
                throw new IOException("invalid WTHOR policy entry count");
            }
            Map<PositionKey, Integer> moves = new HashMap<>(Math.max(16, entries * 4 / 3));
            for (int index = 0; index < entries; index++) {
                long player = input.readLong();
                long opponent = input.readLong();
                int move = input.readUnsignedByte();
                if (move >= 64 || (ZebraBitBoard.legalMoves(player, opponent) & (1L << move)) == 0L) {
                    throw new IOException("invalid WTHOR policy move");
                }
                moves.put(new PositionKey(player, opponent), move);
            }
            if (input.read() != -1) {
                throw new IOException("trailing WTHOR policy data");
            }
            return new ZebraOpeningPolicy(Map.copyOf(moves));
        } catch (EOFException exception) {
            throw new IOException("truncated WTHOR policy", exception);
        }
    }

    private static int inverseTransform(int transform) {
        return switch (transform) {
            case 1 -> 3;
            case 3 -> 1;
            default -> transform;
        };
    }

    public record Canonical(long player, long opponent, int transform) {
    }

    private record PositionKey(long player, long opponent) {
    }
}
