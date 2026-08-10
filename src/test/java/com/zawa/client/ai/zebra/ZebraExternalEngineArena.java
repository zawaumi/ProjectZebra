package com.zawa.client.ai.zebra;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

public final class ZebraExternalEngineArena {
    private ZebraExternalEngineArena() {
    }

    public static void main(String[] arguments) throws Exception {
        Options options = Options.parse(arguments);
        ExternalEngine opponent = ExternalEngine.create(options.opponentClass());
        ZebraPositionEvaluator model = options.zebraEvaluator();
        ZebraSearchProfile searchProfile = options.searchProfile();
        List<Position> openings = generateOpenings(options);
        long warmupMillis = Math.max(5L, Math.min(25L, options.timeMillis()));
        play(openings.get(0), true, warmupMillis, model, searchProfile, options.ponder(), opponent);
        play(openings.get(0), false, warmupMillis, model, searchProfile, options.ponder(), opponent);
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int discDifference = 0;
        long maximumZebraMillis = 0L;
        long maximumOpponentMillis = 0L;
        long zebraDepth = 0L;
        long opponentDepth = 0L;
        long zebraMoves = 0L;
        long opponentMoves = 0L;
        for (int pair = 0; pair < openings.size(); pair++) {
            for (int color = 0; color < 2; color++) {
                GameResult result = play(openings.get(pair), color == 0, options.timeMillis(), model,
                        searchProfile, options.ponder(), opponent);
                if (result.outcome() > 0) {
                    wins++;
                } else if (result.outcome() < 0) {
                    losses++;
                } else {
                    draws++;
                }
                discDifference += result.discDifference();
                maximumZebraMillis = Math.max(maximumZebraMillis, result.maximumZebraMillis());
                maximumOpponentMillis = Math.max(maximumOpponentMillis, result.maximumOpponentMillis());
                zebraDepth += result.zebraDepth();
                opponentDepth += result.opponentDepth();
                zebraMoves += result.zebraMoves();
                opponentMoves += result.opponentMoves();
            }
            if ((pair + 1) % 5 == 0 || pair + 1 == openings.size()) {
                System.out.printf("pairs=%d/%d W-D-L=%d-%d-%d discs=%+d%n", pair + 1, openings.size(),
                        wins, draws, losses, discDifference);
            }
        }
        double points = wins + draws * 0.5;
        System.out.printf("RESULT search=%s ponder=%s games=%d W-D-L=%d-%d-%d score=%.1f%% discs=%+d maxZebra=%dms maxOpponent=%dms depth=%.2f/%.2f%n",
                options.searchMode(), options.ponder(), options.games(), wins, draws, losses,
                points * 100.0 / options.games(), discDifference, maximumZebraMillis, maximumOpponentMillis,
                zebraDepth / (double) zebraMoves, opponentDepth / (double) opponentMoves);
    }

    private static GameResult play(Position opening, boolean zebraBlack, long timeMillis,
                                   ZebraPositionEvaluator model, ZebraSearchProfile searchProfile,
                                   boolean pondering, ExternalEngine opponent) throws Exception {
        long black = opening.black();
        long white = opening.white();
        boolean blackToMove = opening.blackToMove();
        ZebraSearchEngine zebra = new ZebraSearchEngine(model, 21, searchProfile);
        ZebraPonderEngine ponder = pondering && model instanceof ZebraVanguardPatternModel vanguard
                ? new ZebraPonderEngine(vanguard) : null;
        opponent.newGame();
        long maximumZebraMillis = 0L;
        long maximumOpponentMillis = 0L;
        long zebraDepth = 0L;
        long opponentDepth = 0L;
        int zebraMoves = 0;
        int opponentMoves = 0;
        int passes = 0;
        while (passes < 2) {
            long player = blackToMove ? black : white;
            long other = blackToMove ? white : black;
            long legal = ZebraBitBoard.legalMoves(player, other);
            if (legal == 0L) {
                passes++;
                blackToMove = !blackToMove;
                continue;
            }
            passes = 0;
            boolean zebraTurn = blackToMove == zebraBlack;
            int move;
            if (zebraTurn) {
                long started = System.nanoTime();
                ZebraSearchResult pondered = ponder == null ? null : ponder.stopAndFind(player, other);
                if (pondered != null && (legal & (1L << pondered.move())) != 0L) {
                    zebra.hintRootMove(pondered.move());
                }
                move = ZebraPerfectLineBook.find(player, other);
                if (move < 0 || (legal & (1L << move)) == 0L) {
                    long safety = Math.max(2L, timeMillis / 16L);
                    ZebraSearchResult result = zebra.findBestMove(player, other,
                            Math.max(1L, timeMillis - safety), timeMillis);
                    if (pondered != null && pondered.completedDepth() > result.completedDepth()
                            && !result.outcomeSolved()) {
                        move = pondered.move();
                    } else {
                        move = result.move();
                    }
                    zebraDepth += result.completedDepth();
                }
                zebraMoves++;
                maximumZebraMillis = Math.max(maximumZebraMillis,
                        (System.nanoTime() - started) / 1_000_000L);
            } else {
                if (ponder != null) {
                    ponder.start(player, other);
                }
                ExternalResult result = opponent.think(player, other, timeMillis);
                move = result.move();
                maximumOpponentMillis = Math.max(maximumOpponentMillis, result.milliseconds());
                opponentDepth += result.depth();
                opponentMoves++;
            }
            if (move < 0 || (legal & (1L << move)) == 0L) {
                throw new IllegalStateException("Engine returned illegal move " + move);
            }
            long flipped = ZebraBitBoard.flips(player, other, move);
            player |= flipped | (1L << move);
            other ^= flipped;
            if (blackToMove) {
                black = player;
                white = other;
            } else {
                white = player;
                black = other;
            }
            blackToMove = !blackToMove;
        }
        int blackDifference = Long.bitCount(black) - Long.bitCount(white);
        if (ponder != null) {
            ponder.stop();
        }
        int zebraDifference = zebraBlack ? blackDifference : -blackDifference;
        return new GameResult(Integer.compare(zebraDifference, 0), zebraDifference,
                maximumZebraMillis, maximumOpponentMillis, zebraDepth, opponentDepth, zebraMoves, opponentMoves);
    }

    private static List<Position> generateOpenings(Options options) {
        SplittableRandom random = new SplittableRandom(options.seed());
        List<Position> result = new ArrayList<>(options.games() / 2);
        while (result.size() < options.games() / 2) {
            long black = ZebraBitBoard.INITIAL_BLACK;
            long white = ZebraBitBoard.INITIAL_WHITE;
            boolean blackToMove = true;
            boolean valid = true;
            for (int ply = 0; ply < options.openingPlies(); ply++) {
                long player = blackToMove ? black : white;
                long other = blackToMove ? white : black;
                long legal = ZebraBitBoard.legalMoves(player, other);
                if (legal == 0L) {
                    blackToMove = !blackToMove;
                    player = blackToMove ? black : white;
                    other = blackToMove ? white : black;
                    legal = ZebraBitBoard.legalMoves(player, other);
                    if (legal == 0L) {
                        valid = false;
                        break;
                    }
                }
                int move = randomMove(legal, random);
                long flipped = ZebraBitBoard.flips(player, other, move);
                player |= flipped | (1L << move);
                other ^= flipped;
                if (blackToMove) {
                    black = player;
                    white = other;
                } else {
                    white = player;
                    black = other;
                }
                blackToMove = !blackToMove;
            }
            if (valid) {
                result.add(new Position(black, white, blackToMove));
            }
        }
        return result;
    }

    private static int randomMove(long legal, SplittableRandom random) {
        int selected = random.nextInt(Long.bitCount(legal));
        while (selected-- > 0) {
            legal &= legal - 1L;
        }
        return Long.numberOfTrailingZeros(legal);
    }

    private record Position(long black, long white, boolean blackToMove) {
    }

    private record GameResult(int outcome, int discDifference, long maximumZebraMillis,
                              long maximumOpponentMillis, long zebraDepth, long opponentDepth,
                              int zebraMoves, int opponentMoves) {
    }

    private record ExternalResult(int move, long milliseconds, int depth) {
    }

    private record Options(int games, long timeMillis, int openingPlies, long seed, String opponentClass,
                           String zebraModel, String searchMode, boolean ponder) {
        private static Options parse(String[] arguments) {
            int games = 20;
            long timeMillis = 25L;
            int openingPlies = 8;
            long seed = 20260722L;
            String opponentClass = "othello.AI";
            String zebraModel = "vanguard";
            String searchMode = "production";
            boolean ponder = true;
            for (String argument : arguments) {
                if (argument.startsWith("--games=")) {
                    games = Integer.parseInt(argument.substring("--games=".length()));
                } else if (argument.startsWith("--time=")) {
                    timeMillis = Long.parseLong(argument.substring("--time=".length()));
                } else if (argument.startsWith("--openings=")) {
                    openingPlies = Integer.parseInt(argument.substring("--openings=".length()));
                } else if (argument.startsWith("--seed=")) {
                    seed = Long.parseLong(argument.substring("--seed=".length()));
                } else if (argument.startsWith("--opponent=")) {
                    opponentClass = argument.substring("--opponent=".length());
                } else if (argument.startsWith("--zebra=")) {
                    zebraModel = argument.substring("--zebra=".length());
                } else if (argument.startsWith("--search=")) {
                    searchMode = argument.substring("--search=".length());
                } else if (argument.startsWith("--ponder=")) {
                    ponder = Boolean.parseBoolean(argument.substring("--ponder=".length()));
                } else {
                    throw new IllegalArgumentException("Unknown arena argument: " + argument);
                }
            }
            if (games <= 0 || (games & 1) != 0 || timeMillis <= 0L || openingPlies < 0 || openingPlies > 50) {
                throw new IllegalArgumentException("games must be positive and even; time must be positive; openings must be 0..50");
            }
            if (!"legacy".equals(zebraModel) && !"vanguard".equals(zebraModel)
                    && !"vanguard-distilled".equals(zebraModel)
                    && !"teacher".equals(zebraModel)) {
                throw new IllegalArgumentException("zebra must be legacy, vanguard, vanguard-distilled, or teacher");
            }
            if (!"control".equals(searchMode) && !"production".equals(searchMode)) {
                throw new IllegalArgumentException("search must be control or production");
            }
            return new Options(games, timeMillis, openingPlies, seed, opponentClass, zebraModel,
                    searchMode, ponder);
        }

        private ZebraSearchProfile searchProfile() {
            return "control".equals(searchMode) ? ZebraSearchProfile.vanguardControl()
                    : ZebraSearchProfile.vanguard();
        }

        private ZebraPositionEvaluator zebraEvaluator() throws Exception {
            return switch (zebraModel) {
                case "vanguard" -> ZebraVanguardPatternModel.loadDefault();
                case "vanguard-distilled" -> ZebraVanguardEvaluator.loadDefault();
                case "teacher" -> ExternalPatternEvaluator.create();
                default -> ZebraPatternModel.loadDefault();
            };
        }
    }

    private static final class ExternalPatternEvaluator implements ZebraPositionEvaluator {
        private final MethodHandle evaluate;

        private ExternalPatternEvaluator(MethodHandle evaluate) {
            this.evaluate = evaluate;
        }

        private static ExternalPatternEvaluator create() throws Exception {
            Class<?> patternClass = Class.forName("othello.PatternEval");
            MethodHandle method = MethodHandles.publicLookup().findStatic(patternClass, "evaluateV2",
                    MethodType.methodType(int.class, long.class, long.class));
            return new ExternalPatternEvaluator(method);
        }

        @Override
        public int evaluate(long player, long opponent, long playerMoves) {
            try {
                return (int) evaluate.invokeExact(player, opponent);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        }
    }

    private static final class ExternalEngine {
        private final Object engine;
        private final Method newGame;
        private final Method think;
        private final Field resultMove;
        private final Field resultMillis;
        private final Field resultDepth;

        private ExternalEngine(Object engine, Method newGame, Method think, Field resultMove, Field resultMillis,
                               Field resultDepth) {
            this.engine = engine;
            this.newGame = newGame;
            this.think = think;
            this.resultMove = resultMove;
            this.resultMillis = resultMillis;
            this.resultDepth = resultDepth;
        }

        private static ExternalEngine create(String className) throws Exception {
            Class<?> engineClass = Class.forName(className);
            Object engine = engineClass.getConstructor().newInstance();
            engineClass.getField("evalMode").set(engine, "pattern2");
            engineClass.getField("endgameEmpties").setInt(engine, 20);
            Method think = engineClass.getMethod("think", long.class, long.class, long.class);
            Class<?> resultClass = think.getReturnType();
            return new ExternalEngine(engine, engineClass.getMethod("newGame"), think,
                    resultClass.getField("move"), resultClass.getField("millis"), resultClass.getField("depth"));
        }

        private void newGame() throws Exception {
            newGame.invoke(engine);
        }

        private ExternalResult think(long player, long opponent, long timeMillis) throws Exception {
            Object result = think.invoke(engine, player, opponent, timeMillis);
            return new ExternalResult(resultMove.getInt(result), resultMillis.getLong(result),
                    resultDepth.getInt(result));
        }
    }
}
