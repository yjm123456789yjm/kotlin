/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <condition_variable>
#include <mutex>
#include <atomic>

enum class GCState {
    kNone,
    kNeedsGC,
    kWorldIsStopped,
    kGCRunning,
    kShutdown,
};


class GCStateHolder {
public:
    explicit GCStateHolder(std::atomic<bool>& gNeedSafepointSlowpath): gNeedSafepointSlowpath_(gNeedSafepointSlowpath) {}
    GCState get() { return state_.load(); }
    bool compareAndSwap(GCState &oldState, GCState newState) {
        std::unique_lock<std::mutex> lock(mutex_);
        if (state_.compare_exchange_strong(oldState, newState)) {
            cond_.notify_all();
            gNeedSafepointSlowpath_ = recalcNeedSlowPath(newState);
            return true;
        }
        return false;
    }
    bool compareAndSet(GCState oldState, GCState newState) {
        return compareAndSwap(oldState, newState);
    }

    template<class WaitF>
    GCState waitUntil(WaitF fun) {
        return waitUntil(std::move(fun), []{});
    }
    template<class WaitF, class AfterF>
    GCState waitUntil(WaitF fun, AfterF after) {
        std::unique_lock<std::mutex> lock(mutex_);
        cond_.wait(lock, std::move(fun));
        after();
        return state_.load();
    }

private:
    static bool recalcNeedSlowPath(GCState state) {
        return state == GCState::kWorldIsStopped;
    }

    std::atomic<GCState> state_ = GCState::kNone;
    std::mutex mutex_;
    std::condition_variable cond_;
    std::atomic<bool>& gNeedSafepointSlowpath_;
};