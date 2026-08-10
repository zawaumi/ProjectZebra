package com.zawa.client.ai.zebra;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ZebraVanguardPatternModel implements ZebraPositionEvaluator {
    public static final String DEFAULT_FILE_NAME = "zebra_vanguard_delta.bin";
    public static final int MAGIC = 0x5A564C44;
    public static final int VERSION = 1;

    private final int stages;
    private final int stageTableSize;
    private final int parityOffset;
    private final Placement[] placements;
    private final short[] weights;

    private ZebraVanguardPatternModel(int stages, int stageTableSize, int parityOffset,
                                     Placement[] placements, short[] weights) {
        this.stages = stages;
        this.stageTableSize = stageTableSize;
        this.parityOffset = parityOffset;
        this.placements = placements;
        this.weights = weights;
    }

    public static ZebraVanguardPatternModel loadDefault() {
        try (InputStream resource = ZebraVanguardPatternModel.class.getResourceAsStream("/" + DEFAULT_FILE_NAME)) {
            if (resource == null) {
                throw new IOException("missing resource " + DEFAULT_FILE_NAME);
            }
            return read(resource);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError("Cannot load ZebraVanguard model: " + exception.getMessage());
        }
    }

    public static ZebraVanguardPatternModel load(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    public int evaluate(long player, long opponent) {
        return evaluate(player, opponent, ZebraBitBoard.legalMoves(player, opponent));
    }

    @Override
    public int evaluate(long player, long opponent, long playerMoves) {
        if ((player & opponent) != 0L) {
            throw new IllegalArgumentException("player and opponent overlap");
        }
        int discs = Long.bitCount(player | opponent);
        int stage = Math.max(0, Math.min(stages - 1, (discs - 13) >> 2));
        int stageOffset = stage * stageTableSize;
        int score = 0;
        for (Placement placement : placements) {
            int pattern = 0;
            int[] squares = placement.squares();
            int[] powers = placement.powers();
            for (int index = 0; index < squares.length; index++) {
                int square = squares[index];
                pattern += (int) (((player >>> square) & 1L) + 2L * ((opponent >>> square) & 1L))
                        * powers[index];
            }
            score += weights[stageOffset + placement.tableOffset() + pattern];
        }
        score += weights[stageOffset + parityOffset + ((64 - discs) & 1)];
        return score;
    }

    @Override
    public boolean requiresLegalMoves() {
        return false;
    }

    private static ZebraVanguardPatternModel read(InputStream raw) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(raw))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("unsupported model header");
            }
            int scale = input.readInt();
            int stages = input.readInt();
            int features = input.readInt();
            int familyCount = input.readInt();
            int stageTableSize = input.readInt();
            int weightCount = input.readInt();
            if (scale <= 0 || stages <= 0 || features <= 0 || familyCount <= 0 || stageTableSize <= 0
                    || (long) stages * stageTableSize != weightCount) {
                throw new IOException("invalid model dimensions");
            }
            List<Placement> placements = new ArrayList<>(features - 1);
            int nextOffset = 0;
            int featureCount = 0;
            int parityOffset = -1;
            for (int familyIndex = 0; familyIndex < familyCount; familyIndex++) {
                String name = readName(input);
                int offset = input.readInt();
                int tableSize = input.readInt();
                int count = input.readInt();
                int squareCount = input.readInt();
                if (offset != nextOffset || tableSize <= 0 || count <= 0 || squareCount < 0 || squareCount > 20) {
                    throw new IOException("invalid family " + name);
                }
                int[][] familyPlacements = new int[count][squareCount];
                for (int placement = 0; placement < count; placement++) {
                    for (int square = 0; square < squareCount; square++) {
                        familyPlacements[placement][square] = input.readUnsignedByte();
                        if (familyPlacements[placement][square] >= 64) {
                            throw new IOException("invalid square in " + name);
                        }
                    }
                }
                boolean parity = "parity".equals(name);
                if (parity && (tableSize != 2 || count != 1 || squareCount != 0)) {
                    throw new IOException("invalid parity family");
                }
                if (parity) {
                    if (parityOffset >= 0) {
                        throw new IOException("duplicate parity family");
                    }
                    parityOffset = offset;
                } else {
                    for (int[] squares : familyPlacements) {
                        placements.add(new Placement(offset, squares));
                    }
                }
                nextOffset += tableSize;
                featureCount += count;
            }
            if (nextOffset != stageTableSize || featureCount != features || parityOffset < 0
                    || placements.size() != features - 1) {
                throw new IOException("incomplete model layout");
            }
            short[] weights = new short[weightCount];
            for (int tableIndex = 0; tableIndex < stageTableSize; tableIndex++) {
                int value = input.readShort();
                weights[tableIndex] = (short) value;
                for (int stage = 1; stage < stages; stage++) {
                    value += readSignedVariableInteger(input);
                    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                        throw new IOException("weight delta overflow");
                    }
                    weights[stage * stageTableSize + tableIndex] = (short) value;
                }
            }
            if (input.read() != -1) {
                throw new IOException("trailing model data");
            }
            return new ZebraVanguardPatternModel(stages, stageTableSize, parityOffset,
                    placements.toArray(new Placement[0]), weights);
        } catch (EOFException exception) {
            throw new IOException("truncated model", exception);
        }
    }

    private static String readName(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length == 0 || length > 64) {
            throw new IOException("invalid family name length");
        }
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int readSignedVariableInteger(DataInputStream input) throws IOException {
        int encoded = 0;
        int shift = 0;
        while (shift < 35) {
            int next = input.readUnsignedByte();
            encoded |= (next & 0x7F) << shift;
            if ((next & 0x80) == 0) {
                return (encoded >>> 1) ^ -(encoded & 1);
            }
            shift += 7;
        }
        throw new IOException("invalid variable integer");
    }

    private record Placement(int tableOffset, int[] squares, int[] powers) {
        private Placement(int tableOffset, int[] squares) {
            this(tableOffset, squares, powers(squares.length));
        }

        private static int[] powers(int length) {
            int[] result = new int[length];
            int power = 1;
            for (int index = 0; index < length; index++) {
                result[index] = power;
                power *= 3;
            }
            return result;
        }
    }
}
