package com.zawa.client.ai.zebra;

import com.zawa.client.ai.AbstractClientAi;
import com.zawa.client.ai.ClientTurnObserver;

public final class ClientZebraVanguardMpcAI extends AbstractClientAi implements ClientTurnObserver {
    public static final String NAME = "ZebraVanguard-MPC";
    private static final long DEFAULT_HARD_LIMIT_MILLISECONDS = 2_350L;
    private static final ZebraVanguardPatternModel SHARED_MODEL = ZebraVanguardPatternModel.loadDefault();

    private final ZebraSearchEngine searchEngine;
    private final ZebraPonderEngine ponderEngine;
    private final long hardLimitMilliseconds;

    public ClientZebraVanguardMpcAI(Integer myTurn, Integer[][] board) {
        this(myTurn, board, configuredLimit(), SHARED_MODEL);
    }

    public ClientZebraVanguardMpcAI() {
        this(0, new Integer[8][8], configuredLimit(), SHARED_MODEL);
    }

    public ClientZebraVanguardMpcAI(long hardLimitMilliseconds) {
        this(0, new Integer[8][8], hardLimitMilliseconds, SHARED_MODEL);
    }

    ClientZebraVanguardMpcAI(Integer myTurn, Integer[][] board, long hardLimitMilliseconds,
                            ZebraPositionEvaluator evaluator) {
        super(myTurn, board);
        this.ai_name = NAME;
        this.hardLimitMilliseconds = Math.max(20L, hardLimitMilliseconds);
        this.searchEngine = new ZebraSearchEngine(evaluator, 21, ZebraSearchProfile.vanguard());
        this.ponderEngine = evaluator == SHARED_MODEL ? new ZebraPonderEngine(SHARED_MODEL) : null;
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] board, Integer turn) {
        long[] bitBoards = ZebraBitBoard.fromArray(board, turn);
        long player = bitBoards[0];
        long opponent = bitBoards[1];
        long legal = ZebraBitBoard.legalMoves(player, opponent);
        if (legal == 0L) {
            return null;
        }
        int move = ZebraPerfectLineBook.find(player, opponent);
        if (move < 0 || (legal & (1L << move)) == 0L) {
            ZebraSearchResult pondered = ponderEngine == null ? null : ponderEngine.stopAndFind(player, opponent);
            if (pondered != null && (legal & (1L << pondered.move())) != 0L) {
                searchEngine.hintRootMove(pondered.move());
            }
            long reserve = Math.max(100L, hardLimitMilliseconds / 16L);
            ZebraSearchResult result = searchEngine.findBestMove(player, opponent,
                    Math.max(10L, hardLimitMilliseconds - reserve), hardLimitMilliseconds);
            if (pondered != null && pondered.outcomeSolved() && !result.outcomeSolved()) {
                move = pondered.move();
            } else if (pondered != null && pondered.completedDepth() > result.completedDepth()
                    && !result.outcomeSolved()) {
                move = pondered.move();
            } else {
                move = result.move();
            }
            if (Boolean.parseBoolean(System.getProperty("projectzebra.ai.searchLog", "true"))) {
                System.out.printf("%s depth=%d score=%d nodes=%d time=%dms exact=%s wld=%s%n", NAME,
                        result.completedDepth(), result.score(), result.nodes(), result.elapsedMilliseconds(),
                        result.exact(), result.outcomeSolved());
            }
        }
        if (move < 0 || (legal & (1L << move)) == 0L) {
            move = Long.numberOfTrailingZeros(legal);
        }
        return new Integer[]{move / 8, move % 8};
    }

    @Override
    public void turnObserved(Integer[][] board, int myTurn, int currentTurn) {
        if (ponderEngine == null) {
            return;
        }
        if (currentTurn == myTurn) {
            return;
        }
        long[] bitBoards = ZebraBitBoard.fromArray(board, currentTurn);
        ponderEngine.start(bitBoards[0], bitBoards[1]);
    }

    @Override
    public void gameFinished() {
        if (ponderEngine != null) {
            ponderEngine.stop();
        }
    }

    private static long configuredLimit() {
        return Long.getLong("projectzebra.ai.timeMillis", DEFAULT_HARD_LIMIT_MILLISECONDS);
    }
}
