package com.zawa.client.ai.learn;

import com.zawa.client.ai.AbstractClientAi;
import com.zawa.client.util.table.BitBoardUtils;
import com.zawa.client.util.table.BoardConverter;
import com.zawa.client.util.ai.OthelloSearcher;
import com.zawa.client.util.TimeManager;
import com.zawa.client.util.table.TranspositionTable;

public class ClientLearnedHeuristicAI extends AbstractClientAi {

    private static final int TIME_LIMIT_MILLISECONDS = 2500;

    private final TranspositionTable transpositionTable = new TranspositionTable();
    private final TimeManager timeManager = new TimeManager();
    private final OthelloSearcher othelloSearcher = new OthelloSearcher(transpositionTable, timeManager);

    public ClientLearnedHeuristicAI(Integer myTurn, Integer[][] othelloArray) {
        super(myTurn, othelloArray);
        this.ai_name = "LearnedHeuristicAI";
    }

    public ClientLearnedHeuristicAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "LearnedHeuristicAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othelloArray, Integer myTurn) {
        long[] bitBoards = BoardConverter.convertToBitBoards(othelloArray, myTurn);
        long myBoard = bitBoards[0];
        long opponentBoard = bitBoards[1];

        long legalMoves = BitBoardUtils.getLegalMoves(myBoard, opponentBoard);
        if (legalMoves == 0L) {
            return null;
        }

        int emptyCount = 64 - Long.bitCount(myBoard | opponentBoard);
        transpositionTable.clear();
        timeManager.start(TIME_LIMIT_MILLISECONDS);

        int bestMoveIndex = othelloSearcher.searchBestMove(myBoard, opponentBoard, legalMoves, emptyCount);

        return new Integer[]{bestMoveIndex / 8, bestMoveIndex % 8};
    }
}