/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "MetricCollector.hpp"

#include <array>
#include <cinttypes>
#include <fstream>
#include <mutex>
#include <optional>

#include "KString.h"
#include "Logging.hpp"
#include "Mutex.hpp"
#include "Types.h"
#include "cpp_support/Span.hpp"

using namespace kotlin;

namespace {

constexpr const char kFilename[] = "metrics.bin";
constexpr const char kMissedFilename[] = "metrics.missed";
constexpr size_t kBufferSizeBytes = 50 * 1024 * 1024;

enum class EventType: uint8_t {
    kZoneStart,
    kZoneEnd,
    kSignal,
};

using ZoneName = std::array<char, 48>;
using SignalName = std::array<char, 40>;

struct ZoneStartPayload {
    ZoneName name;
};

struct ZoneEndPayload {
    ZoneName name;
};

struct SignalPayload {
    SignalName name;
    int64_t value;
};

class Event {
public:
    static std::optional<ZoneStartPayload*> NewZoneStart(std_support::span<uint8_t>& buffer) noexcept {
        auto optionalEvent = New(buffer);
        if (!optionalEvent) {
            return std::nullopt;
        }
        auto event = *optionalEvent;
        event->type_ = EventType::kZoneStart;
        return &event->payload_.zoneStartPayload;
    }

    static std::optional<ZoneEndPayload*> NewZoneEnd(std_support::span<uint8_t>& buffer) noexcept {
        auto optionalEvent = New(buffer);
        if (!optionalEvent) {
            return std::nullopt;
        }
        auto event = *optionalEvent;
        event->type_ = EventType::kZoneEnd;
        return &event->payload_.zoneEndPayload;
    }

    static std::optional<SignalPayload*> NewSignal(std_support::span<uint8_t>& buffer) noexcept {
        auto optionalEvent = New(buffer);
        if (!optionalEvent) {
            return std::nullopt;
        }
        auto event = *optionalEvent;
        event->type_ = EventType::kSignal;
        return &event->payload_.signalPayload;
    }

private:
    Event() = default;
    ~Event() = default;

    static std::optional<Event*> New(std_support::span<uint8_t>& buffer) noexcept {
        if (buffer.size() < sizeof(Event)) {
            return std::nullopt;
        }
        auto* event = new (buffer.data()) Event();
        RuntimeAssert(reinterpret_cast<uint8_t*>(event) == buffer.data(), "Allocation moved.");
        buffer = buffer.subspan(sizeof(Event));
        return event;
    }

    EventType type_;
    union {
        ZoneStartPayload zoneStartPayload;
        ZoneEndPayload zoneEndPayload;
        SignalPayload signalPayload;
    } payload_;
};

class ThreadCollector;

class GlobalCollector {
public:
    GlobalCollector() noexcept : file_(kFilename, std::ios_base::app | std::ios_base::binary), missedFile_(kMissedFilename, std::ios_base::app) {}

    ~GlobalCollector() {
        RuntimeCheck(collectors_.empty(), "All collectors must be gone already");
    }

    void Flush() noexcept;

    void Post(std_support::span<uint8_t> buffer, uint64_t missed) noexcept {
        std::unique_lock guard(fileMutex_);

        RuntimeLogDebug({"metrics"}, "Will write %zu bytes to metrics file", buffer.size());
        file_.write(reinterpret_cast<char*>(buffer.data()), buffer.size());
        file_.flush();
        if (missed != 0) {
            missedFile_ << missed << "\n";
            missedFile_.flush();
        }
    }

    void Add(ThreadCollector* collector) noexcept {
        std::unique_lock guard(collectorsMutex_);
        collectors_.insert(collector);
    }

    void Remove(ThreadCollector* collector) noexcept {
        std::unique_lock guard(collectorsMutex_);
        collectors_.erase(collector);
    }

    void SetStartTime() noexcept;
    void SendActiveTime() noexcept;

private:
    std::mutex collectorsMutex_;
    std::set<ThreadCollector*> collectors_;

    std::mutex fileMutex_;
    std::ofstream file_;
    std::ofstream missedFile_;
};

[[clang::no_destroy]] GlobalCollector collector;

class ThreadCollector {
public:
    ThreadCollector(GlobalCollector& globalCollector) noexcept : globalCollector_(globalCollector) {
        ResetCursor();
        globalCollector_.Add(this);
        SetStartTime();
    }

    ~ThreadCollector() {
        Flush();
        globalCollector_.Remove(this);
    }

    void Flush() noexcept {
        std::unique_lock guard(bufferLock_);

        globalCollector_.Post(std_support::span<uint8_t>(buffer_.data(), bufferCursor_.data() - buffer_.data()), missed_);
        ResetCursor();
        missed_ = 0;
    }

    void ZoneStart(std::string_view name) noexcept {
        std::unique_lock guard(bufferLock_);

        auto zoneStart = Event::NewZoneStart(bufferCursor_);
        if (!zoneStart) {
          ++missed_;
          RuntimeLogWarning({"metrics"}, "Failed to ZoneStart. Total missed: %" PRIu64, missed_);
          return;
        }
        auto subname = name.substr(0, std::min((*zoneStart)->name.size(), name.size()));
        std::copy(subname.begin(), subname.end(), (*zoneStart)->name.begin());
    }

    void ZoneEnd(std::string_view name) noexcept {
        std::unique_lock guard(bufferLock_);

        auto zoneEnd = Event::NewZoneEnd(bufferCursor_);
        if (!zoneEnd) {
          ++missed_;
          RuntimeLogWarning({"metrics"}, "Failed to ZoneEnd. Total missed: %" PRIu64, missed_);
          return;
        }
        auto subname = name.substr(0, std::min((*zoneEnd)->name.size(), name.size()));
        std::copy(subname.begin(), subname.end(), (*zoneEnd)->name.begin());
    }

    void Post(std::string_view name, int64_t value) noexcept {
        std::unique_lock guard(bufferLock_);

        auto signal = Event::NewSignal(bufferCursor_);
        if (!signal) {
          ++missed_;
          RuntimeLogWarning({"metrics"}, "Failed to Signal. Total missed: %" PRIu64, missed_);
          return;
        }
        auto subname = name.substr(0, std::min((*signal)->name.size(), name.size()));
        std::copy(subname.begin(), subname.end(), (*signal)->name.begin());
        (*signal)->value = value;
    }

    void SetStartTime() noexcept {
        startTime_ = konan::getTimeNanos();
    }

    void ThreadDone() noexcept {
        if (endTime_ == 0) {
            endTime_ = konan::getTimeNanos();
        }
    }

    void SendActiveTime() noexcept {
        ThreadDone();
        Post("thread_time_us", (endTime_ - startTime_) / 1000);
        startTime_ = 0;
        endTime_ = 0;
    }

private:
    void ResetCursor() noexcept {
        bufferCursor_ = std_support::span<uint8_t>(buffer_.data(), buffer_.size());
    }

    SpinLock<MutexThreadStateHandling::kIgnore> bufferLock_;
    GlobalCollector& globalCollector_;
    std::array<uint8_t, kBufferSizeBytes> buffer_;
    std_support::span<uint8_t> bufferCursor_;
    uint64_t missed_ = 0;
    int64_t startTime_ = 0;
    int64_t endTime_ = 0;
};

ThreadCollector& threadCollector() noexcept {
    [[clang::no_destroy]] thread_local ThreadCollector* threadCollector = new ThreadCollector(collector);
    return *threadCollector;
}

void GlobalCollector::Flush() noexcept {
    std::unique_lock guard(collectorsMutex_);
    for (auto* collector : collectors_) {
        collector->Flush();
    }
}

void GlobalCollector::SetStartTime() noexcept {
    std::unique_lock guard(collectorsMutex_);
    for (auto* collector : collectors_) {
        collector->SetStartTime();
    }
}

void GlobalCollector::SendActiveTime() noexcept {
    std::unique_lock guard(collectorsMutex_);
    for (auto* collector : collectors_) {
        collector->SendActiveTime();
    }
}

}

void kotlin::Post(std::string_view name, int64_t value) noexcept {
    threadCollector().Post(name, value);
}

void kotlin::ZoneStart(std::string_view name) noexcept {
    collector.Flush();
    threadCollector().ZoneStart(name);
    collector.Flush();
    collector.SetStartTime();
}

void kotlin::ZoneEnd(std::string_view name) noexcept {
    collector.SendActiveTime();
    collector.Flush();
    threadCollector().ZoneEnd(name);
    collector.Flush();
}

void kotlin::ThreadDone() noexcept {
    threadCollector().ThreadDone();
}

void kotlin::Flush() noexcept {
    collector.Flush();
}

extern "C" void Kotlin_MetricCollector_zoneStart(KRef object, KString name) {
    ZoneStart(CreateStdStringFromString(name));
}

extern "C" void Kotlin_MetricCollector_zoneEnd(KRef object, KString name) {
    ZoneEnd(CreateStdStringFromString(name));
}
