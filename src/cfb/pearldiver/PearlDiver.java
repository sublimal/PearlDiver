package cfb.pearldiver;

/**
 * (c) 2016 Come-from-Beyond
 */
public class PearlDiver {

    public static final int TRANSACTION_LENGTH = 8019;

    private static final int CURL_HASH_LENGTH = 243;
    private static final int CURL_STATE_LENGTH = CURL_HASH_LENGTH * 3;

    private static final int RUNNING = 0;
    private static final int CANCELLED = 1;
    private static final int COMPLETED = 2;

    private volatile int state;

    public synchronized void cancel() {

        state = CANCELLED;

        notifyAll();
    }

    public synchronized boolean search(final int[] transactionTrits, final int minWeightMagnitude, int numberOfThreads) {

        if (transactionTrits.length != TRANSACTION_LENGTH) {

            throw new RuntimeException("Invalid transaction trits length: " + transactionTrits.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > CURL_HASH_LENGTH) {

            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }

        state = RUNNING;

        final long[] midCurlStateLow = new long[CURL_STATE_LENGTH], midCurlStateHigh = new long[CURL_STATE_LENGTH];

        {
            for (int i = CURL_HASH_LENGTH; i < CURL_STATE_LENGTH; i++) {

                midCurlStateLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                midCurlStateHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
            }

            int offset = 0;
            final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH], curlScratchpadHigh = new long[CURL_STATE_LENGTH];
            for (int i = (TRANSACTION_LENGTH - CURL_HASH_LENGTH) / CURL_HASH_LENGTH; i-- > 0; ) {

                for (int j = 0; j < CURL_HASH_LENGTH; j++) {

                    switch (transactionTrits[offset++]) {

                        case 0: {

                            midCurlStateLow[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                            midCurlStateHigh[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                        } break;

                        case 1: {

                            midCurlStateLow[j] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                            midCurlStateHigh[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                        } break;

                        default: {

                            midCurlStateLow[j] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                            midCurlStateHigh[j] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                        }
                    }
                }

                transform(midCurlStateLow, midCurlStateHigh, curlScratchpadLow, curlScratchpadHigh);
            }

            for (int i = 0; i < 162; i++) {

                switch (transactionTrits[offset++]) {

                    case 0: {

                        midCurlStateLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                        midCurlStateHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                    } break;

                    case 1: {

                        midCurlStateLow[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                        midCurlStateHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                    } break;

                    default: {

                        midCurlStateLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                        midCurlStateHigh[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                    }
                }
            }

            midCurlStateLow[162 + 0] = 0b1101101101101101101101101101101101101101101101101101101101101101L;
            midCurlStateHigh[162 + 0] = 0b1011011011011011011011011011011011011011011011011011011011011011L;
            midCurlStateLow[162 + 1] = 0b1111000111111000111111000111111000111111000111111000111111000111L;
            midCurlStateHigh[162 + 1] = 0b1000111111000111111000111111000111111000111111000111111000111111L;
            midCurlStateLow[162 + 2] = 0b0111111111111111111000000000111111111111111111000000000111111111L;
            midCurlStateHigh[162 + 2] = 0b1111111111000000000111111111111111111000000000111111111111111111L;
            midCurlStateLow[162 + 3] = 0b1111111111000000000000000000000000000111111111111111111111111111L;
            midCurlStateHigh[162 + 3] = 0b0000000000111111111111111111111111111111111111111111111111111111L;
        }

        if (numberOfThreads <= 0) {

            numberOfThreads = Runtime.getRuntime().availableProcessors() - 1;
            if (numberOfThreads < 1) {

                numberOfThreads = 1;
            }
        }

        while (numberOfThreads-- > 0) {

            final int threadIndex = numberOfThreads;
            (new Thread(() -> {

                final long[] midCurlStateCopyLow = new long[CURL_STATE_LENGTH], midCurlStateCopyHigh = new long[CURL_STATE_LENGTH];
                System.arraycopy(midCurlStateLow, 0, midCurlStateCopyLow, 0, CURL_STATE_LENGTH);
                System.arraycopy(midCurlStateHigh, 0, midCurlStateCopyHigh, 0, CURL_STATE_LENGTH);
                for (int i = threadIndex; i-- > 0; ) {

                    increment(midCurlStateCopyLow, midCurlStateCopyHigh, 162 + CURL_HASH_LENGTH / 9, 162 + (CURL_HASH_LENGTH / 9) * 2);
                }

                final long[] curlStateLow = new long[CURL_STATE_LENGTH], curlStateHigh = new long[CURL_STATE_LENGTH];
                final long[] curlScratchpadLow = new long[CURL_STATE_LENGTH], curlScratchpadHigh = new long[CURL_STATE_LENGTH];
                while (state == RUNNING) {

                    increment(midCurlStateCopyLow, midCurlStateCopyHigh, 162 + (CURL_HASH_LENGTH / 9) * 2, CURL_HASH_LENGTH);
                    System.arraycopy(midCurlStateCopyLow, 0, curlStateLow, 0, CURL_STATE_LENGTH);
                    System.arraycopy(midCurlStateCopyHigh, 0, curlStateHigh, 0, CURL_STATE_LENGTH);
                    transform(curlStateLow, curlStateHigh, curlScratchpadLow, curlScratchpadHigh);

                NEXT_BIT_INDEX:
                    for (int bitIndex = 64; bitIndex-- > 0; ) {

                        for (int i = minWeightMagnitude; i-- > 0; ) {

                            if ((((int)(curlStateLow[CURL_HASH_LENGTH - 1 - i] >> bitIndex)) & 1) != (((int)(curlStateHigh[CURL_HASH_LENGTH - 1 - i] >> bitIndex)) & 1)) {

                                continue NEXT_BIT_INDEX;
                            }
                        }

                        synchronized (this) {

                            if (state == RUNNING) {

                                for (int i = 0; i < CURL_HASH_LENGTH; i++) {

                                    transactionTrits[TRANSACTION_LENGTH - CURL_HASH_LENGTH + i] = ((((int) (midCurlStateCopyLow[i] >> bitIndex)) & 1) == 0) ? 1 : (((((int) (midCurlStateCopyHigh[i] >> bitIndex)) & 1) == 0) ? -1 : 0);
                                }

                                state = COMPLETED;

                                notifyAll();
                            }
                        }

                        break;
                    }
                }

            })).start();
        }

        try {

            while (state == RUNNING) {

                wait();
            }

        } catch (final InterruptedException e) {

            state = CANCELLED;
        }

        return state == COMPLETED;
    }

    private static void transform(final long[] curlStateLow, final long[] curlStateHigh, final long[] curlScratchpadLow, final long[] curlScratchpadHigh) {

        int curlScratchpadIndex = 0;
        for (int round = 27; round-- > 0; ) {

            System.arraycopy(curlStateLow, 0, curlScratchpadLow, 0, CURL_STATE_LENGTH);
            System.arraycopy(curlStateHigh, 0, curlScratchpadHigh, 0, CURL_STATE_LENGTH);

            for (int curlStateIndex = 0; curlStateIndex < CURL_STATE_LENGTH; curlStateIndex++) {

                final long alpha = curlScratchpadLow[curlScratchpadIndex];
                final long beta = curlScratchpadHigh[curlScratchpadIndex];
                final long gamma = curlScratchpadHigh[curlScratchpadIndex += (curlScratchpadIndex < 365 ? 364 : -365)];
                final long delta = (alpha | (~gamma)) & (curlScratchpadLow[curlScratchpadIndex] ^ beta);

                curlStateLow[curlStateIndex] = ~delta;
                curlStateHigh[curlStateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    private static void increment(final long[] midCurlStateCopyLow, final long[] midCurlStateCopyHigh, final int fromIndex, final int toIndex) {

        for (int i = fromIndex; i < toIndex; i++) {

            if (midCurlStateCopyLow[i] == 0b0000000000000000000000000000000000000000000000000000000000000000L) {

                midCurlStateCopyLow[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;
                midCurlStateCopyHigh[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;

            } else {

                if (midCurlStateCopyHigh[i] == 0b0000000000000000000000000000000000000000000000000000000000000000L) {

                    midCurlStateCopyHigh[i] = 0b1111111111111111111111111111111111111111111111111111111111111111L;

                } else {

                    midCurlStateCopyLow[i] = 0b0000000000000000000000000000000000000000000000000000000000000000L;
                }

                break;
            }
        }
    }
}
