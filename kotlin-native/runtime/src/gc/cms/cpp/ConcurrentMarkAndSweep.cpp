/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ConcurrentMarkAndSweep.hpp"

#include <cinttypes>

#include "CompilerConstants.hpp"
#include "GlobalData.hpp"
#include "Logging.hpp"
#include "MarkAndSweepUtils.hpp"
#include "Memory.h"
#include "RootSet.hpp"
#include "Runtime.h"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"
#include "ThreadSuspension.hpp"
#include "GCState.hpp"

using namespace kotlin;

namespace {

struct MarkTraits {
    static bool IsMarked(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(object).GCObjectData();
        return objectData.color() == gc::ConcurrentMarkAndSweep::ObjectData::Color::kBlack;
    }

    static bool TryMark(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(object).GCObjectData();
        if (objectData.color() == gc::ConcurrentMarkAndSweep::ObjectData::Color::kBlack) return false;
        objectData.setColor(gc::ConcurrentMarkAndSweep::ObjectData::Color::kBlack);
        return true;
    };
};

struct SweepTraits {
    using ObjectFactory = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>;
    using ExtraObjectsFactory = mm::ExtraObjectDataFactory;

    static bool IsMarkedByExtraObject(mm::ExtraObjectData &object) noexcept {
        auto *baseObject = object.GetBaseObject();
        if (!baseObject->heap()) return true;
        auto& objectData = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(baseObject).GCObjectData();
        return objectData.color() == gc::ConcurrentMarkAndSweep::ObjectData::Color::kBlack;
    }

    static bool TryResetMark(ObjectFactory::NodeRef node) noexcept {
        auto& objectData = node.GCObjectData();
        if (objectData.color() == gc::ConcurrentMarkAndSweep::ObjectData::Color::kWhite) return false;
        objectData.setColor(gc::ConcurrentMarkAndSweep::ObjectData::Color::kWhite);
        return true;
    }
};

// Global, because it's accessed on a hot path: avoid memory load from `this`.
std::atomic<bool> gNeedSafepointSlowpath = false;
} // namespace

ALWAYS_INLINE void gc::ConcurrentMarkAndSweep::ThreadData::SafePointFunctionPrologue() noexcept {
    SafePointRegular(GCSchedulerThreadData::kFunctionPrologueWeight);
}

ALWAYS_INLINE void gc::ConcurrentMarkAndSweep::ThreadData::SafePointLoopBody() noexcept {
    SafePointRegular(GCSchedulerThreadData::kLoopBodyWeight);
}

void gc::ConcurrentMarkAndSweep::ThreadData::SafePointAllocation(size_t size) noexcept {
    threadData_.gcScheduler().OnSafePointAllocation(size);
    if (gNeedSafepointSlowpath.load()) {
        SafePointSlowPath();
    }
}

NO_EXTERNAL_CALLS_CHECK void gc::ConcurrentMarkAndSweep::ThreadData::ScheduleAndWaitFullGC() noexcept {
    auto state = gc_.state_.get();
    while (true) {
        if (state == GCState::kNeedsGC || state == GCState::kWorldIsStopped) {
            break;
        }
        // TODO: what if state now is kShutdown?
        gc_.state_.compareAndSwap(state, GCState::kNeedsGC);
    }
    state = gc_.state_.waitUntil([this] { return gc_.state_.get() != GCState::kNeedsGC; });
    RuntimeAssert(state == GCState::kWorldIsStopped, "I'm not suspended, someone started GC, but no suspension requested?");
    threadData_.suspensionData().suspendIfRequested();
    gc_.state_.waitUntil([this] { return gc_.state_.get() != GCState::kGCRunning; });
    SafePointRegular(0);
}

void gc::ConcurrentMarkAndSweep::ThreadData::WaitFinalizersForTests() noexcept {
    gc_.state_.waitUntil([&] { return gc_.state_.get() == GCState::kNone; });
    while (true) {
        std::unique_lock lock(gc_.finalizerQueueMutex_);
        if (gc_.finalizerQueue_.size() == 0 && gc_.finalizersState_ == FinalizerState::NotRunning) break;
    }
    gc_.StopFinalizerThread();
}

void gc::ConcurrentMarkAndSweep::ThreadData::OnOOM(size_t size) noexcept {
    RuntimeLogDebug({kTagGC}, "Attempt to GC on OOM at size=%zu", size);
    ScheduleAndWaitFullGC();
}

ALWAYS_INLINE void gc::ConcurrentMarkAndSweep::ThreadData::SafePointRegular(size_t weight) noexcept {
    threadData_.gcScheduler().OnSafePointRegular(weight);
    if (gNeedSafepointSlowpath.load()) {
        SafePointSlowPath();
    }
}

NO_EXTERNAL_CALLS_CHECK NO_INLINE void gc::ConcurrentMarkAndSweep::ThreadData::SafePointSlowPath() noexcept {
    auto state = gc_.state_.get();
    if (state == GCState::kNone) {
        return;
    }
    threadData_.suspensionData().suspendIfRequested();
}

gc::ConcurrentMarkAndSweep::ConcurrentMarkAndSweep() noexcept : state_(gNeedSafepointSlowpath) {
    mm::GlobalData::Instance().gcScheduler().SetScheduleGC([this]() NO_EXTERNAL_CALLS_CHECK NO_INLINE {
        RuntimeLogDebug({kTagGC}, "Scheduling GC by thread %d", konan::currentThreadId());
        state_.compareAndSet(GCState::kNone, GCState::kNeedsGC);
    });
    gcThread_ = std::thread([this] {
        while (true) {
            auto state = state_.waitUntil([this] {
                auto state = state_.get();
                return state == GCState::kNeedsGC || state == GCState::kShutdown;
            });
            if (state == GCState::kNeedsGC) {
                PerformFullGC();
            } else if (state == GCState::kShutdown){
                break;
            } else {
                RuntimeFail("GC thread wake up in strange state %d", static_cast<int>(state));
            }
        }
    });
}

void gc::ConcurrentMarkAndSweep::StartFinalizerThreadIfNone() noexcept {
    if (finalizerThread_.joinable()) return;
    finalizerThread_ = std::thread([this] {
        Kotlin_initRuntimeIfNeeded();
        while (true) {
            std::unique_lock lock(finalizerQueueMutex_);
            finalizerQueueCondVar_.wait(lock, [this] {
                return finalizerQueue_.size() > 0 || finalizersState_ == FinalizerState::Shutdown;
            });
            auto finState = finalizersState_.load();
            if (finState == FinalizerState::Shutdown) break;
            finalizersState_.compare_exchange_strong(finState, FinalizerState::Running);
            auto queue = std::move(finalizerQueue_);
            lock.unlock();
            if (queue.size() > 0) {
                ThreadStateGuard guard(ThreadState::kRunnable);
                queue.Finalize();
            }
            finState = FinalizerState::Running;
            finalizersState_.compare_exchange_strong(finState, FinalizerState::NotRunning);
        }
    });
}

void gc::ConcurrentMarkAndSweep::StopFinalizerThread() noexcept {
    if (finalizerThread_.joinable()) {
        {
            std::unique_lock lock(finalizerQueueMutex_);
            finalizersState_ = FinalizerState::Shutdown;
            finalizerQueueCondVar_.notify_all();
        }
        finalizerThread_.join();
        finalizersState_ = FinalizerState::NotRunning;
    }
}

gc::ConcurrentMarkAndSweep::~ConcurrentMarkAndSweep() {
    state_.waitUntil(
        [this] { return state_.get() == GCState::kNone; },
        [this] { state_.compareAndSet(state_.get(), GCState::kShutdown); }
    );
    gcThread_.join();
    StopFinalizerThread();
}

bool gc::ConcurrentMarkAndSweep::PerformFullGC() noexcept {
    RuntimeLogDebug({kTagGC}, "Attempt to suspend threads by thread %d", konan::currentThreadId());
    auto timeStartUs = konan::getTimeMicros();
    bool didSuspend = mm::RequestThreadsSuspension();
    if (!didSuspend) {
        RuntimeLogDebug({kTagGC}, "Failed to suspend threads by thread %d", konan::currentThreadId());
        // Somebody else suspended the threads, and so ran a GC.
        // TODO: This breaks if suspension is used by something apart from GC.
        return false;
    }
    RuntimeLogDebug({kTagGC}, "Requested thread suspension by thread %d", konan::currentThreadId());
    if (!state_.compareAndSet(GCState::kNeedsGC, GCState::kWorldIsStopped)) {
        RuntimeFail("Someone steel kNeedsGC state before moving to kWorldIsStopped");
    }

    RuntimeAssert(!kotlin::mm::IsCurrentThreadRegistered(), "Concurrent GC must run on unregistered thread");

    mm::WaitForThreadsSuspension();
    auto timeSuspendUs = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Suspended all threads in %" PRIu64 " microseconds", timeSuspendUs - timeStartUs);

    auto& scheduler = mm::GlobalData::Instance().gcScheduler();
    scheduler.gcData().OnPerformFullGC();

    RuntimeLogInfo(
            {kTagGC}, "Started GC epoch %zu. Time since last GC %" PRIu64 " microseconds", epoch_, timeStartUs - lastGCTimestampUs_);
    auto timeRootSetUs = konan::getTimeMicros();
    // Can be unsafe, because we've stopped the world.
    auto objectsCountBefore = mm::GlobalData::Instance().objectFactory().GetSizeUnsafe();
    KStdVector<ObjHeader*> graySet = collectRootSet();
    RuntimeLogInfo(
            {kTagGC}, "Collected root set of size %zu in %" PRIu64 " microseconds", graySet.size(),
            timeRootSetUs - timeSuspendUs);
    gc::Mark<MarkTraits>(std::move(graySet));
    auto timeMarkUs = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Marked in %" PRIu64 " microseconds", timeMarkUs - timeRootSetUs);
    gc::SweepExtraObjects<SweepTraits>(mm::GlobalData::Instance().extraObjectDataFactory());
    auto timeSweepExtraObjectsUs = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Sweeped extra objects in %" PRIu64 " microseconds", timeSweepExtraObjectsUs - timeMarkUs);

    auto objectFactoryIterable = mm::GlobalData::Instance().objectFactory().LockForIter();

    if (!state_.compareAndSet(GCState::kWorldIsStopped, GCState::kGCRunning)) {
        RuntimeFail("Someone changed kWorldIsStopped during stop-the-world-phase");
    }
    mm::ResumeThreads();
    auto timeResumeUs = konan::getTimeMicros();

    RuntimeLogDebug({kTagGC},
                    "Resumed threads in %" PRIu64 " microseconds. Total pause for most threads is %"  PRIu64" microseconds",
                    timeResumeUs - timeSweepExtraObjectsUs, timeResumeUs - timeStartUs);

    auto finalizerQueue = gc::Sweep<SweepTraits>(objectFactoryIterable);
    auto timeSweepUs = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Swept in %" PRIu64 " microseconds", timeSweepUs - timeResumeUs);

    // Can be unsafe, because we've stopped the world.
    auto objectsCountAfter = mm::GlobalData::Instance().objectFactory().GetSizeUnsafe();
    auto extraObjectsCountAfter = mm::GlobalData::Instance().extraObjectDataFactory().GetSizeUnsafe();

    auto finalizersCount = finalizerQueue.size();
    auto collectedCount = objectsCountBefore - objectsCountAfter - finalizersCount;

    if (finalizersCount) {
        StartFinalizerThreadIfNone();
        std::unique_lock guard(finalizerQueueMutex_);
        finalizerQueue_.MergeWith(std::move(finalizerQueue));
        finalizerQueueCondVar_.notify_all();
    }

    if (!state_.compareAndSet(GCState::kGCRunning, GCState::kNone)) {
        RuntimeLogDebug({kTagGC}, "New GC is already scheduled while finishing previous one");
    }

    RuntimeLogInfo(
            {kTagGC},
            "Finished GC epoch %zu. Collected %zu objects, to be finalized %zu objects, %zu objects and %zd extra data objects remain. Total pause time %" PRIu64
            " microseconds",
            epoch_, collectedCount, finalizersCount, objectsCountAfter, extraObjectsCountAfter, timeSweepUs - timeStartUs);
    ++epoch_;
    lastGCTimestampUs_ = timeResumeUs;
    return true;
}
