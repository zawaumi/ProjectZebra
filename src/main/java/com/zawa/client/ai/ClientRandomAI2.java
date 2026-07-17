package com.zawa.client.ai;

public class ClientRandomAI2 extends ClientAbstractAi {

    public ClientRandomAI2(Integer myturn, Integer[][] othello_array) {
        super(myturn, othello_array);
    }

    public ClientRandomAI2() {
        super(0, new Integer[8][8]);
        this.ai_name = "RandomAI2";
    }

    @Override
    public Integer[] estimateNextPut(Integer[][] othello_array, Integer myturn) {
        java.util.List<Integer[]> validMoves = new java.util.ArrayList<>();
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
        int randomIndex = new java.util.Random().nextInt(validMoves.size());
        return validMoves.get(randomIndex);
    }
}