# ProjectZebra

This is the repository for OthellogicCraft.

## ZebraTCL-PVS

`ZebraTCL-PVS` is the time-safe tournament AI. It combines a phase-aware n-tuple value model, PVS, conservative Multi-ProbCut, a generation-aware transposition table, parity-region endgame ordering, and exact endgame search.

Run its resumable self-play training with:

```shell
./gradlew classes
java -cp build/classes/java/main:build/resources/main com.zawa.client.ai.learn.OthelloTrainer
```

The default model path is `zebra_tcl_weights.bin`. A packaged model is used when no external model exists. The production hard limit defaults to 2350 ms and can be changed with `-Dprojectzebra.ai.timeMillis=2200`.

The design, evidence, training options, and verification procedure are documented in [docs/zebra-tcl-pvs.md](docs/zebra-tcl-pvs.md).

## ZebraVanguard-MPC

`ZebraVanguard-MPC` is the strongest registered AI. It combines a lossless 25.5-million-position pattern teacher, PVS, Multi-ProbCut, late-move reductions, a 72,554-game WTHOR opening policy, a two-way set-associative transposition table, opponent-time resolution from 24 empties, win/loss-first endgame solving from 22 empties, and a hard deadline with a server margin. The packaged runtime uses Java 17 and has no external library dependency.

Build the executable JAR with:

```shell
./gradlew clean test jar
java -jar build/libs/ProjectZebra-1.0-SNAPSHOT.jar
```

The model conversion, optional search-distillation training, benchmark evidence, and research basis are documented in [docs/zebra-vanguard-mpc.md](docs/zebra-vanguard-mpc.md).
