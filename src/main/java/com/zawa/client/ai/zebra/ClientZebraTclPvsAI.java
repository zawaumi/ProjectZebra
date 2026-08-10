package com.zawa.client.ai.zebra;

import com.zawa.client.ai.AbstractClientAi;

public final class ClientZebraTclPvsAI extends AbstractClientAi {
    public static final String NAME = "ZebraTCL-PVS";
    private static final long DEFAULT_HARD_LIMIT_MILLISECONDS = 2_350L;
    private static final ZebraPatternModel SHARED_MODEL = ZebraPatternModel.loadDefault();

    private final ZebraSearchEngine searchEngine;
    private final long hardLimitMilliseconds;

    public ClientZebraTclPvsAI(Integer myTurn, Integer[][] board) {
        this(myTurn, board, configuredLimit(), SHARED_MODEL);
    }

    public ClientZebraTclPvsAI() {
        this(0, new Integer[8][8], configuredLimit(), SHARED_MODEL);
    }

    public ClientZebraTclPvsAI(long hardLimitMilliseconds) {
        this(0, new Integer[8][8], hardLimitMilliseconds, SHARED_MODEL);
    }

    ClientZebraTclPvsAI(Integer myTurn, Integer[][] board, long hardLimitMilliseconds, ZebraPatternModel model) {
        super(myTurn, board);
        this.ai_name = NAME;
        this.hardLimitMilliseconds = Math.max(20L, hardLimitMilliseconds);
        this.searchEngine = new ZebraSearchEngine(model);
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
        int bookMove = ZebraPerfectLineBook.find(player, opponent);
        if (bookMove >= 0 && (legal & (1L << bookMove)) != 0L) {
            return new Integer[]{bookMove / 8, bookMove % 8};
        }
        long softLimit = Math.max(10L, hardLimitMilliseconds - Math.max(100L, hardLimitMilliseconds / 16L));
        ZebraSearchResult result = searchEngine.findBestMove(player, opponent, softLimit, hardLimitMilliseconds);
        int move = result.move();
        if (move < 0 || (legal & (1L << move)) == 0L) {
            move = Long.numberOfTrailingZeros(legal);
        }
        if (Boolean.getBoolean("projectzebra.ai.searchLog")) {
                System.out.printf("%s depth=%d score=%d nodes=%d time=%dms exact=%s wld=%s%n", NAME,
                    result.completedDepth(), result.score(), result.nodes(), result.elapsedMilliseconds(),
                    result.exact(), result.outcomeSolved());
        }
        return new Integer[]{move / 8, move % 8};
    }

    private static long configuredLimit() {
        return Long.getLong("projectzebra.ai.timeMillis", DEFAULT_HARD_LIMIT_MILLISECONDS);
    }
}
