/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>

#include "GCScheduler.hpp"
#include "ObjectFactory.hpp"
#include "Types.h"
#include "Utils.hpp"
#include "GCState.hpp"

namespace kotlin {

namespace mm {
class ThreadData;
}

namespace gc {

// Stop-the-world Mark-and-Sweep that runs on mutator threads. Can support targets that do not have threads.
class ConcurrentMarkAndSweep : private Pinned {
public:

    class ObjectData {
    public:
        enum class Color {
            kWhite = 0, // Initial color at the start of collection cycles. Objects with this color at the end of GC cycle are collected.
                        // All new objects are allocated with this color.
            kBlack, // Objects encountered during mark phase.
        };

        Color color() const noexcept { return color_; }
        void setColor(Color color) noexcept { color_ = color; }

    private:
        Color color_ = Color::kWhite;
    };

    class ThreadData : private Pinned {
    public:
        using ObjectData = ConcurrentMarkAndSweep::ObjectData;

        explicit ThreadData(ConcurrentMarkAndSweep& gc, mm::ThreadData& threadData) noexcept : gc_(gc), threadData_(threadData) {}
        ~ThreadData() = default;

        void SafePointFunctionPrologue() noexcept;
        void SafePointLoopBody() noexcept;
        void SafePointExceptionUnwind() noexcept;
        void SafePointAllocation(size_t size) noexcept;

        void ScheduleAndWaitFullGC() noexcept;
        void ScheduleAndWaitFullGCWithFinalizers() noexcept;
        void StopFinalizerThreadForTests() noexcept;

        void OnOOM(size_t size) noexcept;

    private:
        void SafePointRegular(size_t weight) noexcept;
        void SafePointSlowPath() noexcept;

        ConcurrentMarkAndSweep& gc_;
        mm::ThreadData& threadData_;
    };

    ConcurrentMarkAndSweep() noexcept;
    ~ConcurrentMarkAndSweep();
    void StopFinalizerThreadForTests() noexcept;

private:
    // Returns `true` if GC has happened, and `false` if not (because someone else has suspended the threads).
    bool PerformFullGC(int64_t epoch) noexcept;
    void StartFinalizerThreadIfNone() noexcept;
    void RequestThreadsSuspension() noexcept;
    void ResumeThreads() noexcept;

    uint64_t lastGCTimestampUs_ = 0;
    GCStateHolder state_;
    std::thread gcThread_;
    std::thread finalizerThread_;
    mm::ObjectFactory<ConcurrentMarkAndSweep>::FinalizerQueue finalizerQueue_;
    std::mutex finalizerQueueMutex_;
};

} // namespace gc
} // namespace kotlin
