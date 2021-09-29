#include <numeric>
#include <array>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <map>
#include <optional>
#include <vector>

namespace {

template <typename K, typename V>
std::optional<std::reference_wrapper<const V>> find(const std::map<K, V>& map, const K& key) noexcept {
    auto it = map.find(key);
    if (it == map.end()) {
        return std::nullopt;
    }
    return std::make_optional(std::reference_wrapper(it->second));
}

enum class EventType: uint8_t {
    kZoneStart,
    kZoneEnd,
    kSignal,
};

std::ostream& operator<<(std::ostream& ost, const EventType& type) {
    switch (type) {
        case EventType::kZoneStart:
          return ost << "ZoneStart";
        case EventType::kZoneEnd:
          return ost << "ZoneEnd";
        case EventType::kSignal:
          return ost << "Signal";
    }
}

template <size_t N>
std::ostream& operator<<(std::ostream& ost, const std::array<char, N> arr) {
    for (auto c: arr) {
        if (c == '\0')
            break;
        ost << c;
    }
    return ost;
}

using ZoneName = std::array<char, 48>;
using SignalName = std::array<char, 40>;

template <size_t N>
bool Matches(const std::array<char, N>& lhs, std::string_view rhs) noexcept {
    size_t size = std::min(lhs.size(), rhs.size());
    for (size_t i = 0; i < size; ++i) {
        if (lhs[i] != rhs[i])
          return false;
    }

    return true;
}

struct ZoneStartPayload {
    ZoneName name;
};

std::ostream& operator<<(std::ostream& ost, const ZoneStartPayload& payload) {
    return ost << payload.name;
}

struct ZoneEndPayload {
    ZoneName name;
};

std::ostream& operator<<(std::ostream& ost, const ZoneEndPayload& payload) {
    return ost << payload.name;
}

struct SignalPayload {
    SignalName name;
    int64_t value;
};

std::ostream& operator<<(std::ostream& ost, const SignalPayload& payload) {
    return ost << payload.name << " " << payload.value;
}

struct Event {
    EventType type;
    union {
        ZoneStartPayload zoneStartPayload;
        ZoneEndPayload zoneEndPayload;
        SignalPayload signalPayload;
    } payload;
};

std::ostream& operator<<(std::ostream& ost, const Event& event) {
    ost << event.type << ": ";
    switch (event.type) {
      case EventType::kZoneStart:
        ost << event.payload.zoneStartPayload;
        break;
      case EventType::kZoneEnd:
        ost << event.payload.zoneEndPayload;
        break;
      case EventType::kSignal:
        ost << event.payload.signalPayload;
        break;
    }
    return ost;
}

template <typename F>
void ProcessEvents(const char* filename, F&& f) noexcept {
    std::ifstream file(filename, std::ios_base::binary);

    while (!file.eof()) {
        std::array<char, sizeof(Event)> eventBuffer;
        if (file.read(eventBuffer.data(), eventBuffer.size())) {
            std::forward<F>(f)(*reinterpret_cast<Event*>(eventBuffer.data()));
        }
    }
}

std::map<ZoneName, std::vector<SignalPayload>> CollectSignals(const char* filename) noexcept {
    std::map<ZoneName, std::vector<SignalPayload>> result;

    ZoneName zoneName = {0};

    ProcessEvents(filename, [&zoneName, &result](const Event& event) {
        switch (event.type) {
        case EventType::kZoneStart:
            zoneName = event.payload.zoneStartPayload.name;
            break;
        case EventType::kZoneEnd:
            zoneName[0] = 0;
            break;
        case EventType::kSignal:
            if (zoneName[0] != 0) {
                result[zoneName].push_back(event.payload.signalPayload);
            }
            break;
        }
    });

    return result;
}

template<typename T>
struct Stats {
    size_t count = 0;
    T avg = 0;
    T p0 = 0;
    T p50 = 0;
    T p90 = 0;
    T p95 = 0;
    T p99 = 0;
    T p100 = 0;

    static Stats From(std::vector<T> values) noexcept {
        Stats result;

        result.count = values.size();
        if (result.count  == 0) {
            return result;
        }
        result.avg = std::accumulate(values.begin(), values.end(), (T)0) / (T)result.count;
        std::sort(values.begin(), values.end());
        result.p0 = values[0];
        result.p50 = values[result.count * 0.5];
        result.p90 = values[result.count * 0.9];
        result.p95 = values[result.count * 0.95];
        result.p99 = values[result.count * 0.99];
        result.p100 = values[result.count - 1];

        return result;
    }
};

template <typename T>
std::ostream& operator<<(std::ostream& ost, const Stats<T>& stats) noexcept {
    return ost
        << "cnt=" << stats.count << " "
        << "avg=" << stats.avg << " "
        << "p0=" << stats.p0 << " "
        << "p50=" << stats.p50 << " "
        << "p90=" << stats.p90 << " "
        << "p95=" << stats.p95 << " "
        << "p99=" << stats.p99 << " "
        << "p100=" << stats.p100 << " ";
}

template <typename Key, typename Value>
struct Histogram {
    std::map<Key, Value> buckets;

    template <size_t N>
    static Histogram Init(const std::array<Key, N>& keys) noexcept {
        std::map<Key, Value> buckets;
        for (auto key : keys) {
            buckets.emplace(key, Value{});
        }
        buckets.emplace(std::numeric_limits<Key>::max(), Value{});

        return Histogram { buckets };
    }

    void Insert(Key key, Value value) noexcept {
        auto it = buckets.lower_bound(key);
        it->second += value;
    }

    void Merge(const Histogram& other) noexcept {
        // TODO: Check that buckets are equal.
        for (auto& [key, value] : buckets) {
            value += other.buckets.at(key);
        }
    }

    template <typename F>
    void MapValues(F f) noexcept {
        for (auto& [key, value] : buckets) {
            value = f(value);
        }
    }
};

template <typename K, typename V>
std::ostream& operator<<(std::ostream& ost, const Histogram<K, V>& histogram) {
    std::optional<K> lastKey;
    for (const auto& [key, value] : histogram.buckets) {
        if (lastKey == std::nullopt) {
            ost << "≤" << key << "=" << value << " ";
        } else if (key == std::numeric_limits<K>::max()) {
            ost << ">" << *lastKey << "=" << value;
        } else {
            ost << "(" << *lastKey << "," << key << "]=" << value << " ";
        }
        lastKey = key;
    }
    return ost;
}

template <typename Key, typename Value>
struct AverageHistogram {
    std::map<Key, std::vector<Value>> buckets;

    template <size_t N>
    static AverageHistogram Init(const std::array<Key, N>& keys) noexcept {
        std::map<Key, std::vector<Value>> buckets;
        for (auto key : keys) {
            buckets.emplace(key, std::vector<Value>{});
        }
        buckets.emplace(std::numeric_limits<Key>::max(), std::vector<Value>{});

        return AverageHistogram { buckets };
    }

    void Insert(Key key, Value value) noexcept {
        auto it = buckets.lower_bound(key);
        it->second.push_back(value);
    }

    void Merge(const AverageHistogram& other) noexcept {
        // TODO: Check that buckets are equal.
        for (auto& [key, value] : buckets) {
            const auto& otherValue = other.buckets.at(key);
            value.insert(value.end(), otherValue.begin(), otherValue.end());
        }
    }
};

template <typename K, typename V>
std::ostream& operator<<(std::ostream& ost, const AverageHistogram<K, V>& histogram) {
    std::optional<K> lastKey;
    for (const auto& [key, values] : histogram.buckets) {
        auto value = values.size() ? std::accumulate(values.begin(), values.end(), int64_t(0)) / values.size() : 0;
        if (lastKey == std::nullopt) {
            ost << "≤" << key << "=" << value << " ";
        } else if (key == std::numeric_limits<K>::max()) {
            ost << ">" << *lastKey << "=" << value;
        } else {
            ost << "(" << *lastKey << "," << key << "]=" << value << " ";
        }
        lastKey = key;
    }
    return ost;
}

constexpr std::array<int64_t, 4> kBetweenGcMsBuckets = {
    10, 50, 100, 200,
};

constexpr std::array<int64_t, 5> kSuspendCollectedMiBBuckets = {
    1, 2, 5, 10, 20,
};

constexpr std::array<int64_t, 5> kSuspendAliveMiBBuckets = {
    1, 10, 20, 50, 100
};

struct ZoneStats {
    size_t count = 0;
    size_t threadCount = 0;
    int64_t maxRSSMiB = 0;
    int64_t maxPeakRSSMiB = 0;
    int64_t maxObjectsMiB = 0;
    double gcTimePercentage = 0.0;
    int64_t maxSuspendTimeMs = 0;
    int64_t minBetweenTimeMs = 0;
    Histogram<int64_t, int64_t> garbageMiBMs;
    double totalRuntimeS = 0;
    Stats<int64_t> rssFreedKiB;

    struct GarbageKiBMsCollector {
        Histogram<int64_t, int64_t> garbageKiBMs = Histogram<int64_t, int64_t>::Init(kBetweenGcMsBuckets);
        std::optional<int64_t> lastObjectBeforeKiB;
        std::optional<int64_t> lastObjectAfterKiB;
        std::optional<int64_t> lastBetweenGcMs;

        void addObjectBeforeKiB(int64_t value) noexcept {
            lastObjectBeforeKiB = value;
            maybeAppend();
        }

        void addObjectAfterKiB(int64_t value) noexcept {
            lastObjectAfterKiB = value;
            maybeAppend();
        }

        void addBetweenGcMs(int64_t value) noexcept {
            lastBetweenGcMs = value;
            maybeAppend();
        }

        void maybeAppend() noexcept {
            if (lastObjectBeforeKiB == std::nullopt) {
                return;
            }
            if (lastObjectAfterKiB == std::nullopt) {
                return;
            }
            if (lastBetweenGcMs == std::nullopt) {
                return;
            }
            garbageKiBMs.Insert(*lastBetweenGcMs, *lastObjectBeforeKiB - *lastObjectAfterKiB);
            lastObjectBeforeKiB = std::nullopt;
            lastObjectAfterKiB = std::nullopt;
            lastBetweenGcMs = std::nullopt;
        }
    };

    struct RssFreedKiBCollector {
        std::vector<int64_t> rssFreedKiB;
        std::optional<int64_t> lastRssBeforeKiB;
        std::optional<int64_t> lastRssAfterKiB;

        void addRssBeforeKiB(int64_t value) noexcept {
            lastRssBeforeKiB = value;
            maybeAppend();
        }

        void addRssAfterKiB(int64_t value) noexcept {
            lastRssAfterKiB = value;
            maybeAppend();
        }

        void maybeAppend() noexcept {
            if (lastRssBeforeKiB == std::nullopt) {
                return;
            }
            if (lastRssAfterKiB == std::nullopt) {
                return;
            }
            rssFreedKiB.push_back(*lastRssBeforeKiB - *lastRssAfterKiB);
            lastRssAfterKiB = std::nullopt;
            lastRssBeforeKiB = std::nullopt;
        }
    };

    static std::optional<ZoneStats> From(const std::vector<SignalPayload>& signals) noexcept {
        size_t count = 0;
        size_t threadCount = 0;
        int64_t maxRSSMiB = std::numeric_limits<int64_t>::min();
        int64_t maxPeakRSSMiB = std::numeric_limits<int64_t>::min();
        int64_t maxObjectsMiB = std::numeric_limits<int64_t>::min();
        int64_t totalSuspendTimeMs = 0;
        int64_t totalThreadTimeMs = 0;
        int64_t maxSuspendTimeMs = std::numeric_limits<int64_t>::min();
        int64_t minBetweenTimeMs = std::numeric_limits<int64_t>::max();
        GarbageKiBMsCollector garbageKiBMsCollector;
        RssFreedKiBCollector rssFreedKiBCollector;

        for (auto& signal : signals) {
            if (Matches(signal.name, "rss_before_kb")) {
                ++count;
                maxRSSMiB = std::max(maxRSSMiB, signal.value / 1024);
                rssFreedKiBCollector.addRssBeforeKiB(signal.value);
            } else if (Matches(signal.name, "rss_peak_before_kb")) {
                maxPeakRSSMiB = std::max(maxPeakRSSMiB, signal.value / 1024);
            } else if (Matches(signal.name, "rss_after_kb")) {
                rssFreedKiBCollector.addRssAfterKiB(signal.value);
            } else if (Matches(signal.name, "objects_before_bytes")) {
                maxObjectsMiB = std::max(maxObjectsMiB, signal.value / 1024 / 1024);
                garbageKiBMsCollector.addObjectBeforeKiB(signal.value / 1024);
            } else if (Matches(signal.name, "suspend_time_us")) {
                totalSuspendTimeMs += signal.value / 1000;
                maxSuspendTimeMs = std::max(maxSuspendTimeMs, signal.value / 1000);
            } else if (Matches(signal.name, "thread_time_us")) {
                auto value = signal.value < 0 ? 0 : signal.value;
                ++threadCount;
                totalThreadTimeMs += value / 1000;
            } else if (Matches(signal.name, "objects_after_bytes")) {
                garbageKiBMsCollector.addObjectAfterKiB(signal.value / 1024);
            } else if (Matches(signal.name, "between_gc_us")) {
                auto value = signal.value < 0 ? 0 : signal.value;
                minBetweenTimeMs = std::min(minBetweenTimeMs, value / 1000);
                garbageKiBMsCollector.addBetweenGcMs(value / 1000);
            }
        }

        if (totalThreadTimeMs == 0) {
            return std::nullopt;
        }
        if (count < 1) {
            return std::nullopt;
        }

        auto garbageKiBMs = std::move(garbageKiBMsCollector.garbageKiBMs);
        // Now it's MiBMs
        garbageKiBMs.MapValues([](int64_t value) { return value / 1024; });

        return ZoneStats {
            .count = count,
            .threadCount = threadCount,
            .maxRSSMiB = maxRSSMiB,
            .maxPeakRSSMiB = maxPeakRSSMiB,
            .maxObjectsMiB = maxObjectsMiB,
            .gcTimePercentage = (double)totalSuspendTimeMs / (double)totalThreadTimeMs * 100.0,
            .maxSuspendTimeMs = maxSuspendTimeMs,
            .minBetweenTimeMs = minBetweenTimeMs,
            .garbageMiBMs = garbageKiBMs,
            .totalRuntimeS = (double)totalThreadTimeMs / 1000.0,
            .rssFreedKiB = Stats<int64_t>::From(rssFreedKiBCollector.rssFreedKiB),
        };
    }
};

std::ostream& operator<<(std::ostream& ost, const ZoneStats& zoneStats) {
    return ost
        << "\n  count:           " << zoneStats.count
        << "\n  threads:         " << zoneStats.threadCount
        << "\n  maxRSS:          " << zoneStats.maxRSSMiB << " MiB"
        << "\n  maxPeakRSS:      " << zoneStats.maxPeakRSSMiB << " MiB"
        << "\n  maxObjects:      " << zoneStats.maxObjectsMiB << " MiB"
        << "\n  gcTime:          " << std::fixed << std::setprecision(2) << zoneStats.gcTimePercentage << "%"
        << "\n  totalRuntime:    " << std::fixed << std::setprecision(1) << zoneStats.totalRuntimeS << " s"
        << "\n  avgThreadTime:   " << std::fixed << std::setprecision(1) << zoneStats.totalRuntimeS / zoneStats.threadCount << " s"
        << "\n  maxSuspendTime:  " << zoneStats.maxSuspendTimeMs << " ms"
        << "\n  minBetweenTime:  " << zoneStats.minBetweenTimeMs << " ms"
        << "\n  garbage(ms=MiB): " << zoneStats.garbageMiBMs;
}

struct TotalStats {
    Stats<int64_t> maxRSSMiB;
    Stats<int64_t> maxObjectsMiB;
    Stats<double> gcTimePercentage;
    Stats<int64_t> maxSuspendTimeMs;
    Stats<int64_t> minBetweenTimeMs;
    Histogram<int64_t, int64_t> garbageMiBMs;

    static TotalStats From(const std::vector<ZoneStats>& allStats) {
        std::vector<int64_t> maxRSSMiB;
        std::vector<int64_t> maxObjectsMiB;
        std::vector<double> gcTimePercentage;
        std::vector<int64_t> maxSuspendTimeMs;
        std::vector<int64_t> minBetweenTimeMs;
        Histogram<int64_t, int64_t> garbageMiBMs = Histogram<int64_t, int64_t>::Init(kBetweenGcMsBuckets);

        for (auto& stats: allStats) {
            maxRSSMiB.push_back(stats.maxRSSMiB);
            maxObjectsMiB.push_back(stats.maxObjectsMiB);
            gcTimePercentage.push_back(stats.gcTimePercentage);
            maxSuspendTimeMs.push_back(stats.maxSuspendTimeMs);
            minBetweenTimeMs.push_back(stats.minBetweenTimeMs);
            garbageMiBMs.Merge(stats.garbageMiBMs);
        }

        return TotalStats {
            .maxRSSMiB = Stats<int64_t>::From(maxRSSMiB),
            .maxObjectsMiB = Stats<int64_t>::From(maxObjectsMiB),
            .gcTimePercentage = Stats<double>::From(gcTimePercentage),
            .maxSuspendTimeMs = Stats<int64_t>::From(maxSuspendTimeMs),
            .minBetweenTimeMs = Stats<int64_t>::From(minBetweenTimeMs),
            .garbageMiBMs = garbageMiBMs,
        };
    }
};

std::ostream& operator<<(std::ostream& ost, const TotalStats& stats) {
    return ost
        << "\n  maxRSS(MiB):        " << stats.maxRSSMiB
        << "\n  maxObjects(MiB):    " << stats.maxObjectsMiB
        << "\n  gcTime(%):          " << stats.gcTimePercentage
        << "\n  maxSuspendTime(ms): " << stats.maxSuspendTimeMs
        << "\n  minBetweenTime(ms): " << stats.minBetweenTimeMs
        << "\n  garbage(ms=MiB):    " << stats.garbageMiBMs;
}

struct ZoneDiffStats {
    size_t count = 0;
    size_t baseCount = 0;
    int64_t maxRSSMib = 0;
    int64_t maxRSSOverheadMiB = 0;
    int64_t maxObjectsMib = 0;
    int64_t maxObjectsOverheadMiB = 0;
    double gcTimePercentage = 0.0;
    double baseGcTimePercentage = 0.0;
    double totalRuntimeS = 0.0;
    double totalRuntimeSlowdown = 0.0;
    int64_t maxSuspendTimeMs = 0;
    int64_t baseMaxSuspendTimeMs = 0;
    int64_t minBetweenTimeMs = 0;
    int64_t baseMinBetweenTimeMs = 0;
    Histogram<int64_t, int64_t> garbageMiBMs;
    Histogram<int64_t, int64_t> baseGarbageMiBMs;

    static ZoneDiffStats From(ZoneStats base, ZoneStats target) noexcept {
        return ZoneDiffStats {
            .count = target.count,
            .baseCount = base.count,
            .maxRSSMib = target.maxRSSMiB,
            .maxRSSOverheadMiB = target.maxRSSMiB - base.maxRSSMiB,
            .maxObjectsMib = target.maxObjectsMiB,
            .maxObjectsOverheadMiB = target.maxObjectsMiB - base.maxObjectsMiB,
            .gcTimePercentage = target.gcTimePercentage,
            .baseGcTimePercentage = base.gcTimePercentage,
            .totalRuntimeS = target.totalRuntimeS,
            .totalRuntimeSlowdown = target.totalRuntimeS / base.totalRuntimeS,
            .maxSuspendTimeMs = target.maxSuspendTimeMs,
            .baseMaxSuspendTimeMs = base.maxSuspendTimeMs,
            .minBetweenTimeMs = target.minBetweenTimeMs,
            .baseMinBetweenTimeMs = base.minBetweenTimeMs,
            .garbageMiBMs = target.garbageMiBMs,
            .baseGarbageMiBMs = base.garbageMiBMs,
        };
    }
};

std::ostream& operator<<(std::ostream& ost, const ZoneDiffStats& stats) {
    return ost
        << "\n  count:                " << stats.count << " vs " << stats.baseCount
        << "\n  maxRSS:               " << stats.maxRSSMib << " MiB (overhead " << stats.maxRSSOverheadMiB << " MiB " << std::fixed << std::setprecision(1) << ((double)stats.maxRSSOverheadMiB / (double)stats.maxRSSMib) * 100 << "%)"
        << "\n  maxObjects:           " << stats.maxObjectsMib << " MiB (overhead " << stats.maxObjectsOverheadMiB << " MiB " << std::fixed << std::setprecision(1) << ((double)stats.maxObjectsOverheadMiB / (double)stats.maxObjectsMib) * 100 << "%)"
        << "\n  gcTime:               " << std::fixed << std::setprecision(2) << stats.gcTimePercentage << "% vs " << stats.baseGcTimePercentage << "%"
        << "\n  totalRuntime:         " << std::fixed << std::setprecision(1) << stats.totalRuntimeS << " s (slowdown " << std::setprecision(2) << stats.totalRuntimeSlowdown << ")"
        << "\n  maxSuspendTime:       " << stats.maxSuspendTimeMs << " ms vs " << stats.baseMaxSuspendTimeMs << " ms (overhead " << std::fixed << std::setprecision(1) << ((double)(stats.maxSuspendTimeMs - stats.baseMaxSuspendTimeMs) / (double)stats.maxSuspendTimeMs) * 100 << "%)"
        << "\n  minBetweenTime:       " << stats.minBetweenTimeMs << " ms vs " << stats.baseMinBetweenTimeMs << " ms (overhead " << std::fixed << std::setprecision(1) << ((double)(stats.baseMinBetweenTimeMs - stats.minBetweenTimeMs) / (double)stats.baseMinBetweenTimeMs) * 100 << "%)"
        << "\n  trgt garbage(ms=MiB): " << stats.garbageMiBMs
        << "\n  base garbage(ms=MiB): " << stats.baseGarbageMiBMs;
}

struct TotalDiffStats {
    Stats<int64_t> maxRSSOverheadMiB;
    Stats<int64_t> maxObjectsOverheadMiB;
    Stats<double> gcTimePercentage;
    Stats<double> baseGcTimePercentage;
    Stats<int64_t> maxSuspendTimeMs;
    Stats<int64_t> baseMaxSuspendTimeMs;
    Stats<int64_t> minBetweenTimeMs;
    Stats<int64_t> baseMinBetweenTimeMs;
    Histogram<int64_t, int64_t> garbageMiBMs;
    Histogram<int64_t, int64_t> baseGarbageMiBMs;

    static TotalDiffStats From(const std::vector<ZoneDiffStats>& allStats) {
        std::vector<int64_t> maxRSSOverheadMiB;
        std::vector<int64_t> maxObjectsOverheadMiB;
        std::vector<double> gcTimePercentage;
        std::vector<double> baseGcTimePercentage;
        std::vector<int64_t> maxSuspendTimeMs;
        std::vector<int64_t> baseMaxSuspendTimeMs;
        std::vector<int64_t> minBetweenTimeMs;
        std::vector<int64_t> baseMinBetweenTimeMs;
        Histogram<int64_t, int64_t> garbageMiBMs = Histogram<int64_t, int64_t>::Init(kBetweenGcMsBuckets);
        Histogram<int64_t, int64_t> baseGarbageMiBMs = Histogram<int64_t, int64_t>::Init(kBetweenGcMsBuckets);

        for (const auto& stats : allStats) {
            maxRSSOverheadMiB.push_back(stats.maxRSSOverheadMiB);
            maxObjectsOverheadMiB.push_back(stats.maxObjectsOverheadMiB);
            gcTimePercentage.push_back(stats.gcTimePercentage);
            baseGcTimePercentage.push_back(stats.baseGcTimePercentage);
            maxSuspendTimeMs.push_back(stats.maxSuspendTimeMs);
            baseMaxSuspendTimeMs.push_back(stats.baseMaxSuspendTimeMs);
            minBetweenTimeMs.push_back(stats.minBetweenTimeMs);
            baseMinBetweenTimeMs.push_back(stats.baseMinBetweenTimeMs);
            garbageMiBMs.Merge(stats.garbageMiBMs);
            baseGarbageMiBMs.Merge(stats.baseGarbageMiBMs);
        }

        return TotalDiffStats {
            .maxRSSOverheadMiB = Stats<int64_t>::From(std::move(maxRSSOverheadMiB)),
            .maxObjectsOverheadMiB = Stats<int64_t>::From(std::move(maxObjectsOverheadMiB)),
            .gcTimePercentage = Stats<double>::From(std::move(gcTimePercentage)),
            .baseGcTimePercentage = Stats<double>::From(std::move(baseGcTimePercentage)),
            .maxSuspendTimeMs = Stats<int64_t>::From(std::move(maxSuspendTimeMs)),
            .baseMaxSuspendTimeMs = Stats<int64_t>::From(std::move(baseMaxSuspendTimeMs)),
            .minBetweenTimeMs = Stats<int64_t>::From(std::move(minBetweenTimeMs)),
            .baseMinBetweenTimeMs = Stats<int64_t>::From(std::move(baseMinBetweenTimeMs)),
            .garbageMiBMs = garbageMiBMs,
            .baseGarbageMiBMs = baseGarbageMiBMs,
        };
    }
};

std::ostream& operator<<(std::ostream& ost, const TotalDiffStats& totalStats) {
    return ost
        << "\n  maxRSSOverhead(MiB):     " << totalStats.maxRSSOverheadMiB
        << "\n  maxObjectsOverhead(MiB): " << totalStats.maxObjectsOverheadMiB
        << "\n  trgt gcTime(%):          " << totalStats.gcTimePercentage
        << "\n  base gcTime(%):          " << totalStats.baseGcTimePercentage
        << "\n  trgt maxSuspendTime(ms): " << totalStats.maxSuspendTimeMs
        << "\n  base maxSuspendTime(ms): " << totalStats.baseMaxSuspendTimeMs
        << "\n  trgt minBetweenTime(ms): " << totalStats.minBetweenTimeMs
        << "\n  base minBetweenTime(ms): " << totalStats.baseMinBetweenTimeMs
        << "\n  trgt garbage(ms=MiB):    " << totalStats.garbageMiBMs
        << "\n  base garbage(ms=MiB):    " << totalStats.baseGarbageMiBMs;
}

std::map<ZoneName, std::map<SignalName, Stats<int64_t>>> GetStats(const char* filename) noexcept {
    std::map<ZoneName, std::map<SignalName, Stats<int64_t>>> result;

    for (auto& [zoneName, signals] : CollectSignals(filename)) {
        std::map<SignalName, std::vector<int64_t>> signalsMap;
        for (auto& signal : signals) {
            signalsMap[signal.name].push_back(signal.value);
        }
        for (auto& [signalName, values] : signalsMap) {
            result[zoneName][signalName] = Stats<int64_t>::From(std::move(values));
        }
    }

    return result;
}

void PrintVerboseOne(const char* filename) noexcept {
    auto stats = GetStats(filename);
    for (const auto& [zoneName, signals] : stats) {
        std::cout << "Zone: " << zoneName << "\n";
        for (const auto& [signalName, signalStats] : signals) {
            std::cout << "   " << signalName << " " << signalStats << "\n";
        }
    }
}

void PrintDiff(const char* targetFilename, const char* baseFilename) noexcept {
    std::cout << "base: " << baseFilename << "\n";
    std::cout << "trgt: " << targetFilename << "\n";

    auto target = CollectSignals(targetFilename);
    auto base = CollectSignals(baseFilename);

    std::vector<ZoneDiffStats> total;

    for (const auto& [zoneName, targetSignals] : target) {
        if (auto baseSignals = find(base, zoneName)) {
            if (auto targetStats = ZoneStats::From(targetSignals)) {
                if (auto baseStats = ZoneStats::From(*baseSignals)) {
                    auto diffStats = ZoneDiffStats::From(*baseStats, *targetStats);
                    std::cout << "Zone: " << zoneName << diffStats << "\n";
                    total.emplace_back(std::move(diffStats));
                }
            }
        }
    }

    std::cout << "Total: " << TotalDiffStats::From(total) << "\n";
}

void PrintShortOne(const char* filename) noexcept {
    std::vector<ZoneStats> total;

    for (auto& [zoneName, signals] : CollectSignals(filename)) {
        auto zoneStats = ZoneStats::From(signals);
        if (zoneStats == std::nullopt) {
            continue;
        }
        std::cout << "Zone: " << zoneName << *zoneStats << "\n";
        total.emplace_back(std::move(*zoneStats));
    }

    std::cout << "Total: " << TotalStats::From(total) << "\n";
}

}

int main(int argc, char** argv) {
    if (argc == 3 && std::string_view(argv[1]) == std::string_view("verbose")) {
        PrintVerboseOne(argv[2]);
        return 0;
    } else if (argc == 3 && std::string_view(argv[1]) == std::string_view("short")) {
        PrintShortOne(argv[2]);
        return 0;
    } else if (argc == 4 && std::string_view(argv[1]) == std::string_view("diff")) {
        PrintDiff(argv[2], argv[3]);
        return 0;
    }

    std::cerr << "Usage: " << argv[0] << " verbose|short|diff [target filename] [base filename]\n";
    return -1;
}
