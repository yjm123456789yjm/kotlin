/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "MemoryUsageInfo.hpp"

#include <algorithm>
#include <limits>

#include "Types.h"

#if KONAN_WINDOWS

#include <windows.h>
#include <psapi.h>

size_t kotlin::GetPeakResidentSetSizeBytes() noexcept {
    ::PROCESS_MEMORY_COUNTERS memoryCounters;
    auto succeeded = ::GetProcessMemoryInfo(::GetCurrentProcess(), &memoryCounters, sizeof(memoryCounters));
    if (!succeeded) {
        return 0;
    }
    return memoryCounters.PeakWorkingSetSize;
}

#elif KONAN_LINUX || KONAN_MACOSX || KONAN_IOS

#include <sys/time.h>
#include <sys/resource.h>

size_t kotlin::GetPeakResidentSetSizeBytes() noexcept {
    ::rusage usage;
    auto failed = ::getrusage(RUSAGE_SELF, &usage);
#if KONAN_LINUX
    // On Linux it's in kilobytes.
    size_t maxrss = static_cast<size_t>(usage.ru_maxrss * 1024);
#elif KONAN_MACOSX || KONAN_IOS
    // On macOS and iOS it's in bytes.
    size_t maxrss = static_cast<size_t>(usage.ru_maxrss);
#else
#error "Check what units ru_maxrss is in."
#endif
    if (failed) {
        return 0;
    }
    return maxrss;
}

#else

// TODO: Support more platforms
size_t kotlin::GetPeakResidentSetSizeBytes() noexcept {
    return 0;
}

#endif

#if KONAN_MACOSX

#include <mach/mach_init.h>
#include <mach/mach_types.h>
#include <mach/task_info.h>
#include <mach/task.h>
#include <unistd.h>

size_t kotlin::GetResidentSetSizeBytes() noexcept {
    task_basic_info info;
    mach_msg_type_number_t info_count = TASK_BASIC_INFO_COUNT;
    task_t task = MACH_PORT_NULL;
    task_for_pid(current_task(), getpid(), &task);
    task_info(task, TASK_BASIC_INFO, (task_info_t)&info, &info_count);
    return info.resident_size;
}

#else

// TODO: Support more platforms
size_t kotlin::GetResidentSetSizeBytes() noexcept {
    return 0;
}

#endif

extern "C" RUNTIME_NOTHROW KLong Kotlin_MemoryUsageInfo_getPeakResidentSetSizeBytes() {
    auto result = kotlin::GetPeakResidentSetSizeBytes();
    // TODO: Need a common implementation for such conversions.
    if constexpr (sizeof(decltype(result)) >= sizeof(KLong)) {
        return static_cast<KLong>(std::min<decltype(result)>(result, std::numeric_limits<KLong>::max()));
    } else {
        return std::min<KLong>(result, std::numeric_limits<KLong>::max());
    }
}
