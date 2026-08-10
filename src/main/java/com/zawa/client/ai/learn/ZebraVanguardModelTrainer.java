package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraBitBoard;
import com.zawa.client.ai.zebra.ZebraVanguardPatternModel;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.SplittableRandom;

final class ZebraVanguardModelTrainer {
    private static final int VERIFICATION_POSITIONS = 20_000;

    private ZebraVanguardModelTrainer() {
    }

    static void train(ZebraVanguardTrainingConfig config) throws IOException {
        ZebraTeacherPatternModel teacher = ZebraTeacherPatternModel.load(config.teacher());
        write(config.output(), teacher);
        verify(config.output(), teacher);
        ZebraVanguardResidualTrainer.train(config, teacher.stages());
        System.out.printf("Vanguard lossless model written: %s bytes=%d weights=%d verified=%d%n",
                config.output().toAbsolutePath(), Files.size(config.output()), teacher.weights().length,
                VERIFICATION_POSITIONS);
    }

    private static void write(Path path, ZebraTeacherPatternModel teacher) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(ZebraVanguardPatternModel.MAGIC);
            output.writeInt(ZebraVanguardPatternModel.VERSION);
            output.writeInt(teacher.scale());
            output.writeInt(teacher.stages());
            output.writeInt(teacher.features());
            output.writeInt(teacher.families().length);
            output.writeInt(teacher.stageTableSize());
            output.writeInt(teacher.weights().length);
            for (ZebraTeacherPatternModel.Family family : teacher.families()) {
                writeName(output, family.name());
                output.writeInt(family.offset());
                output.writeInt(family.tableSize());
                output.writeInt(family.placements().length);
                output.writeInt(family.placements()[0].length);
                for (int[] placement : family.placements()) {
                    for (int square : placement) {
                        output.writeByte(square);
                    }
                }
            }
            for (int tableIndex = 0; tableIndex < teacher.stageTableSize(); tableIndex++) {
                int previous = teacher.weights()[tableIndex];
                output.writeShort(previous);
                for (int stage = 1; stage < teacher.stages(); stage++) {
                    int current = teacher.weights()[stage * teacher.stageTableSize() + tableIndex];
                    writeSignedVariableInteger(output, current - previous);
                    previous = current;
                }
            }
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void verify(Path path, ZebraTeacherPatternModel teacher) throws IOException {
        ZebraVanguardPatternModel model = ZebraVanguardPatternModel.load(path);
        SplittableRandom random = new SplittableRandom(0x5A56414E47554152L);
        long black = ZebraBitBoard.INITIAL_BLACK;
        long white = ZebraBitBoard.INITIAL_WHITE;
        int turn = 1;
        int verified = 0;
        while (verified < VERIFICATION_POSITIONS) {
            long player = turn == 1 ? black : white;
            long opponent = turn == 1 ? white : black;
            if (teacher.score(player, opponent) != model.evaluate(player, opponent)) {
                throw new IOException("lossless model verification failed at position " + verified);
            }
            verified++;
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                turn = -turn;
                player = turn == 1 ? black : white;
                opponent = turn == 1 ? white : black;
                legal = ZebraBitBoard.legalMoves(player, opponent);
                if (legal == 0L) {
                    black = ZebraBitBoard.INITIAL_BLACK;
                    white = ZebraBitBoard.INITIAL_WHITE;
                    turn = 1;
                    continue;
                }
            }
            int move = randomMove(legal, random);
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            player |= flipped | (1L << move);
            opponent ^= flipped;
            if (turn == 1) {
                black = player;
                white = opponent;
            } else {
                white = player;
                black = opponent;
            }
            turn = -turn;
        }
    }

    private static int randomMove(long legal, SplittableRandom random) {
        int selected = random.nextInt(Long.bitCount(legal));
        while (selected-- > 0) {
            legal &= legal - 1L;
        }
        return Long.numberOfTrailingZeros(legal);
    }

    private static void writeSignedVariableInteger(DataOutputStream output, int value) throws IOException {
        int encoded = (value << 1) ^ (value >> 31);
        while ((encoded & ~0x7F) != 0) {
            output.writeByte((encoded & 0x7F) | 0x80);
            encoded >>>= 7;
        }
        output.writeByte(encoded);
    }

    private static void writeName(DataOutputStream output, String name) throws IOException {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }
}
