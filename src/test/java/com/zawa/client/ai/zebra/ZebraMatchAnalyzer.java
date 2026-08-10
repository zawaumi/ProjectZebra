package com.zawa.client.ai.zebra;

import java.util.ArrayList;
import java.util.List;

public final class ZebraMatchAnalyzer {
    private ZebraMatchAnalyzer() {
    }

    public static void main(String[] arguments) {
        if (arguments.length < 1 || arguments.length > 3 || (arguments[0].length() & 1) != 0) {
            throw new IllegalArgumentException("an even-length transcript and optional exact ply count are required");
        }
        List<Position> positions = replay(arguments[0]);
        ZebraVanguardPatternModel model = ZebraVanguardPatternModel.loadDefault();
        ZebraSearchEngine engine = new ZebraSearchEngine(model, 22, ZebraSearchProfile.vanguard());
        int exactPlies = arguments.length >= 2 ? Integer.parseInt(arguments[1]) : 20;
        int firstExactPly = Math.max(0, positions.size() - exactPlies);
        Position timedPosition = positions.get(firstExactPly);
        boolean ponderTimed = arguments.length == 3 && "--ponder-timed".equals(arguments[2]);
        ZebraSearchProfile timedProfile = ponderTimed ? ZebraSearchProfile.ponder()
                : ZebraSearchProfile.vanguard();
        long timedLimit = ponderTimed ? 60_000L : 2_350L;
        ZebraSearchResult timed = new ZebraSearchEngine(model, ponderTimed ? 18 : 22, timedProfile)
                .findBestMove(timedPosition.player(), timedPosition.opponent(),
                        ponderTimed ? 60_000L : 2_200L, timedLimit);
        System.out.printf("TIMED ply=%d player=%016X opponent=%016X move=%s score=%s depth=%d exact=%s wld=%s nodes=%d time=%dms%n",
                firstExactPly + 1, timedPosition.player(), timedPosition.opponent(),
                notation(timed.move()), score(timed.score()), timed.completedDepth(),
                timed.exact(), timed.outcomeSolved(), timed.nodes(), timed.elapsedMilliseconds());
        if (arguments.length == 3 && ("--timed-only".equals(arguments[2]) || ponderTimed)) {
            return;
        }
        for (int ply = firstExactPly; ply < positions.size(); ply++) {
            Position position = positions.get(ply);
            int empties = 64 - Long.bitCount(position.player() | position.opponent());
            int playedMove = move(arguments[0], ply);
            ZebraSearchResult best = engine.findBestMoveAtDepth(position.player(), position.opponent(), empties);
            int playedScore = scorePlayedMove(engine, position, playedMove, empties);
            System.out.printf("ply=%d side=%s empties=%d played=%s playedScore=%s best=%s bestScore=%s nodes=%d time=%dms%n",
                    ply + 1, position.blackToMove() ? "black" : "white", empties,
                    notation(playedMove), score(playedScore), notation(best.move()), score(best.score()),
                    best.nodes(), best.elapsedMilliseconds());
        }
    }

    private static int scorePlayedMove(ZebraSearchEngine engine, Position position, int move, int empties) {
        long flipped = ZebraBitBoard.flips(position.player(), position.opponent(), move);
        long nextPlayer = position.opponent() ^ flipped;
        long nextOpponent = position.player() | flipped | (1L << move);
        long nextLegal = ZebraBitBoard.legalMoves(nextPlayer, nextOpponent);
        if (nextLegal != 0L) {
            return -engine.findBestMoveAtDepth(nextPlayer, nextOpponent, empties - 1).score();
        }
        long samePlayerLegal = ZebraBitBoard.legalMoves(nextOpponent, nextPlayer);
        if (samePlayerLegal != 0L) {
            return engine.findBestMoveAtDepth(nextOpponent, nextPlayer, empties - 1).score();
        }
        int difference = Long.bitCount(nextOpponent) - Long.bitCount(nextPlayer);
        return difference > 0 ? 30_000 + difference : difference < 0 ? -30_000 + difference : 0;
    }

    private static List<Position> replay(String transcript) {
        long black = ZebraBitBoard.INITIAL_WHITE;
        long white = ZebraBitBoard.INITIAL_BLACK;
        boolean blackToMove = true;
        List<Position> result = new ArrayList<>(transcript.length() / 2);
        for (int ply = 0; ply < transcript.length() / 2; ply++) {
            long player = blackToMove ? black : white;
            long opponent = blackToMove ? white : black;
            long legal = ZebraBitBoard.legalMoves(player, opponent);
            if (legal == 0L) {
                blackToMove = !blackToMove;
                player = blackToMove ? black : white;
                opponent = blackToMove ? white : black;
                legal = ZebraBitBoard.legalMoves(player, opponent);
            }
            int move = move(transcript, ply);
            if ((legal & (1L << move)) == 0L) {
                throw new IllegalArgumentException("illegal transcript move " + notation(move) + " at ply " + (ply + 1));
            }
            result.add(new Position(player, opponent, blackToMove));
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
        return result;
    }

    private static int move(String transcript, int ply) {
        int offset = ply * 2;
        int column = Character.toUpperCase(transcript.charAt(offset)) - 'A';
        int row = transcript.charAt(offset + 1) - '1';
        return row * 8 + column;
    }

    private static String notation(int move) {
        return "" + (char) ('A' + (move & 7)) + (char) ('1' + (move >>> 3));
    }

    private static String score(int score) {
        if (score > 30_000) {
            return "+" + (score - 30_000);
        }
        if (score < -30_000) {
            return Integer.toString(score + 30_000);
        }
        return score == 0 ? "draw" : Integer.toString(score);
    }

    private record Position(long player, long opponent, boolean blackToMove) {
    }
}
