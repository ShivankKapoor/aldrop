package com.shivankkapoor.aldrop.Cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitTest {

    @Test
    void runsImmediatelyWhenNoTransactionIsActive() {
        AtomicInteger runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void waitsForTheCommitInsideATransaction() {
        AtomicInteger runs = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(runs::incrementAndGet);
            assertThat(runs).hasValue(0);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            assertThat(runs).hasValue(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void neverRunsWhenTheTransactionRollsBack() {
        AtomicInteger runs = new AtomicInteger();

        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(runs::incrementAndGet);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(runs).hasValue(0);
    }
}
