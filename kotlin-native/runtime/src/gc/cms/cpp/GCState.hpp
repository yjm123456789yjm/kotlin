/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <condition_variable>
#include <mutex>
#include <atomic>

class GCStateHolder {
public:
    int64_t schedule() {
        std::unique_lock lock(mutex_);
        if (scheduledEpoch <= startedEpoch) {
            scheduledEpoch = startedEpoch + 1;
            cond_.notify_all();
        }
        return scheduledEpoch;
    }

    void shutdown() {
        std::unique_lock lock(mutex_);
        scheduledEpoch = std::numeric_limits<int64_t>::max();
        cond_.notify_all();
    }

    void start(int64_t epoch) {
        std::unique_lock lock(mutex_);
        startedEpoch = epoch;
        cond_.notify_all();
    }

    void finish(int64_t epoch) {
        std::unique_lock lock(mutex_);
        finishedEpoch = epoch;
        cond_.notify_all();
    }

    void finalized(int64_t epoch) {
        std::unique_lock lock(mutex_);
        finalizedEpoch = epoch;
        cond_.notify_all();
    }

    void waitEpochFinished(int64_t epoch) {
        std::unique_lock lock(mutex_);
        cond_.wait(lock, [this, epoch] { return finishedEpoch >= epoch; });
    }

    void waitEpochFinalized(int64_t epoch) {
        std::unique_lock lock(mutex_);
        cond_.wait(lock, [this, epoch] { return finalizedEpoch >= epoch; });
    }

    int64_t waitCurrentFinished() {
        std::unique_lock lock(mutex_);
        int64_t epoch = startedEpoch;
        cond_.wait(lock, [this, epoch] { return finishedEpoch >= epoch; });
        return epoch;
    }

    int64_t waitScheduled() {
        std::unique_lock lock(mutex_);
        cond_.wait(lock, [this] { return scheduledEpoch > finishedEpoch; });
        return scheduledEpoch;
    }

    int64_t waitFinalizersRequired() {
        std::unique_lock lock(mutex_);
        cond_.wait(lock, [this] { return finishedEpoch > finalizedEpoch; });
        return finishedEpoch;
    }


private:
    std::mutex mutex_;
    std::condition_variable cond_;
    int64_t startedEpoch = 0;
    int64_t finishedEpoch = 0;
    int64_t scheduledEpoch = 0;
    int64_t finalizedEpoch = 0;
};