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
#include "MemoryUsageInfo.hpp"
#include "MetricCollector.hpp"
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
void gc::ConcurrentMarkAndSweep::ThreadData::ScheduleAndWaitFullGC() noexcept {
    auto suspendAt = konan::getTimeNanos();
    ThreadStateGuard guard(ThreadState::kNative);
    auto scheduled_epoch = gc_.state_.schedule();
    gc_.state_.waitEpochFinished(scheduled_epoch);
    auto suspendedFor = konan::getTimeNanos() - suspendAt;
    kotlin::Post("suspend_time_us", suspendedFor / 1000);
}

void gc::ConcurrentMarkAndSweep::ThreadData::ScheduleAndWaitFullGCWithFinalizers() noexcept {
    ThreadStateGuard guard(ThreadState::kNative);
    auto scheduled_epoch = gc_.state_.schedule();
    gc_.state_.waitEpochFinalized(scheduled_epoch);
}

void gc::ConcurrentMarkAndSweep::ThreadData::StopFinalizerThreadForTests() noexcept {
    gc_.StopFinalizerThreadForTests();
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
    threadData_.suspensionData().suspendIfRequested();
}

gc::ConcurrentMarkAndSweep::ConcurrentMarkAndSweep() noexcept  {
    mm::GlobalData::Instance().gcScheduler().SetScheduleGC([this]() NO_EXTERNAL_CALLS_CHECK NO_INLINE {
        RuntimeLogDebug({kTagGC}, "Scheduling GC by thread %d", konan::currentThreadId());
        state_.schedule();
    });
    gcThread_ = std::thread([this] {
        while (true) {
            auto epoch = state_.waitScheduled();
            if (epoch != std::numeric_limits<int64_t>::max()) {
                PerformFullGC(epoch);
            } else {
                state_.start(epoch);
                state_.finish(epoch);
                break;
            }
        }
    });
}

void gc::ConcurrentMarkAndSweep::StartFinalizerThreadIfNone() noexcept {
    if (finalizerThread_.joinable()) return;
    finalizerThread_ = std::thread([this] {
        Kotlin_initRuntimeIfNeeded();
        while (true) {
            auto finalizersEpoch = state_.waitFinalizersRequired();
            if (finalizersEpoch == std::numeric_limits<int64_t>::max()) break;
            std::unique_lock lock(finalizerQueueMutex_);
            auto queue = std::move(finalizerQueue_);
            lock.unlock();
            if (queue.size() > 0) {
                ThreadStateGuard guard(ThreadState::kRunnable);
                queue.Finalize();
            }
            state_.finalized(finalizersEpoch);
        }
    });
}

void gc::ConcurrentMarkAndSweep::StopFinalizerThreadForTests() noexcept {
    auto epoch = state_.waitCurrentFinished();
    if (finalizerThread_.joinable()) {
        state_.finish(std::numeric_limits<int64_t>::max());
        finalizerThread_.join();
        RuntimeAssert(finalizerQueue_.size() == 0, "Finalizer queue should be empty when killing finalizer thread");
        state_.finish(epoch);
        state_.finalized(epoch);
    }
}

gc::ConcurrentMarkAndSweep::~ConcurrentMarkAndSweep() {
    state_.shutdown();
    gcThread_.join();
    if (finalizerThread_.joinable()) {
        finalizerThread_.join();
    }
}

void gc::ConcurrentMarkAndSweep::RequestThreadsSuspension() noexcept {
    gNeedSafepointSlowpath = true;
    bool didSuspend = mm::RequestThreadsSuspension();
    RuntimeAssert(didSuspend, "Only GC thread can request suspension");
}

void gc::ConcurrentMarkAndSweep::ResumeThreads() noexcept {
    mm::ResumeThreads();
    gNeedSafepointSlowpath = false;
}

bool gc::ConcurrentMarkAndSweep::PerformFullGC(int64_t epoch) noexcept {
    RuntimeLogDebug({kTagGC}, "Attempt to suspend threads by thread %d", konan::currentThreadId());
    auto timeStartUs = konan::getTimeMicros();
    RequestThreadsSuspension();
    static std::optional<int64_t> lastGCTimestampUs = std::nullopt;
    if (lastGCTimestampUs) {
        kotlin::Post("between_gc_us", timeStartUs - *lastGCTimestampUs);
    }
    lastGCTimestampUs = timeStartUs;
    RuntimeLogDebug({kTagGC}, "Requested thread suspension by thread %d", konan::currentThreadId());

    auto rssBefore = GetResidentSetSizeBytes();
    auto rssPeakBefore = GetPeakResidentSetSizeBytes();
    kotlin::Post("rss_before_kb", rssBefore / 1024);
    kotlin::Post("rss_peak_before_kb", rssPeakBefore / 1024);

    RuntimeAssert(!kotlin::mm::IsCurrentThreadRegistered(), "Concurrent GC must run on unregistered thread");

    mm::WaitForThreadsSuspension();
    auto timeSuspendUs = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Suspended all threads in %" PRIu64 " microseconds", timeSuspendUs - timeStartUs);

    auto& scheduler = mm::GlobalData::Instance().gcScheduler();
    scheduler.gcData().OnPerformFullGC();

    state_.start(epoch);
    RuntimeLogInfo(
            {kTagGC}, "Started GC epoch %" PRId64 ". Time since last GC %" PRIu64 " microseconds", epoch, timeStartUs - lastGCTimestampUs_);
    auto timeRootSetUs = konan::getTimeMicros();
    // Can be unsafe, because we've stopped the world.
    auto objectsCountBefore = mm::GlobalData::Instance().objectFactory().GetSizeUnsafe();
    auto objectsCountBeforeBytes = mm::GlobalData::Instance().objectFactory().GetSizeBytesUnsafe();
    kotlin::Post("objects_before_bytes", objectsCountBeforeBytes);
    kotlin::Post("objects_before", objectsCountBefore);
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

    ResumeThreads();
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

    auto objectsCountAfterBytes = mm::GlobalData::Instance().objectFactory().GetSizeBytesUnsafe();
    kotlin::Post("objects_after_bytes", objectsCountAfterBytes);
    kotlin::Post("objects_after", objectsCountAfter);

    auto rssAfter = GetResidentSetSizeBytes();
    kotlin::Post("rss_after_kb", rssAfter / 1024);

    if (finalizersCount) {
        StartFinalizerThreadIfNone();
        std::unique_lock guard(finalizerQueueMutex_);
        finalizerQueue_.MergeWith(std::move(finalizerQueue));
    }

    state_.finish(epoch);
    if (!finalizerThread_.joinable()) {
        state_.finalized(epoch);
    }

    RuntimeLogInfo(
            {kTagGC},
            "Finished GC epoch %" PRId64 ". Collected %zu objects, to be finalized %zu objects, %zu objects and %zd extra data objects remain. Total pause time %" PRIu64
            " microseconds",
            epoch, collectedCount, finalizersCount, objectsCountAfter, extraObjectsCountAfter, timeSweepUs - timeStartUs);
    lastGCTimestampUs_ = timeResumeUs;
    return true;
}
