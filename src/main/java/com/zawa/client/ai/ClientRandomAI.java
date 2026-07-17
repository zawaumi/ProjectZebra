package com.zawa.client.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ClientRandomAI extends ClientAbstractAi {

    public ClientRandomAI(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientRandomAI() {
        super(0, new Integer[8][8]);
        this.ai_name = "RandomAI";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        List<Integer[]> validMoves = new ArrayList<>();
        for (int i = 0; i < othello_array.length; i++) {
            for (int j = 0; j < othello_array[i].length; j++) {
                if (isValidMove(othello_array, myturn, i, j)) {
                    validMoves.add(new Integer[]{i, j});
                }
            }
        }
        if (validMoves.isEmpty()) {
            return null;
        }
        int randomIndex = new Random().nextInt(validMoves.size());
        return validMoves.get(randomIndex);
    }
}