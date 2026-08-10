package com.zawa.client.ai.learn;

import com.zawa.client.ai.zebra.ZebraBitBoard;
import com.zawa.client.ai.zebra.ZebraOpeningPolicy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ZebraWthorBookTrainer {
    private ZebraWthorBookTrainer() {
    }

    static void train(ZebraWthorTrainingConfig config) throws IOException {
        Map<PositionKey, MoveCounts> positions = new HashMap<>();
        Counter counter = new Counter();
        for (Path input : inputs(config.input())) {
            String name = input.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".zip")) {
                readZip(input, config.maximumPly(), positions, counter);
            } else if (name.endsWith(".wtb")) {
                try (InputStream stream = new BufferedInputStream(Files.newInputStream(input))) {
                    readWthor(stream, config.maximumPly(), positions, counter);
                }
            }
        }
        List<PolicyEntry> entries = select(positions, config.minimumVisits(), config.minimumShare());
        write(config.output(), entries);
        System.out.printf("WTHOR expert policy written: %s files=%d games=%d positions=%d entries=%d%n",
                config.output().toAbsolutePath(), counter.files, counter.games, positions.size(), entries.size());
    }

    private static List<Path> inputs(Path input) throws IOException {
        if (Files.isRegularFile(input)) {
            return List.of(input);
        }
        if (!Files.isDirectory(input)) {
            throw new IOException("WTHOR input does not exist: " + input);
        }
        try (var files = Files.walk(input)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".zip") || name.endsWith(".wtb");
                    })
                    .sorted()
                    .toList();
        }
    }

    private static void readZip(Path path, int maximumPly, Map<PositionKey, MoveCounts> positions,
                                Counter counter) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".wtb")) {
                    readWthor(zip, maximumPly, positions, counter);
                }
                zip.closeEntry();
            }
        }
    }

    private static void readWthor(InputStream input, int maximumPly, Map<PositionKey, MoveCounts> positions,
                                  Counter counter) throws IOException {
        byte[] header = readExactly(input, 16);
        int games = littleEndianInt(header, 4);
        int boardSize = header[12] & 0xFF;
        if (games < 0 || games > 10_000_000 || (boardSize != 0 && boardSize != 8)) {
            throw new IOException("invalid WTHOR header");
        }
        counter.files++;
        byte[] record = new byte[68];
        for (int game = 0; game < games; game++) {
            readFully(input, record);
            addGame(record, maximumPly, positions);
            counter.games++;
        }
    }

    private static void addGame(byte[] record, int maximumPly, Map<PositionKey, MoveCounts> positions) {
        long black = ZebraBitBoard.INITIAL_BLACK;
        long white = ZebraBitBoard.INITIAL_WHITE;
        boolean blackToMove = true;
        for (int ply = 0; ply < 60 && ply < maximumPly; ply++) {
            int encoded = record[8 + ply] & 0xFF;
            if (encoded == 0) {
                break;
            }
            int row = encoded / 10 - 1;
            int column = encoded % 10 - 1;
            if (row < 0 || row >= 8 || column < 0 || column >= 8) {
                return;
            }
            long player = blackToMove ? black : white;
            long opponent = blackToMove ? white : black;
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                blackToMove = !blackToMove;
                player = blackToMove ? black : white;
                opponent = blackToMove ? white : black;
                legal = ZebraBitBoard.legalMoves(player, opponent);
                if (legal == 0L) {
                    return;
                }
            }
            int move = row * 8 + column;
            if ((legal & (1L << move)) == 0L) {
                return;
            }
            ZebraOpeningPolicy.Canonical canonical = ZebraOpeningPolicy.canonical(player, opponent);
            int canonicalMove = ZebraBitBoard.transformSquare(move, canonical.transform());
            positions.computeIfAbsent(new PositionKey(canonical.player(), canonical.opponent()), ignored -> new MoveCounts())
                    .add(canonicalMove);
            long flipped = ZebraBitBoard.flips(player, opponent, move);
            player |= flipped | (1L << move);
            opponent ^= flipped;
            if (blackToMove) {
                black = player;
                white = opponent;
            } else {
                white = player;
                black = opponent;
            }
            blackToMove = !blackToMove;
        }
    }

    private static List<PolicyEntry> select(Map<PositionKey, MoveCounts> positions, int minimumVisits,
                                            double minimumShare) {
        List<PolicyEntry> result = new ArrayList<>();
        for (Map.Entry<PositionKey, MoveCounts> entry : positions.entrySet()) {
            MoveCounts counts = entry.getValue();
            int bestMove = counts.bestMove();
            int bestVisits = counts.moves[bestMove];
            if (counts.total >= minimumVisits && bestVisits >= minimumVisits / 2
                    && bestVisits >= counts.total * minimumShare) {
                result.add(new PolicyEntry(entry.getKey().player(), entry.getKey().opponent(), bestMove));
            }
        }
        result.sort(Comparator.comparing(PolicyEntry::player, Long::compareUnsigned)
                .thenComparing(PolicyEntry::opponent, Long::compareUnsigned));
        return result;
    }

    private static void write(Path path, List<PolicyEntry> entries) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        try (DataOutputStream output = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary)))) {
            output.writeInt(ZebraOpeningPolicy.MAGIC);
            output.writeInt(ZebraOpeningPolicy.VERSION);
            output.writeInt(entries.size());
            for (PolicyEntry entry : entries) {
                output.writeLong(entry.player());
                output.writeLong(entry.opponent());
                output.writeByte(entry.move());
            }
        }
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        readFully(input, bytes);
        return bytes;
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int offset = 0;
        while (offset < bytes.length) {
            int count = input.read(bytes, offset, bytes.length - offset);
            if (count < 0) {
                throw new EOFException("truncated WTHOR data");
            }
            offset += count;
        }
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16) | ((bytes[offset + 3] & 0xFF) << 24);
    }

    private static final class MoveCounts {
        private final int[] moves = new int[64];
        private int total;

        private void add(int move) {
            moves[move]++;
            total++;
        }

        private int bestMove() {
            int best = 0;
            for (int move = 1; move < moves.length; move++) {
                if (moves[move] > moves[best]) {
                    best = move;
                }
            }
            return best;
        }
    }

    private static final class Counter {
        private int files;
        private int games;
    }

    private record PositionKey(long player, long opponent) {
    }

    private record PolicyEntry(long player, long opponent, int move) {
    }
}
